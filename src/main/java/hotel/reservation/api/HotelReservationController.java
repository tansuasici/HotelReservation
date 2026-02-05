package hotel.reservation.api;

import ai.scop.core.ExecutionState;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.api.dto.*;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.CustomerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * REST Controller for Hotel Reservation Multi-Agent System.
 * Provides endpoints for simulation control and agent interaction.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HotelReservationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelReservationController.class);

    private HotelReservationPlayground playground = null;
    private ExecutorService executor = null;

    // ==========================================
    // SIMULATION CONTROL ENDPOINTS
    // ==========================================

    /**
     * POST /api/simulation?action=setup|run|pause|stop
     * Control the simulation state.
     */
    @PostMapping("/simulation")
    public ResponseEntity<SimulationStatusDTO> simulationAction(
            @RequestParam(value = "action", required = true) String action) {

        LOGGER.info("[API] POST /api/simulation?action={}", action);

        if (action == null || action.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return switch (action.toLowerCase(Locale.ROOT)) {
            case "setup" -> setupSimulation();
            case "run" -> runSimulation();
            case "pause" -> pauseSimulation();
            case "stop" -> stopSimulation();
            default -> ResponseEntity.badRequest().build();
        };
    }

    private ResponseEntity<SimulationStatusDTO> setupSimulation() {
        try {
            LOGGER.info("[API] Setting up simulation...");

            // Create playground
            playground = new HotelReservationPlayground();

            // Start on executor thread
            executor = Executors.newSingleThreadExecutor();
            executor.submit(playground);
            executor.shutdown();

            // Wait for paused state
            playground.getPausedStateLatch().await();

            LOGGER.info("[API] Simulation setup complete");
            return ResponseEntity.ok(getSimulationStatus());

        } catch (Exception e) {
            LOGGER.error("[API] Setup failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<SimulationStatusDTO> runSimulation() {
        if (playground == null) {
            return ResponseEntity.badRequest().body(
                new SimulationStatusDTO("NOT_INITIALIZED", 0, 0, 0, "Simulation not set up"));
        }
        playground.setExecutionState(ExecutionState.RUNNING);
        LOGGER.info("[API] Simulation running");
        return ResponseEntity.ok(getSimulationStatus());
    }

    private ResponseEntity<SimulationStatusDTO> pauseSimulation() {
        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }
        playground.setExecutionState(ExecutionState.PAUSED);
        LOGGER.info("[API] Simulation paused");
        return ResponseEntity.ok(getSimulationStatus());
    }

    private ResponseEntity<SimulationStatusDTO> stopSimulation() {
        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }
        playground.setExecutionState(ExecutionState.ENDED);
        LOGGER.info("[API] Simulation stopped");
        return ResponseEntity.ok(getSimulationStatus());
    }

    /**
     * GET /api/simulation/status
     * Get current simulation status.
     */
    @GetMapping("/simulation/status")
    public ResponseEntity<SimulationStatusDTO> getStatus() {
        LOGGER.info("[API] GET /api/simulation/status");

        if (playground == null) {
            return ResponseEntity.ok(new SimulationStatusDTO(
                "NOT_INITIALIZED", 0, 0, 0, "Simulation not set up"));
        }

        return ResponseEntity.ok(getSimulationStatus());
    }

    private SimulationStatusDTO getSimulationStatus() {
        DirectoryFacilitator df = playground.getDirectoryFacilitator();
        return new SimulationStatusDTO(
            playground.getExecutionState().name(),
            playground.getTick().now().longValue(),
            playground.getScenarioAgents().size(),
            df != null ? df.getRegisteredCount() : 0,
            "OK"
        );
    }

    // ==========================================
    // DIRECTORY FACILITATOR ENDPOINTS
    // ==========================================

    /**
     * GET /api/df/entries
     * List all DF registrations.
     */
    @GetMapping("/df/entries")
    public ResponseEntity<List<DFEntryDTO>> getDFEntries() {
        LOGGER.info("[API] GET /api/df/entries");

        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }

        DirectoryFacilitator df = playground.getDirectoryFacilitator();
        List<DFEntryDTO> entries = df.getAllEntries().stream()
            .map(DFEntryDTO::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/df/search
     * Search DF for hotels.
     */
    @GetMapping("/df/search")
    public ResponseEntity<List<DFEntryDTO>> searchDF(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minRank,
            @RequestParam(required = false) Double maxPrice) {

        LOGGER.info("[API] GET /api/df/search?location={}&minRank={}&maxPrice={}",
            location, minRank, maxPrice);

        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }

        DirectoryFacilitator df = playground.getDirectoryFacilitator();
        List<DFEntryDTO> results = df.search(location, minRank, maxPrice).stream()
            .map(DFEntryDTO::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    // ==========================================
    // SEARCH & BOOKING ENDPOINTS
    // ==========================================

    /**
     * POST /api/search
     * Start hotel search for a customer.
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> startSearch(@RequestBody SearchRequestDTO request) {
        LOGGER.info("[API] POST /api/search: {}", request);

        if (playground == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Simulation not set up"));
        }

        // Find or create customer
        String customerId = request.getCustomerId();
        if (customerId == null) {
            customerId = "Customer-1";
        }

        CustomerAgent customer = (CustomerAgent) playground.findAgent(customerId);
        if (customer == null) {
            // Create new customer if not exists
            customer = playground.addCustomer(
                customerId,
                request.getLocation(),
                request.getMinRank() != null ? request.getMinRank() : 3,
                request.getMaxPrice() != null ? request.getMaxPrice() : 500
            );
        }

        // Start search
        customer.startSearch();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "customerId", customerId,
            "message", "Search started for " + request.getLocation()
        ));
    }

    /**
     * GET /api/proposals
     * Get proposals for a customer.
     */
    @GetMapping("/proposals")
    public ResponseEntity<List<ProposalDTO>> getProposals(
            @RequestParam(value = "customerId", defaultValue = "Customer-1") String customerId) {

        LOGGER.info("[API] GET /api/proposals?customerId={}", customerId);

        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }

        CustomerAgent customer = (CustomerAgent) playground.findAgent(customerId);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        CustomerRole role = customer.as(CustomerRole.class);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }

        List<ProposalDTO> proposals = role.getProposals().values().stream()
            .map(ProposalDTO::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(proposals);
    }

    /**
     * GET /api/customer/{id}/status
     * Get customer status.
     */
    @GetMapping("/customer/{id}/status")
    public ResponseEntity<?> getCustomerStatus(@PathVariable String id) {
        LOGGER.info("[API] GET /api/customer/{}/status", id);

        if (playground == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Playground not initialized"));
        }

        // Check if simulation has ended (agents cleaned up)
        if (playground.getScenarioAgents() == null) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                    "error", "Simulation has ended",
                    "message", "Agents are no longer available. Run 'setup' to start a new simulation."
                ));
        }

        CustomerAgent customer = (CustomerAgent) playground.findAgent(id);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        CustomerRole role = customer.as(CustomerRole.class);
        return ResponseEntity.ok(CustomerStatusDTO.from(customer, role));
    }

    // ==========================================
    // HOTEL MANAGEMENT ENDPOINTS
    // ==========================================

    /**
     * GET /api/hotels
     * List all hotel agents.
     */
    @GetMapping("/hotels")
    public ResponseEntity<?> getHotelAgents() {
        LOGGER.info("[API] GET /api/hotels");

        if (playground == null || playground.getScenarioAgents() == null) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                    "error", "Simulation not active",
                    "message", "Run 'setup' to start a new simulation."
                ));
        }

        List<HotelAgentDTO> hotels = playground.getScenarioAgents().stream()
            .filter(a -> a instanceof HotelAgent)
            .map(a -> HotelAgentDTO.from((HotelAgent) a))
            .collect(Collectors.toList());

        return ResponseEntity.ok(hotels);
    }

    /**
     * POST /api/hotels
     * Add a new hotel agent by hotelId (fetches data from Hotel Data API).
     */
    @PostMapping(value = "/hotels", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addHotelAgent(@RequestBody Map<String, String> request) {
        String hotelId = request.get("hotelId");
        LOGGER.info("[API] POST /api/hotels: hotelId={}", hotelId);

        if (playground == null) {
            return ResponseEntity.badRequest().build();
        }

        if (hotelId == null || hotelId.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "hotelId is required"));
        }

        // Add hotel agent - it will fetch its data from Hotel Data API
        HotelAgent hotel = playground.addHotel(hotelId);

        if (!hotel.isDataLoaded()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Failed to load hotel data from API",
                    "hotelId", hotelId,
                    "message", "Make sure the hotel exists in Hotel Data API"
                ));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(HotelAgentDTO.from(hotel));
    }

    // ==========================================
    // AGENTS ENDPOINTS
    // ==========================================

    /**
     * GET /api/agents
     * List all agents.
     */
    @GetMapping("/agents")
    public ResponseEntity<?> getAgents() {
        LOGGER.info("[API] GET /api/agents");

        if (playground == null || playground.getScenarioAgents() == null) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                    "error", "Simulation not active",
                    "message", "Run 'setup' to start a new simulation."
                ));
        }

        List<Map<String, String>> agents = playground.getScenarioAgents().stream()
            .map(a -> Map.of(
                "name", a.getName(),
                "displayName", a.getDisplayName(),
                "type", a.getClass().getSimpleName()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(agents);
    }

}
