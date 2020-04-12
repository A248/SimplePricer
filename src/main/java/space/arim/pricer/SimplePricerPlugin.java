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

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.pablo67340.guishop.api.DynamicPriceProvider;

import space.arim.api.util.log.LoggerConverter;

public class SimplePricerPlugin extends JavaPlugin {

	private SimplePricer pricer;
	
	@Override
	public void onEnable() {
		pricer = new SimplePricer(LoggerConverter.get().convert(getLogger()), getDataFolder());
		getServer().getServicesManager().register(DynamicPriceProvider.class, pricer, this, ServicePriority.Low);
		getServer().getScheduler().runTaskLater(this, pricer::load, 1L);
	}
	
	@Override
	public void onDisable() {
		pricer.close();
	}
	
}
