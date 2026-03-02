package servlet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import simulation.Market;
import simulation.World;

@WebListener
public class SimulationServlet implements ServletContextListener {
    
	private ScheduledExecutorService scheduler;
	
	public static long SIMULATION_INTERVAL = 1000;
	public static long PRICE_INTERVAL_MINUTES = 15;
	
	@Override
	public void contextInitialized(ServletContextEvent event) {
	    try {
	        scheduler = Executors.newScheduledThreadPool(16);

	        World world = World.create();

	        scheduler.scheduleAtFixedRate(() -> {
	            try {
	                Market.updatePrice();
	            } catch (Exception e) {
	                System.err.println("Error in Market.updatePrice: " + e.getMessage());
	                e.printStackTrace(System.err);
	            }
	        }, 0, PRICE_INTERVAL_MINUTES, TimeUnit.MINUTES);

	        scheduler.scheduleAtFixedRate(() -> {
	            try {
	                Market.step(scheduler);
	                world.step(scheduler);
	            } catch (Exception e) {
	                System.err.println("Error in simulation step: " + e.getMessage());
	                e.printStackTrace(System.err);
	            }
	        }, 0, SIMULATION_INTERVAL, TimeUnit.MILLISECONDS);

	        System.out.println("Simulation initialized successfully");
	    } catch (Exception e) {
	        System.err.println("FATAL: Failed to initialize SimulationServlet: " + e.getMessage());
	        e.printStackTrace(System.err);
	        throw new RuntimeException("SimulationServlet initialization failed", e);
	    }
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
	    scheduler.shutdownNow();
	 }

}
