package hotel.reservation.api;

import ai.scop.core.Agent;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.ui.command.impl.exec.web_service.dto.NetworkEnvironmentDTO;
import ai.scop.ui.command.impl.exec.web_service.dto.NetworkMetricsDTO;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/network")
public class TopologyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopologyController.class);

    private final PlaygroundHolder holder;

    public TopologyController(PlaygroundHolder holder) {
        this.holder = holder;
    }

    @GetMapping("/topology")
    public ResponseEntity<?> getTopology() {
        if (!holder.isActive()) {
            return ResponseEntity.ok(Map.of("nodes", List.of(), "edges", List.of()));
        }

        var playground = holder.get();
        List<NetworkEnvironmentDTO> networks = playground.getAgents(NetworkEnvironment.class).stream()
                .map(NetworkEnvironmentDTO::new)
                .toList();

        if (networks.isEmpty()) {
            return ResponseEntity.ok(Map.of("nodes", List.of(), "edges", List.of()));
        }

        NetworkEnvironmentDTO net = networks.get(0);

        List<Map<String, Object>> enrichedNodes = net.nodes().stream().map(node -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("name", node.name());
            n.put("type", node.type());
            n.put("colorCode", node.colorCode());
            n.put("degree", node.degree());
            n.put("neighbors", node.neighbors());

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

    @GetMapping("/metrics")
    public ResponseEntity<?> getNetworkMetrics() {
        if (!holder.isActive()) return ResponseEntity.ok(Map.of());

        try {
            NetworkEnvironment env = holder.get().getHotelNetwork();
            if (env != null) {
                return ResponseEntity.ok(new NetworkMetricsDTO(env.getNetworkMetrics()));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not get network metrics: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of());
    }
}
