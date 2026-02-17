package hotel.reservation.api;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import ai.scop.core.ExecutionState;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.core.env.network.NetworkMetrics;
import ai.scop.ui.config.Configurator;
import hotel.reservation.ActivityLog;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.message.RoomProposal;
import hotel.reservation.role.CustomerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * REST controller for simulation control, network topology,
 * customer status, activity feed, and agent chat.
 */
@RestController
@RequestMapping("/api")
public class SimulationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationController.class);

    private HotelReservationPlayground playground;
    private ExecutorService executor;

    // ==========================================
    // SIMULATION CONTROL
    // ==========================================

    @PostMapping("/simulation")
    public Map<String, Object> simulationAction(@RequestParam String action) {
        switch (action.toLowerCase()) {
            case "setup" -> {
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
            case "run" -> {
                if (playground != null) {
                    playground.setExecutionState(ExecutionState.RUNNING);
                    playground.triggerAllSearches();
                    LOGGER.info("Simulation running");
                }
            }
            case "pause" -> {
                if (playground != null) {
                    playground.setExecutionState(ExecutionState.PAUSED);
                    LOGGER.info("Simulation paused");
                }
            }
            case "stop" -> {
                if (playground != null) {
                    playground.writeOutputFiles();
                    playground.setExecutionState(ExecutionState.ENDED);
                    LOGGER.info("Simulation stopped");
                }
            }
        }
        return getStatusMap();
    }

    @GetMapping("/simulation/status")
    public Map<String, Object> getStatus() {
        return getStatusMap();
    }

    private Map<String, Object> getStatusMap() {
        Map<String, Object> status = new LinkedHashMap<>();
        if (playground == null) {
            status.put("state", "NOT_INITIALIZED");
            status.put("currentTick", 0);
            status.put("agentCount", 0);
            status.put("registeredHotels", 0);
            return status;
        }

        ExecutionState execState = playground.getExecutionState();
        String stateStr = switch (execState) {
            case RUNNING -> "RUNNING";
            case PAUSED -> "PAUSED";
            case ENDED -> "ENDED";
            default -> "SETUP";
        };

        status.put("state", stateStr);
        status.put("currentTick", playground.getTick() != null ? playground.getTick().now() : 0);

        var agents = playground.getScenarioAgents();
        status.put("agentCount", agents != null ? agents.size() : 0);

        DirectoryFacilitator df = playground.getDirectoryFacilitator();
        status.put("registeredHotels", df != null ? df.getRegisteredCount() : 0);

        return status;
    }

    // ==========================================
    // NETWORK TOPOLOGY
    // ==========================================

    @GetMapping("/network/topology")
    public Map<String, Object> getTopology() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        if (playground == null) {
            result.put("nodes", nodes);
            result.put("edges", edges);
            return result;
        }

        Set<String> edgeSet = new HashSet<>();
        var agents = playground.getScenarioAgents();
        if (agents != null) {
            for (var agent : agents) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", agent.getName());
                node.put("displayName", agent.getDisplayName());
                node.put("type", agent.getClass().getSimpleName());

                if (agent instanceof HotelAgent ha) {
                    node.put("hotelId", ha.getHotelId());
                    node.put("location", ha.getLocation());
                    node.put("rank", ha.getRank());
                    node.put("basePrice", ha.getBasePrice());
                    node.put("availableRooms", ha.getAvailableRooms());
                    node.put("totalRooms", ha.getTotalRooms());
                } else if (agent instanceof CustomerAgent ca) {
                    node.put("location", ca.getDesiredLocation());
                    node.put("desiredRank", ca.getDesiredRank());
                    node.put("maxPrice", ca.getMaxPrice());
                }

                nodes.add(node);

                try {
                    var neighbors = playground.getAgentNeighbors(agent);
                    if (neighbors != null) {
                        for (var neighbor : neighbors) {
                            String edgeKey = agent.getName().compareTo(neighbor.getName()) < 0
                                    ? agent.getName() + "->" + neighbor.getName()
                                    : neighbor.getName() + "->" + agent.getName();
                            if (edgeSet.add(edgeKey)) {
                                edges.add(Map.of("from", agent.getName(), "to", neighbor.getName()));
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    @GetMapping("/network/metrics")
    public Map<String, Object> getNetworkMetrics() {
        Map<String, Object> metricsMap = new LinkedHashMap<>();
        if (playground == null) return metricsMap;

        try {
            NetworkEnvironment env = playground.getHotelNetwork();
            if (env != null) {
                NetworkMetrics m = env.getNetworkMetrics();
                metricsMap.put("nodeCount", m.getNodeCount());
                metricsMap.put("edgeCount", m.getEdgeCount());
                metricsMap.put("averageDegree", m.getAverageDegree());
                metricsMap.put("density", m.getDensity());
                metricsMap.put("averageClusteringCoefficient", m.getAverageClusteringCoefficient());
                metricsMap.put("averagePathLength", m.getAveragePathLength());
                metricsMap.put("diameter", m.getDiameter());
                metricsMap.put("connectedComponents", m.getConnectedComponents());
                metricsMap.put("smallWorld", m.isSmallWorld());
                metricsMap.put("smallWorldExplanation", m.getSmallWorldExplanation());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not get network metrics: {}", e.getMessage());
        }

        return metricsMap;
    }

    // ==========================================
    // CUSTOMER STATUS
    // ==========================================

    @GetMapping("/customers/status")
    public List<Map<String, Object>> getCustomersStatus() {
        if (playground == null) return Collections.emptyList();

        return playground.getCustomerAgents().stream()
                .map(this::buildCustomerStatus)
                .collect(Collectors.toList());
    }

    @GetMapping("/customer/{id}/status")
    public ResponseEntity<Map<String, Object>> getCustomerStatus(@PathVariable String id) {
        if (playground == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Simulation not initialized"));
        }

        Agent agent = playground.findAgent(id);
        if (agent instanceof CustomerAgent ca) {
            return ResponseEntity.ok(buildCustomerStatus(ca));
        }
        return ResponseEntity.status(404).body(Map.of("error", "Customer not found: " + id));
    }

    private Map<String, Object> buildCustomerStatus(CustomerAgent customer) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("customerId", customer.getName());
        status.put("desiredLocation", customer.getDesiredLocation());
        status.put("desiredRank", customer.getDesiredRank());
        status.put("maxPrice", customer.getMaxPrice());
        status.put("desiredPrice", customer.getDesiredPrice());

        try {
            CustomerRole role = customer.as(CustomerRole.class);
            if (role != null) {
                status.put("state", role.getCustomerState().name());
                status.put("proposalCount", role.getProposals().size());
                status.put("negotiationRound", role.getNegotiationRound());

                if (role.getNegotiatingWith() != null) {
                    status.put("negotiatingHotel", role.getNegotiatingWith().getHotelName());
                    status.put("lastOffer", role.getNegotiatingWith().getPricePerNight());
                }

                if (role.getSelectedProposal() != null) {
                    RoomProposal sp = role.getSelectedProposal();
                    Map<String, Object> proposal = new LinkedHashMap<>();
                    proposal.put("hotelId", sp.getHotelId());
                    proposal.put("hotelName", sp.getHotelName());
                    proposal.put("pricePerNight", sp.getPricePerNight());
                    status.put("selectedProposal", proposal);
                }

                if (role.getConfirmation() != null) {
                    var c = role.getConfirmation();
                    Map<String, Object> conf = new LinkedHashMap<>();
                    conf.put("confirmationNumber", c.getConfirmationNumber());
                    conf.put("hotelName", c.getHotelName());
                    conf.put("pricePerNight", c.getPricePerNight());
                    conf.put("totalPrice", c.getTotalPrice());
                    conf.put("discountPercent", c.getDiscountPercent());
                    status.put("confirmation", conf);
                }

                List<String> history = role.getNegotiationHistory().stream()
                        .map(o -> String.format("R%d: $%.0f (%s)",
                                o.getRound(), o.getOfferedPrice(), o.getMessage()))
                        .collect(Collectors.toList());
                if (!history.isEmpty()) {
                    status.put("negotiationHistory", history);
                }
            } else {
                status.put("state", "UNKNOWN");
            }
        } catch (Exception e) {
            status.put("state", "ENDED");
        }

        return status;
    }

    // ==========================================
    // ACTIVITY FEED
    // ==========================================

    @GetMapping("/activity")
    public List<Map<String, Object>> getActivity(
            @RequestParam(defaultValue = "0") long since) {
        return ActivityLog.getEntriesSince(since).stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("timestamp", e.timestamp());
                    m.put("from", e.from());
                    m.put("to", e.to());
                    m.put("type", e.type());
                    m.put("detail", e.detail());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ==========================================
    // AGENT CHAT
    // ==========================================

    @PostMapping("/agents/{id}/chat")
    public ResponseEntity<Map<String, Object>> agentChat(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        if (playground == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Simulation not initialized"));
        }

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Message is required"));
        }

        Agent agent = playground.findAgent(id);
        if (agent == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Agent not found: " + id));
        }

        Conversation conversation = agent.as(Conversation.class);
        if (conversation == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Agent does not support chat"));
        }

        String response = conversation.chat(message);
        return ResponseEntity.ok(Map.of("response", response != null ? response : "No response"));
    }

    // ==========================================
    // DF ENTRIES (bonus)
    // ==========================================

    @GetMapping("/df/entries")
    public ResponseEntity<?> getDfEntries() {
        if (playground == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Simulation not initialized"));
        }
        DirectoryFacilitator df = playground.getDirectoryFacilitator();
        if (df == null) {
            return ResponseEntity.status(400).body(Map.of("error", "DF not available"));
        }
        return ResponseEntity.ok(df.getAllEntries());
    }
}
