package hotel.reservation.cli;

import ai.scop.core.ExecutionState;
import ai.scop.ui.config.Configurator;
import com.fasterxml.jackson.databind.ObjectMapper;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.role.CustomerRole;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Headless simulation runner controlled via file-based commands.
 *
 * Communication:
 *   output-data/.sim-state   — written by this runner (JSON: { state, message })
 *   output-data/.sim-command — read by this runner (plain text: run / pause / stop)
 *
 * Lifecycle: NOT_INITIALIZED → PAUSED (setup done) → RUNNING → ENDED
 */
public class SimulationRunner {

    private static final Path OUTPUT_DIR = Path.of("output-data");
    private static final Path STATE_FILE = OUTPUT_DIR.resolve(".sim-state");
    private static final Path COMMAND_FILE = OUTPUT_DIR.resolve(".sim-command");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static HotelReservationPlayground playground;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        clearCommand();
        writeState("NOT_INITIALIZED", "Starting...");

        // 1. Load config
        Configurator.getInstance().load("config.json");

        // 2. Create playground
        writeState("NOT_INITIALIZED", "Creating playground...");
        playground = new HotelReservationPlayground();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(playground);
        executor.shutdown();

        // 3. Wait for setup to complete (playground reaches PAUSED state)
        playground.getPausedStateLatch().await();

        // 4. Write topology immediately after setup
        playground.writeOutputFiles();
        writeState("PAUSED", "Setup complete. Ready to run.");

        // 5. Command loop — poll for commands
        boolean running = true;
        while (running) {
            String cmd = readCommand();
            if (cmd != null) {
                clearCommand();
                switch (cmd) {
                    case "run" -> {
                        playground.setExecutionState(ExecutionState.RUNNING);
                        writeState("RUNNING", "Simulation running");
                        // Stagger customer searches with short delay for frontend visibility
                        new Thread(() -> {
                            for (CustomerAgent c : playground.getCustomerAgents()) {
                                if (playground.getExecutionState() != ExecutionState.RUNNING) break;
                                c.startSearch();
                                try { Thread.sleep(300); } catch (InterruptedException ignored) { break; }
                            }
                        }).start();
                    }
                    case "pause" -> {
                        playground.setExecutionState(ExecutionState.PAUSED);
                        playground.writeOutputFiles();
                        writeState("PAUSED", "Simulation paused");
                    }
                    case "stop" -> {
                        playground.writeOutputFiles();
                        playground.setExecutionState(ExecutionState.ENDED);
                        writeState("ENDED", "Simulation stopped");
                        running = false;
                    }
                }
            }

            // During RUNNING: write output files every tick so frontend sees progress
            if (playground.getExecutionState() == ExecutionState.RUNNING) {
                playground.writeOutputFiles();
                writeState("RUNNING", "Simulation running");

                if (allCustomersDone()) {
                    playground.writeOutputFiles(); // Final flush — capture last state
                    playground.setExecutionState(ExecutionState.ENDED);
                    writeState("ENDED", "Simulation completed");
                    running = false;
                }
            }

            if (running) {
                Thread.sleep(500);
            }
        }

        // Give time for state file to be read
        Thread.sleep(500);
    }

    private static boolean allCustomersDone() {
        try {
            for (CustomerAgent c : playground.getCustomerAgents()) {
                CustomerRole role = c.as(CustomerRole.class);
                if (role == null) return false;
                String state = role.getCustomerState().name();
                if (!"COMPLETED".equals(state) && !"FAILED".equals(state)) {
                    return false;
                }
            }
            return !playground.getCustomerAgents().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeState(String state, String message) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("state", state);
            map.put("message", message);
            if (playground != null && playground.getTick() != null) {
                map.put("currentTick", playground.getTick().now().intValue());
            }
            String json = MAPPER.writeValueAsString(map);
            Files.writeString(STATE_FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Runner] Failed to write state: " + e.getMessage());
        }
    }

    private static String readCommand() {
        try {
            if (Files.exists(COMMAND_FILE)) {
                String cmd = Files.readString(COMMAND_FILE).trim().toLowerCase();
                return cmd.isEmpty() ? null : cmd;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void clearCommand() {
        try {
            Files.deleteIfExists(COMMAND_FILE);
        } catch (IOException ignored) {
        }
    }
}
