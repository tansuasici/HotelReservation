package hotel.reservation.api;

import ai.scop.core.Agent;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.message.RoomProposal;
import hotel.reservation.role.CustomerRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CustomerStatusController {

    private final PlaygroundAccess holder;

    public CustomerStatusController(PlaygroundAccess holder) {
        this.holder = holder;
    }

    @GetMapping("/customers/allDone")
    public Map<String, Object> allDone() {
        if (!holder.isActive()) {
            return Map.of("allDone", false);
        }
        try {
            var customers = holder.get().getCustomerAgents();
            if (customers.isEmpty()) {
                return Map.of("allDone", false);
            }
            for (CustomerAgent c : customers) {
                CustomerRole role = c.as(CustomerRole.class);
                if (role == null) return Map.of("allDone", false);
                String state = role.getCustomerState().name();
                if (!"COMPLETED".equals(state) && !"FAILED".equals(state)) {
                    return Map.of("allDone", false);
                }
            }
            return Map.of("allDone", true);
        } catch (Exception e) {
            return Map.of("allDone", false);
        }
    }

    @GetMapping("/customers/status")
    public List<Map<String, Object>> getCustomersStatus() {
        if (!holder.isActive()) return Collections.emptyList();

        return holder.get().getCustomerAgents().stream()
                .map(this::buildCustomerStatus)
                .collect(Collectors.toList());
    }

    @GetMapping("/customer/{id}/status")
    public ResponseEntity<Map<String, Object>> getCustomerStatus(@PathVariable String id) {
        if (!holder.isActive()) {
            return ResponseEntity.status(404).body(Map.of("error", "Simulation not initialized"));
        }

        Agent agent = holder.get().findAgent(id);
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
}
