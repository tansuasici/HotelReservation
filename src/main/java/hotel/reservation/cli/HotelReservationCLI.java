package hotel.reservation.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

/**
 * Interactive CLI for Hotel Reservation System.
 * Connects to the REST API server.
 */
public class HotelReservationCLI {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   OTEL REZERVASYON SİSTEMİ - İnteraktif CLI            ║");
        System.out.println("║   LLM: glm-4.7 (Customer) + minimax-m2.1 (Hotels)      ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();

        // Check if server is running
        if (!checkServer()) {
            System.out.println("❌ Server çalışmıyor! Önce başlat:");
            System.out.println("   mvn spring-boot:run");
            return;
        }

        // Setup simulation
        System.out.println("🔧 Simülasyon başlatılıyor...");
        if (!setupSimulation()) {
            System.out.println("❌ Simülasyon başlatılamadı!");
            return;
        }

        System.out.println("✅ Sistem hazır!");
        System.out.println();
        System.out.println("💬 Ne aramak istiyorsun? (çıkmak için 'exit' yaz)");
        System.out.println("   Örnek: Ankarada 4 yildizli otel bul, butcem 300 dolar");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Check for pending question
            String pendingQuestion = getPendingQuestion();
            if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                System.out.println("❓ Agent soruyor: " + pendingQuestion);
                System.out.print("🧑 Cevabın: ");
            } else {
                System.out.print("🧑 Sen: ");
            }

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
                System.out.println("\n👋 Görüşürüz!");
                break;
            }

            if (input.isEmpty()) {
                continue;
            }

            if (input.equalsIgnoreCase("history") || input.equalsIgnoreCase("h")) {
                showConversation();
                continue;
            }

            // Send message
            if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                sendResponse(input);
            } else {
                sendChat(input);
            }

            // Wait and show conversation
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            showLatestMessages();
            System.out.println();
        }

        scanner.close();
    }

    private static boolean checkServer() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/simulation/status"))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean setupSimulation() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/simulation?action=setup"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                int hotels = json.path("registeredHotels").asInt();
                System.out.println("   " + hotels + " otel yüklendi");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Setup error: " + e.getMessage());
            return false;
        }
    }

    private static void sendChat(String message) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("message", message));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                JsonNode json = objectMapper.readTree(response.body());
                System.out.println("⚠️  " + json.path("error").asText());
            }
        } catch (Exception e) {
            System.err.println("Chat error: " + e.getMessage());
        }
    }

    private static void sendResponse(String response) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("response", response));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/respond"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Response error: " + e.getMessage());
        }
    }

    private static String getPendingQuestion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/llm-status"))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                if (json.path("hasPendingQuestion").asBoolean()) {
                    return json.path("pendingQuestion").asText();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static void showLatestMessages() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/conversation"))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode history = json.path("history");

                // Show last 5 messages
                int start = Math.max(0, history.size() - 5);
                for (int i = start; i < history.size(); i++) {
                    JsonNode entry = history.get(i);
                    String from = entry.path("from").asText();
                    String to = entry.path("to").asText();
                    String msg = entry.path("message").asText();

                    String icon = getIcon(from);
                    System.out.println(icon + " " + from + " → " + to + ": \"" + msg + "\"");
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void showConversation() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/conversation"))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode history = json.path("history");

                System.out.println("\n📜 Konuşma Geçmişi:");
                System.out.println("─".repeat(50));

                for (JsonNode entry : history) {
                    String from = entry.path("from").asText();
                    String to = entry.path("to").asText();
                    String msg = entry.path("message").asText();

                    String icon = getIcon(from);
                    System.out.println(icon + " " + from + " → " + to);
                    System.out.println("   \"" + msg + "\"");
                }
                System.out.println("─".repeat(50));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static String getIcon(String from) {
        if (from.startsWith("Hotel") || from.contains("Hotel")) return "🏨";
        if (from.startsWith("Customer")) return "🤖";
        if (from.equals("User")) return "🧑";
        return "📨";
    }
}
