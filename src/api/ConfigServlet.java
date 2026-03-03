package api;

import config.ConfigManager;
import simulation.ResourceRegistry;
import simulation.ResourceRegistry.Resource;
import simulation.ResourceRegistry.Recipe;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.Map;

/**
 * GET /api/v1/config
 *
 * Returns publicly visible game configuration values.
 * No authentication required — used by the UI and AI agents
 * to display costs, rates, and other tuning parameters.
 */
@WebServlet("/api/v1/config")
public class ConfigServlet extends HttpServlet {

    @Override
    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        ConfigManager cfg = ConfigManager.getInstance();

        JSONObject data = new JSONObject();

        // Economy
        data.put("economy.starting_cash",      cfg.getDouble("economy.starting_cash",      1000.0));
        data.put("economy.market_fee_percent",  cfg.getDouble("economy.market_fee_percent",  2.0));
        data.put("economy.luxury_tax_percent",  cfg.getDouble("economy.luxury_tax_percent",  3.0));

        // Facility build costs
        JSONObject buildCosts = new JSONObject();
        buildCosts.put("raw",                  cfg.getDouble("facility.build_cost.raw",                   100.0));
        buildCosts.put("intermediate",         cfg.getDouble("facility.build_cost.intermediate",           300.0));
        buildCosts.put("advanced_intermediate",cfg.getDouble("facility.build_cost.advanced_intermediate",  800.0));
        buildCosts.put("consumer",             cfg.getDouble("facility.build_cost.consumer",              2000.0));
        data.put("facility_build_costs", buildCosts);

        // Facility operating costs
        JSONObject opCosts = new JSONObject();
        opCosts.put("raw",                  cfg.getDouble("facility.operating_cost.raw",                   2.0));
        opCosts.put("intermediate",         cfg.getDouble("facility.operating_cost.intermediate",           5.0));
        opCosts.put("advanced_intermediate",cfg.getDouble("facility.operating_cost.advanced_intermediate", 12.0));
        opCosts.put("consumer",             cfg.getDouble("facility.operating_cost.consumer",              30.0));
        data.put("facility_operating_costs", opCosts);

        // Simulation
        data.put("simulation.ticks_per_second", cfg.getInt("simulation.ticks_per_second", 4));

        // Facility modifiers
        data.put("facility.idle_cost_multiplier",  cfg.getDouble("facility.idle_cost_multiplier",  0.30));
        data.put("facility.downsize_refund_rate",   cfg.getDouble("facility.downsize_refund_rate",   0.40));

        // Resources (full list with tier, decay, base price, production rate)
        JSONArray resources = new JSONArray();
        for (Resource res : ResourceRegistry.allResources()) {
            JSONObject r = new JSONObject();
            r.put("name",                     res.name);
            r.put("tier",                     res.tier.name().toLowerCase());
            r.put("default_production_tick",  (long) res.defaultProductionPerTick);
            r.put("decay_rate_per_cycle",     res.decayRatePerCycle);
            r.put("base_price",               res.basePrice);
            r.put("demand_cycle_minutes",     (long) res.demandCycleMinutes);
            r.put("is_consumer_good",         res.isConsumerGood());
            r.put("is_perishable",            res.isPerishable());
            r.put("build_cost",               res.getBuildCost());
            r.put("operating_cost_per_tick",  res.getOperatingCostPerTick());
            resources.add(r);
        }
        data.put("resources", resources);

        // Recipes (input requirements for non-raw resources)
        JSONObject recipes = new JSONObject();
        for (Resource res : ResourceRegistry.allResources()) {
            Recipe recipe = ResourceRegistry.getRecipe(res.name);
            if (recipe == null) continue;
            JSONObject rec = new JSONObject();
            rec.put("output_quantity", (long) recipe.outputQuantity);
            JSONObject inputs = new JSONObject();
            for (Map.Entry<String, Integer> entry : recipe.inputs.entrySet()) {
                inputs.put(entry.getKey(), (long) (int) entry.getValue());
            }
            rec.put("inputs", inputs);
            recipes.put(res.name, rec);
        }
        data.put("recipes", recipes);

        JSONObject result = new JSONObject();
        result.put("success", Boolean.TRUE);
        result.put("data",    data);
        result.put("error",   null);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(result.toJSONString());
    }
}
