/* 
 * SimplePricer
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * SimplePricer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SimplePricer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SimplePricer. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU General Public License.
 */
package space.arim.pricer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.pablo67340.guishop.api.DynamicPriceProvider;

import space.arim.universal.util.AutoClosable;

import space.arim.api.config.SimpleConfig;

public class SimplePricer implements DynamicPriceProvider, AutoClosable {

	private final Logger logger;
	private final File dataFolder;
	private final SimpleConfig config;
	private final ConcurrentHashMap<String, BlankItem> items = new ConcurrentHashMap<String, BlankItem>();
	
	/**
	 * CompletableFuture representing loading files asynchronously at startup,
	 * <code>null</code> if we don't pass the parallelism threshold
	 * 
	 * To ensure that we've finished loading once server startup has completed,
	 * we call <code>CompletableFuture.join</code> in {@link #finishLoad()}
	 */
	private volatile CompletableFuture<?> future;
	
	private static final int ASYNC_IO_PARALLELISM_THRESHOLD = 10;
	
	SimplePricer(Logger logger, File dataFolder) {
		this.logger = logger;
		this.dataFolder = dataFolder;
		config = new SimpleConfig(dataFolder, "config.yml", "do-not-touch-version") {};
	}
	
	private Runnable getFileLoadAction(File dataFile) {
		return () -> {
			String itemString = dataFile.getName();
			try (Scanner scanner = new Scanner(dataFile, "UTF-8")) {
				if (items.put(itemString, new PartialItem(scanner.nextDouble())) != null) {
					logger.warn("Item " + itemString + " has duplicate entries!");
				}
			} catch (IOException | NoSuchElementException ex) {
				logger.warn("Error reading file " + dataFile.getPath(), ex);
			}
		};
	}
	
	/**
	 * Begin loading files
	 * 
	 */
	void startLoad() {
		config.reload();
		// loading files
		if (config.getBoolean("save-market-state")) {
			File[] marketFiles = (new File(dataFolder, "market-state")).listFiles();
			// if the market-state directory doesn't exist or isn't a dir, marketFiles must be null
			if (marketFiles != null && marketFiles.length > 0) {

				// Remains null if we don't pass the parallelism threshold
				CompletableFuture<?>[] futureFiles = null;
				if (marketFiles.length >= ASYNC_IO_PARALLELISM_THRESHOLD) {
					futureFiles = new CompletableFuture<?>[marketFiles.length];
				}
				for (int n = 0; n < marketFiles.length; n++) {
					File dataFile = marketFiles[n];

					Runnable cmd = getFileLoadAction(dataFile);
					if (futureFiles != null) {
						futureFiles[n] = CompletableFuture.runAsync(cmd);

					} else {
						cmd.run();
					}
				}
				if (futureFiles != null) {
					future = CompletableFuture.allOf(futureFiles);
				}
			}
		}
	}
	
	/**
	 * Called after server startup has completed
	 * 
	 */
	void finishLoad() {
		if (future != null) {
			future.join(); // await termination
			future = null;
		}
	}

	private BlankItem getItem(String itemString, double base, double spread) {
		return items.compute(itemString, (is, existing) -> {
			if (existing == null) {
				return (base == 0D) ? new BlankItem() : FullItem.fromBaseAndSpread(base, spread);
			} else if (!(existing instanceof FullItem) && existing instanceof PartialItem) {
				return ((PartialItem) existing).toFullItem(spread);
			}
			return existing;
		});
	}
	
	@Override
	public double calculateBuyPrice(String item, int quantity, double staticBuyPrice, double staticSellPrice) {
		return getItem(item, staticBuyPrice, staticSellPrice).calculateBuyPrice(quantity);
	}
	
	@Override
	public double calculateSellPrice(String item, int quantity, double staticBuyPrice, double staticSellPrice) {
		return getItem(item, staticBuyPrice, staticSellPrice).calculateSellPrice(quantity);
	}
	
	@Override
	public void buyItem(String item, int quantity) {
		/*
		 * Assuming GUIShop is working correctly, the key should already be contained in the map.
		 * Meaning, if it isn't, something has gone horribly wrong.
		 * In that case, we want a NPE to clearly indicate there's a bug.
		 */
		items.get(item).buyItem(quantity);
	}

	@Override
	public void sellItem(String item, int quantity) {
		// same as with #buyItem, if items.get(item) is null, we want a NPE
		items.get(item).sellItem(quantity);
	}
	
	@Override
	public void close() {
		if (config.getBoolean("save-market-state")) {
			File marketStateFolder = new File(dataFolder, "market-state");
			if (marketStateFolder.isDirectory() || marketStateFolder.mkdirs()) {

				/*
				 * Use a larger parallelism threshold because some items are not
				 * instances of FullItem, so don't need to be saved to disk
				 */
				items.forEach((ASYNC_IO_PARALLELISM_THRESHOLD * 3 / 2), (itemString, itemPricing) -> {
					if (itemPricing instanceof FullItem) {
						File dataFile = new File(marketStateFolder, itemString);
						if (dataFile.exists() && !dataFile.delete()) {
							logger.warn("Could not override data file " + dataFile.getPath());

						} else {
							FullItem pricing = ((FullItem) itemPricing);
							try (OutputStream output = new FileOutputStream(dataFile); OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8")) {
								writer.append(Double.toString(pricing.getStock()));
							} catch (IOException ex) {
								logger.warn("Could not print data to file " + dataFile.getPath() + "!", ex);
							}
						}
					}
				});
			} else {
				logger.warn("Could not create market state directory!");
			}
		}
		items.clear();
	}
	
}
