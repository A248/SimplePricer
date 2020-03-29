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

import com.google.common.util.concurrent.AtomicDouble;

public class PartialItem extends BlankItem {

	final AtomicDouble stock;

	PartialItem(AtomicDouble stock) {
		this.stock = stock;
	}

	PartialItem(double stock) {
		this(new AtomicDouble(stock));
	}

	@Override
	double calculateBuyPrice(int quantity) {
		throw new IllegalStateException("Item is partial!");
	}

	@Override
	double calculateSellPrice(int quantity) {
		throw new IllegalStateException("Item is partial!");
	}
	
	@Override
	void buyItem(int quantity) {
		throw new IllegalStateException("Item is partial!");
	}
	
	@Override
	void sellItem(int quantity) {
		throw new IllegalStateException("Item is partial!");
	}

	double getStock() {
		return stock.get();
	}

	FullItem toFullItem(double spread) {
		return FullItem.forStockAndSpread(stock, spread);
	}
	
}
