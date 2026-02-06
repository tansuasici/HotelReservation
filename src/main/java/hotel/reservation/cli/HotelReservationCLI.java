package hotel.reservation.cli;

import ai.scop.core.Conversation;
import ai.scop.core.ExecutionState;
import ai.scop.ui.config.Configurator;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;

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
        System.out.println("   OTEL REZERVASYON SISTEMI - Interaktif CLI");
        System.out.println("   SCOP + TNSAI Multi-Agent System");
        System.out.println("========================================================");
        System.out.println();

        // 1. Load config
        System.out.println("[CLI] Konfigurasyon yukleniyor...");
        Configurator.getInstance().load("config.json");

        // 2. Create playground and run on executor
        System.out.println("[CLI] Playground olusturuluyor...");
        HotelReservationPlayground playground = new HotelReservationPlayground();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(playground);
        executor.shutdown();

        // 3. Wait for paused state
        try {
            System.out.println("[CLI] Simulasyon baslatiliyor...");
            playground.getPausedStateLatch().await();
        } catch (InterruptedException e) {
            System.err.println("[CLI] Bekleme kesildi: " + e.getMessage());
            return;
        }

        // 4. Set state to RUNNING
        playground.setExecutionState(ExecutionState.RUNNING);
        System.out.println("[CLI] Simulasyon calisiyor!");

        // 5. Find CustomerAgent and get Conversation role
        CustomerAgent customer = (CustomerAgent) playground.findAgent("Customer-1");
        if (customer == null) {
            System.err.println("[CLI] Customer-1 agent bulunamadi!");
            playground.setExecutionState(ExecutionState.ENDED);
            return;
        }

        Conversation conversation = customer.as(Conversation.class);
        if (conversation == null) {
            System.err.println("[CLI] Conversation role bulunamadi!");
            playground.setExecutionState(ExecutionState.ENDED);
            return;
        }

        System.out.println();
        System.out.println("Hazir! Otel hakkinda sorularinizi yazin.");
        System.out.println("Cikmak icin 'exit' yazin.");
        System.out.println();

        // 6. Chat loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Siz > ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }

            if (input.isEmpty()) {
                continue;
            }

            String response = conversation.chat(input);
            System.out.println();
            System.out.println("Asistan > " + response);
            System.out.println();
        }

        // 7. Stop
        scanner.close();
        playground.setExecutionState(ExecutionState.ENDED);
        System.out.println("[CLI] Simulasyon durduruldu. Gorusuruz!");
    }
}
