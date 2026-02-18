package hotel.reservation.api;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import ai.scop.core.ExecutionState;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.ui.command.impl.exec.web_service.dto.NetworkEnvironmentDTO;
import ai.scop.ui.command.impl.exec.web_service.dto.NetworkMetricsDTO;
import ai.scop.ui.config.Configurator;
import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.integration.scop.SCOPBridge;
import hotel.reservation.ActivityLog;
import hotel.reservation.HotelReservationPlayground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.message.RoomProposal;
import hotel.reservation.role.CustomerRole;
import hotel.reservation.role.HotelProviderRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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

        // Auto-end: if RUNNING and all customers finished, switch to ENDED
        if (execState == ExecutionState.RUNNING && allCustomersDone()) {
            playground.setExecutionState(ExecutionState.ENDED);
            execState = ExecutionState.ENDED;
            LOGGER.info("All customers done — simulation auto-ended");
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

    // ==========================================
    // NETWORK TOPOLOGY
    // ==========================================

    @GetMapping("/network/topology")
    public ResponseEntity<?> getTopology() {
        if (playground == null) {
            return ResponseEntity.ok(Map.of("nodes", List.of(), "edges", List.of()));
        }

        List<NetworkEnvironmentDTO> networks = playground.getAgents(NetworkEnvironment.class).stream()
                .map(NetworkEnvironmentDTO::new)
                .toList();

        if (networks.isEmpty()) {
            return ResponseEntity.ok(Map.of("nodes", List.of(), "edges", List.of()));
        }

        // Base topology from scop-ui DTO
        NetworkEnvironmentDTO net = networks.get(0);

        // Enrich nodes with hotel-reservation specific fields
        List<Map<String, Object>> enrichedNodes = net.nodes().stream().map(node -> {
            Map<String, Object> n = new LinkedHashMap<>();
            // Generic fields from scop-ui NodeDTO
            n.put("name", node.name());
            n.put("type", node.type());
            n.put("colorCode", node.colorCode());
            n.put("degree", node.degree());
            n.put("neighbors", node.neighbors());

            // Domain-specific enrichment
            Agent agent = playground.findAgent(node.name());
            if (agent instanceof HotelAgent ha) {
                n.put("displayName", ha.getDisplayName());
                n.put("hotelId", ha.getHotelId());
                n.put("location", ha.getLocation());
                n.put("rank", ha.getRank());
                n.put("basePrice", ha.getBasePrice());
                n.put("availableRooms", ha.getAvailableRooms());
                n.put("totalRooms", ha.getTotalRooms());
            } else if (agent instanceof CustomerAgent ca) {
                n.put("displayName", ca.getDisplayName());
                n.put("location", ca.getDesiredLocation());
                n.put("desiredRank", ca.getDesiredRank());
                n.put("maxPrice", ca.getMaxPrice());
            }

            return n;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "name", net.name(),
                "nodes", enrichedNodes,
                "edges", net.edges()
        ));
    }

    @GetMapping("/network/metrics")
    public ResponseEntity<?> getNetworkMetrics() {
        if (playground == null) return ResponseEntity.ok(Map.of());

        try {
            NetworkEnvironment env = playground.getHotelNetwork();
            if (env != null) {
                return ResponseEntity.ok(new NetworkMetricsDTO(env.getNetworkMetrics()));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not get network metrics: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of());
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
    // AGENTS (metadata, prompt, log)
    // ==========================================

    @GetMapping("/agents")
    public List<Map<String, Object>> getAgents() {
        if (playground == null) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            var agents = playground.getScenarioAgents();
            if (agents == null) return result;
            for (var agent : agents) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", agent.getName());
                m.put("displayName", agent.getDisplayName());
                m.put("type", agent.getClass().getSimpleName());

                AgentSpec spec = agent.getClass().getAnnotation(AgentSpec.class);
                if (spec != null) {
                    LLMSpec llm = spec.llm();
                    if (llm != null && !llm.model().isEmpty()) {
                        Map<String, Object> llmMap = new LinkedHashMap<>();
                        llmMap.put("provider", llm.provider().name());
                        llmMap.put("model", llm.model());
                        llmMap.put("temperature", llm.temperature());
                        m.put("llm", llmMap);
                    }
                }
                result.add(m);
            }
        } catch (Exception e) {
            LOGGER.warn("Error building agent list: {}", e.getMessage());
        }
        return result;
    }

    @GetMapping(value = "/agents/{id}/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAgentPrompt(@PathVariable String id) {
        if (playground == null) {
            return ResponseEntity.status(400).body("Simulation not initialized");
        }

        Agent agent = playground.findAgent(id);
        if (agent == null) {
            return ResponseEntity.status(404).body("Agent not found: " + id);
        }

        try {
            SCOPBridge bridge = SCOPBridge.getInstance();
            if (agent instanceof CustomerAgent ca) {
                CustomerRole role = ca.as(CustomerRole.class);
                if (role != null) {
                    return ResponseEntity.ok(bridge.buildSystemPromptFromAnnotations(role));
                }
            } else if (agent instanceof HotelAgent ha) {
                HotelProviderRole role = ha.as(HotelProviderRole.class);
                if (role != null) {
                    return ResponseEntity.ok(bridge.buildSystemPromptFromAnnotations(role));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error building prompt for {}: {}", id, e.getMessage());
        }

        return ResponseEntity.ok("");
    }

    @GetMapping(value = "/agents/{id}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAgentLog(@PathVariable String id) {
        if (playground == null) {
            return ResponseEntity.status(400).body("Simulation not initialized");
        }

        // Build log from activity entries involving this agent
        Agent agent = playground.findAgent(id);
        String displayName = agent != null ? agent.getDisplayName() : id;

        StringBuilder sb = new StringBuilder();
        for (var entry : ActivityLog.getEntriesSince(0)) {
            if (id.equals(entry.from()) || id.equals(entry.to()) ||
                displayName.equals(entry.from()) || displayName.equals(entry.to())) {
                sb.append(String.format("[%s] %s → %s: %s%n",
                        entry.type(), entry.from(), entry.to(), entry.detail()));
            }
        }
        return ResponseEntity.ok(sb.toString());
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
