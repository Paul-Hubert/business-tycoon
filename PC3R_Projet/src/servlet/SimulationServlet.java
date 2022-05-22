package servlet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import simulation.Market;
import simulation.World;

@WebListener
public class SimulationServlet implements ServletContextListener {
    
	private ScheduledExecutorService scheduler;
	
	public static long SIMULATION_INTERVAL = 1000;
	public static long PRICE_INTERVAL_MINUTES = 15;
	
	@Override
	public void contextInitialized(ServletContextEvent event) {
	    scheduler = Executors.newScheduledThreadPool(16);
	    
	    try {
			
			World world = World.create();
			
			scheduler.scheduleAtFixedRate(() -> {
				
				try {
					Market.updatePrice();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}, 0, PRICE_INTERVAL_MINUTES, TimeUnit.MINUTES);
			
			scheduler.scheduleAtFixedRate(() -> {
				
				try {
					Market.step(scheduler);
					world.step(scheduler);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}, 0, SIMULATION_INTERVAL, TimeUnit.MILLISECONDS);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
	    scheduler.shutdownNow();
	 }

}
