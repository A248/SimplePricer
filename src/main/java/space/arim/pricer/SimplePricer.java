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
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

import com.pablo67340.guishop.api.DynamicPriceProvider;

import space.arim.universal.util.AutoClosable;

import space.arim.api.config.SimpleConfig;
import space.arim.api.util.FilesUtil;

public class SimplePricer implements DynamicPriceProvider, AutoClosable {

	private final Logger logger;
	private final File dataFolder;
	private final SimpleConfig config;
	private final ConcurrentHashMap<String, DummyItem> items = new ConcurrentHashMap<String, DummyItem>();
	
	SimplePricer(Logger logger, File dataFolder) {
		this.logger = logger;
		this.dataFolder = dataFolder;
		config = new SimpleConfig(dataFolder, "config.yml", "do-not-touch-version") {};
	}
	
	void load() {
		config.reload();
		if (config.getBoolean("save-market-state")) {
			File[] marketFiles = (new File(dataFolder, "market-state")).listFiles();
			// if the market-state directory doesn't exist or isn't a dir, marketFiles must be null
			if (marketFiles != null) {
				for (File dataFile : marketFiles) {
					String itemString = dataFile.getName();
					try (Scanner scanner = new Scanner(dataFile, "UTF-8")) {
						items.put(itemString, PricedItem.forStockAndSpread(scanner.nextDouble(), scanner.nextDouble()));
					} catch (IOException | NoSuchElementException ex) {
						logger.warn("Error reading file " + dataFile.getPath(), ex);
					}
				}
			}
		}
	}
	
	private DummyItem getItem(String itemString, double base, double spread) {
		return items.computeIfAbsent(itemString, (is) -> (base == 0D) ? new DummyItem() : PricedItem.forBaseAndSpread(base, spread));
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
		 * 
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
				items.forEach((itemString, itemPricing) -> {
					if (itemPricing instanceof PricedItem) {
						File dataFile = new File(marketStateFolder, itemString);
						if (dataFile.exists() && !dataFile.delete()) {
							logger.warn("Could not override data file " + dataFile.getPath());
						} else {
							PricedItem pricing = ((PricedItem) itemPricing);
							FilesUtil.writeTo(dataFile, (writer) -> {
								writer.append(Double.toString(pricing.getStock()) + '\n' + pricing.getSpread());
							}, (ex) -> logger.warn("Could not print data to file " + dataFile.getPath() + "!", ex));
						}
					}
				});
			} else {
				logger.warn("Could not create market state directory!");
			}
		}
	}
	
}
