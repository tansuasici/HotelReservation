package hotel.reservation.api;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.integration.scop.SCOPBridge;
import hotel.reservation.ActivityLog;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.CustomerRole;
import hotel.reservation.role.HotelProviderRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class AgentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentController.class);

    private final PlaygroundHolder holder;

    public AgentController(PlaygroundHolder holder) {
        this.holder = holder;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> getAgents() {
        if (!holder.isActive()) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            var agents = holder.get().getScenarioAgents();
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
        if (!holder.isActive()) {
            return ResponseEntity.status(400).body("Simulation not initialized");
        }

        Agent agent = holder.get().findAgent(id);
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
        if (!holder.isActive()) {
            return ResponseEntity.status(400).body("Simulation not initialized");
        }

        Agent agent = holder.get().findAgent(id);
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

    @PostMapping("/agents/{id}/chat")
    public ResponseEntity<Map<String, Object>> agentChat(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        if (!holder.isActive()) {
            return ResponseEntity.status(400).body(Map.of("error", "Simulation not initialized"));
        }

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Message is required"));
        }

        Agent agent = holder.get().findAgent(id);
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

    @GetMapping("/df/entries")
    public ResponseEntity<?> getDfEntries() {
        if (!holder.isActive()) {
            return ResponseEntity.status(400).body(Map.of("error", "Simulation not initialized"));
        }
        DirectoryFacilitator df = holder.get().getDirectoryFacilitator();
        if (df == null) {
            return ResponseEntity.status(400).body(Map.of("error", "DF not available"));
        }
        return ResponseEntity.ok(df.getAllEntries());
    }
}
