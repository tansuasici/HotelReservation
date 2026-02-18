package hotel.reservation.api;

import ai.scop.core.ExecutionState;
import ai.scop.ui.config.Configurator;
import hotel.reservation.ActivityLog;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.CustomerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class PlaygroundHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaygroundHolder.class);

    private HotelReservationPlayground playground;
    private ExecutorService executor;

    public void setup() {
        if (playground != null) {
            try {
                playground.setExecutionState(ExecutionState.ENDED);
            } catch (Exception e) {
                LOGGER.warn("Error stopping previous playground: {}", e.getMessage());
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        ActivityLog.clear();

        Configurator.getInstance().load("config.json");
        playground = new HotelReservationPlayground();
        executor = Executors.newSingleThreadExecutor();
        executor.submit(playground);
        executor.shutdown();

        try {
            playground.getPausedStateLatch().await();
        } catch (InterruptedException e) {
            LOGGER.error("Setup interrupted: {}", e.getMessage());
        }
        LOGGER.info("Simulation setup complete");
    }

    public void run() {
        if (playground != null) {
            playground.setExecutionState(ExecutionState.RUNNING);
            playground.triggerAllSearches();
            LOGGER.info("Simulation running");
        }
    }

    public void pause() {
        if (playground != null) {
            playground.setExecutionState(ExecutionState.PAUSED);
            LOGGER.info("Simulation paused");
        }
    }

    public void stop() {
        if (playground != null) {
            playground.setExecutionState(ExecutionState.ENDED);
            LOGGER.info("Simulation stopped");
        }
    }

    public HotelReservationPlayground get() {
        return playground;
    }

    public boolean isActive() {
        return playground != null;
    }

    public Map<String, Object> getStatusMap() {
        Map<String, Object> status = new LinkedHashMap<>();
        if (playground == null) {
            status.put("state", "NOT_INITIALIZED");
            status.put("currentTick", 0);
            status.put("agentCount", 0);
            status.put("registeredHotels", 0);
            return status;
        }

        ExecutionState execState = playground.getExecutionState();

        // Auto-end: if RUNNING and all customers finished, switch to ENDED
        if (execState == ExecutionState.RUNNING && allCustomersDone()) {
            playground.setExecutionState(ExecutionState.ENDED);
            execState = ExecutionState.ENDED;
            LOGGER.info("All customers done - simulation auto-ended");
        }

        String stateStr = switch (execState) {
            case RUNNING -> "RUNNING";
            case PAUSED -> "PAUSED";
            case ENDED -> "ENDED";
            default -> "SETUP";
        };

        status.put("state", stateStr);
        status.put("currentTick", playground.getTick() != null ? playground.getTick().now() : 0);

        try {
            var agents = playground.getScenarioAgents();
            status.put("agentCount", agents != null ? agents.size() : 0);
        } catch (Exception e) {
            status.put("agentCount", 0);
        }

        try {
            DirectoryFacilitator df = playground.getDirectoryFacilitator();
            status.put("registeredHotels", df != null ? df.getRegisteredCount() : 0);
        } catch (Exception e) {
            status.put("registeredHotels", 0);
        }

        return status;
    }

    private boolean allCustomersDone() {
        try {
            var customers = playground.getCustomerAgents();
            if (customers.isEmpty()) return false;
            for (CustomerAgent c : customers) {
                CustomerRole role = c.as(CustomerRole.class);
                if (role == null) return false;
                String state = role.getCustomerState().name();
                if (!"COMPLETED".equals(state) && !"FAILED".equals(state)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
