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

/**
 * Uses a pricing function of <code>be^({@literal -}x/s)</code> where
 * <i>b</i> is the base value and <i>s</i> is the spread value. <br>
 * <br>
 * To calculate prices we find the integral of the pricing function
 * from the current stock of the item to the resulting stock of the item
 * after the purchase.
 * 
 * @author A248
 *
 */
public class FullItem extends PartialItem {

	private final double spread;

	private FullItem(AtomicDouble stock, double spread) {
		super(stock);
		this.spread = spread;
	}

	private FullItem(double stock, double spread) {
		this(new AtomicDouble(stock), spread);
	}

	static FullItem fromBaseAndSpread(double base, double spread) {
		/*
		 * Notice: b*e^(-x/s) = e^(lnb-x/s)=e^(-(x-slnb)/s),
		 * meaning our price equation is simply shifted by a
		 * constant involving base value.
		 * 
		 * Thus we can take b into the exponent and save a variable
		 */
		return new FullItem(-spread*Math.log(base), spread);
	}

	static FullItem forStockAndSpread(AtomicDouble stock, double spread) {
		return new FullItem(stock, spread);
	}

	/**
	 * The integral of the pricing function
	 * 
	 * @param a the lower limit
	 * @param b the upper limit
	 * @return the integral evaluated
	 */
	private double integral(double a, double b) {
		/* 
		 * The integral from a to b of e^(-x/s) equals
		 * -s*e^(-x/s) evaluated from a to b which equals
		 * -s*e^(-b/s) minus -s*e^(-a/s) which equals
		 * s*(e^(-a/s) - e^(-b/s))
		 */
		return spread*(Math.exp(-a/spread) - Math.exp(-b/spread));
	}

	@Override
	double calculateBuyPrice(int quantity) {
		double stock = this.stock.get();
		return integral(stock - quantity, stock); // to get a positive, integrate forwards
	}

	@Override
	double calculateSellPrice(int quantity) {
		double stock = this.stock.get();
		return integral(stock, stock + quantity);
	}

	@Override
	void buyItem(int quantity) {
		stock.addAndGet(quantity);
	}

	@Override
	void sellItem(int quantity) {
		stock.addAndGet(-quantity);
	}

	double getSpread() {
		return spread;
	}

	@Override
	FullItem toFullItem(double spread) {
		throw new IllegalStateException("Already a full item");
	}

}
