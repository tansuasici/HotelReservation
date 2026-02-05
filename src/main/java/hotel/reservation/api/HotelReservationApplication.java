package hotel.reservation.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot Application for Hotel Reservation Multi-Agent System.
 * Provides REST API endpoints for controlling the simulation and interacting with agents.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"hotel.reservation"})
public class HotelReservationApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Hotel Reservation Multi-Agent System");
        System.out.println("Starting REST API Server...");
        System.out.println("========================================");

        SpringApplication.run(HotelReservationApplication.class, args);

        System.out.println("========================================");
        System.out.println("Server started on http://localhost:8080");
        System.out.println("========================================");
        System.out.println("Available endpoints:");
        System.out.println("  - GET  /api/data/hotels         - List all hotels (Data API)");
        System.out.println("  - GET  /api/data/hotels/search  - Search hotels");
        System.out.println("  - POST /api/simulation?action=  - Control simulation");
        System.out.println("  - GET  /api/simulation/status   - Get simulation status");
        System.out.println("  - POST /api/search              - Start hotel search");
        System.out.println("  - GET  /api/proposals           - Get proposals");
        System.out.println("  - GET  /api/df/entries          - List DF entries");
        System.out.println("========================================");
    }
}
