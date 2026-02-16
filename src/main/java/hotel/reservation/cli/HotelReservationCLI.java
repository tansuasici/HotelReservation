package hotel.reservation.cli;

import ai.scop.core.Conversation;
import ai.scop.core.ExecutionState;
import ai.scop.ui.config.Configurator;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interactive CLI for Hotel Reservation System.
 * Directly creates a Playground and uses CustomerAgent's Conversation role for chat.
 */
public class HotelReservationCLI {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println("   HOTEL RESERVATION SYSTEM - Interactive CLI");
        System.out.println("   SCOP + TNSAI Multi-Agent System");
        System.out.println("========================================================");
        System.out.println();

        // 1. Load config
        System.out.println("[CLI] Loading configuration...");
        Configurator.getInstance().load("config.json");

        // 2. Create playground and run on executor
        System.out.println("[CLI] Creating playground...");
        HotelReservationPlayground playground = new HotelReservationPlayground();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(playground);
        executor.shutdown();

        // 3. Wait for paused state
        try {
            System.out.println("[CLI] Starting simulation...");
            playground.getPausedStateLatch().await();
        } catch (InterruptedException e) {
            System.err.println("[CLI] Wait interrupted: " + e.getMessage());
            return;
        }

        // 4. Set state to RUNNING
        playground.setExecutionState(ExecutionState.RUNNING);
        System.out.println("[CLI] Simulation running!");

        // 5. Auto-trigger all customer searches
        playground.triggerAllSearches();
        System.out.println("[CLI] All customer searches triggered.");

        // 6. Find CustomerAgent and get Conversation role
        CustomerAgent customer = (CustomerAgent) playground.findAgent("Customer-1");
        if (customer == null) {
            System.err.println("[CLI] Customer-1 agent not found!");
            playground.setExecutionState(ExecutionState.ENDED);
            return;
        }

        Conversation conversation = customer.as(Conversation.class);
        if (conversation == null) {
            System.err.println("[CLI] Conversation role not found!");
            playground.setExecutionState(ExecutionState.ENDED);
            return;
        }

        System.out.println();
        System.out.println("Ready! Ask your questions about hotels.");
        System.out.println("Type 'exit' to quit.");
        System.out.println();

        // 7. Chat loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("You > ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }

            if (input.isEmpty()) {
                continue;
            }

            String response = conversation.chat(input);
            System.out.println();
            System.out.println("Assistant > " + response);
            System.out.println();
        }

        // 8. Write output files before stopping
        System.out.println("[CLI] Writing output files...");
        playground.writeOutputFiles();
        System.out.println("[CLI] Output files written to: " + Path.of("output-data").toAbsolutePath());

        // 9. Stop
        scanner.close();
        playground.setExecutionState(ExecutionState.ENDED);
        System.out.println("[CLI] Simulation stopped. Goodbye!");
    }
}
