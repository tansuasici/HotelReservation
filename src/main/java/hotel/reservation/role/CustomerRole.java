package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Identifier;
import ai.scop.core.Role;
import ai.scop.core.messaging.Message;
import com.tnsai.annotations.*;
import com.tnsai.annotations.LLMSpec.Provider;
import com.tnsai.enums.ActionType;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DFEntry;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Customer Role - Implements the CNP initiator behavior.
 * Searches for hotels, collects proposals, and makes reservations.
 */
@RoleSpec(
    description = "Hotel reservation customer that searches for hotels and makes bookings",
    responsibilities = {
        @Responsibility(
            name = "HotelSearch",
            description = "Search for hotels matching criteria via Directory Facilitator",
            actions = {"startSearch", "queryDirectoryFacilitator"}
        ),
        @Responsibility(
            name = "ProposalCollection",
            description = "Collect and evaluate proposals from hotels",
            actions = {"handleProposalMessage", "evaluateProposals"}
        ),
        @Responsibility(
            name = "Reservation",
            description = "Make reservation with selected hotel",
            actions = {"makeReservation", "handleConfirmMessage"}
        )
    },
    llm = @LLMSpec(
        provider = Provider.OLLAMA,
        model = "glm-4.7:cloud",
        temperature = 0.7f
    ),
    communication = @Communication(
        style = @Communication.Style(
            tone = Communication.Tone.FRIENDLY,
            verbosity = Communication.Verbosity.NORMAL
        ),
        languages = {"tr", "en"}
    )
)
public class CustomerRole extends Role {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerRole.class);

    // Search criteria
    @State(description = "Desired hotel location/city")
    private final String desiredLocation;

    @State(description = "Minimum star rating required")
    private final int desiredRank;

    @State(description = "Maximum price per night")
    private final double maxPrice;

    @State(description = "Desired price for negotiation")
    private final double desiredPrice;

    // State management
    @State(description = "Current state in the reservation process")
    private CustomerState state = CustomerState.IDLE;

    private final Map<String, RoomProposal> proposals = new ConcurrentHashMap<>();
    private final Set<String> pendingResponses = Collections.synchronizedSet(new HashSet<>());

    @State(description = "Currently selected hotel proposal")
    private RoomProposal selectedProposal = null;

    @State(description = "Final reservation confirmation")
    private ReservationConfirmation confirmation = null;

    // Deadline management
    private long searchStartTime;
    private static final long PROPOSAL_DEADLINE_MS = 30000;  // 30 seconds

    // Store DFEntry list for sending messages
    private List<DFEntry> matchingHotels = new ArrayList<>();

    public enum CustomerState {
        IDLE,
        SEARCHING,
        WAITING_PROPOSALS,
        EVALUATING,
        RESERVING,
        COMPLETED,
        FAILED
    }

    public CustomerRole(Agent owner, String envName,
                        String desiredLocation, int desiredRank,
                        double maxPrice, double desiredPrice) {
        super(owner, envName);
        this.desiredLocation = desiredLocation;
        this.desiredRank = desiredRank;
        this.maxPrice = maxPrice;
        this.desiredPrice = desiredPrice;
    }

    /**
     * Start the hotel search process.
     */
    @Action(type = ActionType.LOCAL, description = "Initiate hotel search based on criteria")
    public void startSearch() {
        if (state != CustomerState.IDLE && state != CustomerState.FAILED) {
            LOGGER.warn("[{}] Cannot start search - already in state: {}",
                getOwner().getName(), state);
            return;
        }

        state = CustomerState.SEARCHING;
        proposals.clear();
        pendingResponses.clear();
        selectedProposal = null;
        confirmation = null;
        searchStartTime = System.currentTimeMillis();

        LOGGER.info("");
        LOGGER.info("┌─ CNP: SEARCH ─────────────────────────────────────────┐");
        LOGGER.info("│  Customer: {}                                         │", getOwner().getName());
        LOGGER.info("│  Looking for: {}★ hotel in {} (max ${})              │", desiredRank, desiredLocation, maxPrice);
        LOGGER.info("└──────────────────────────────────────────────────────┘");

        // Query Directory Facilitator for hotel agents
        queryDirectoryFacilitator();
    }

    /**
     * Query the DF for matching hotel agents and send CFP to each.
     */
    @Action(type = ActionType.LOCAL, description = "Query Directory Facilitator for matching hotels")
    private void queryDirectoryFacilitator() {
        DirectoryFacilitator df = getOwner().getPlayground()
            .getAgent(DirectoryFacilitator.class, "DF");

        if (df == null) {
            LOGGER.error("[{}] Directory Facilitator not found!", getOwner().getName());
            state = CustomerState.FAILED;
            return;
        }

        // Search for matching hotels
        matchingHotels = df.search(desiredLocation, desiredRank, maxPrice);

        if (matchingHotels.isEmpty()) {
            LOGGER.warn("[{}] No hotels found matching criteria", getOwner().getName());
            state = CustomerState.FAILED;
            return;
        }

        LOGGER.info("[{}] Found {} matching hotels in DF", getOwner().getName(), matchingHotels.size());

        // Send CFP to each matching hotel
        state = CustomerState.WAITING_PROPOSALS;
        broadcastCFP(matchingHotels);
    }

    /**
     * Broadcast CFP to all matching hotel agents.
     */
    @Action(type = ActionType.LOCAL, description = "Send Call For Proposals to all matching hotels")
    private void broadcastCFP(List<DFEntry> hotels) {
        RoomQuery query = new RoomQuery(
            getOwner().getName(),
            desiredLocation,
            desiredRank,
            maxPrice
        );

        for (DFEntry hotel : hotels) {
            LOGGER.info("[{}] Sending CFP to {}", getOwner().getName(), hotel.getHotelName());

            pendingResponses.add(hotel.getAgentId());

            // Find the hotel agent and get its HotelProviderRole identifier
            HotelAgent hotelAgent = getAgent(HotelAgent.class, hotel.getAgentName());
            if (hotelAgent != null) {
                HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
                if (hotelRole != null) {
                    sendMessage(MessageTypes.MSG_CFP, query, hotelRole.getIdentifier());
                }
            }
        }

        LOGGER.info("[{}] CFP broadcast complete - waiting for {} responses",
            getOwner().getName(), pendingResponses.size());
    }

    /**
     * Handle Proposal message from a hotel.
     */
    @Action(type = ActionType.LOCAL, description = "Process incoming proposal from hotel")
    public void handleProposalMessage(Message<RoomProposal> message) {
        if (state != CustomerState.WAITING_PROPOSALS) {
            LOGGER.warn("[{}] Received proposal in unexpected state: {}",
                getOwner().getName(), state);
            return;
        }

        RoomProposal proposal = message.getPayload();
        LOGGER.info("[{}] Received proposal from {}: {} - ${}/night",
            getOwner().getName(), message.getSender(),
            proposal.getHotelName(), proposal.getPricePerNight());

        // Store proposal
        proposals.put(proposal.getProposalId(), proposal);
        pendingResponses.remove(message.getSender().toString());

        // Check if all responses received or deadline passed
        checkProposalDeadline();
    }

    /**
     * Handle Refuse message from a hotel.
     */
    @Action(type = ActionType.LOCAL, description = "Process refusal from hotel")
    public void handleRefuseMessage(Message<String> message) {
        LOGGER.info("[{}] Received refusal from {}: {}",
            getOwner().getName(), message.getSender(), message.getPayload());

        pendingResponses.remove(message.getSender().toString());
        checkProposalDeadline();
    }

    /**
     * Check if we should evaluate proposals (deadline or all responses received).
     */
    private void checkProposalDeadline() {
        boolean allResponded = pendingResponses.isEmpty();
        boolean deadlinePassed = System.currentTimeMillis() - searchStartTime > PROPOSAL_DEADLINE_MS;

        if (allResponded || deadlinePassed) {
            if (deadlinePassed) {
                LOGGER.info("[{}] Proposal deadline reached", getOwner().getName());
            }
            evaluateProposals();
        }
    }

    /**
     * Evaluate collected proposals and select the best one.
     */
    @Action(type = ActionType.LOCAL, description = "Evaluate all proposals and select the best one")
    public void evaluateProposals() {
        state = CustomerState.EVALUATING;

        LOGGER.info("[{}] Evaluating {} proposals", getOwner().getName(), proposals.size());

        if (proposals.isEmpty()) {
            LOGGER.warn("[{}] No proposals received - reservation FAILED", getOwner().getName());
            state = CustomerState.FAILED;
            return;
        }

        // Select best proposal (lowest price, FCFS for ties)
        selectedProposal = selectBestProposal();

        if (selectedProposal != null) {
            LOGGER.info("[{}] Selected: {} - ${}/night",
                getOwner().getName(),
                selectedProposal.getHotelName(),
                selectedProposal.getPricePerNight());

            makeReservation();
        } else {
            state = CustomerState.FAILED;
        }
    }

    /**
     * Select the best proposal based on price (FCFS for ties).
     */
    private RoomProposal selectBestProposal() {
        return proposals.values().stream()
            .sorted(Comparator
                .comparingDouble(RoomProposal::getPricePerNight)
                .thenComparingLong(RoomProposal::getTimestamp))
            .findFirst()
            .orElse(null);
    }

    /**
     * Make reservation with selected hotel and reject others.
     */
    @Action(type = ActionType.LOCAL, description = "Send acceptance to selected hotel and reject others")
    private void makeReservation() {
        state = CustomerState.RESERVING;

        // Send Accept to selected hotel
        ReservationRequest request = new ReservationRequest(
            getOwner().getName(),
            selectedProposal.getProposalId(),
            selectedProposal.getHotelId()
        );
        request.setCustomerName(getOwner().getName());
        request.setNumberOfNights(1);

        LOGGER.info("[{}] Sending ACCEPT to {}", getOwner().getName(), selectedProposal.getHotelName());

        // Find the selected hotel agent
        HotelAgent selectedHotel = getAgent(HotelAgent.class, "Hotel-" + selectedProposal.getHotelId());
        if (selectedHotel != null) {
            HotelProviderRole hotelRole = selectedHotel.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_ACCEPT, request, hotelRole.getIdentifier());
            }
        }

        // Send Reject to other hotels
        for (RoomProposal proposal : proposals.values()) {
            if (!proposal.getProposalId().equals(selectedProposal.getProposalId())) {
                LOGGER.info("[{}] Sending REJECT to {}",
                    getOwner().getName(), proposal.getHotelName());

                HotelAgent otherHotel = getAgent(HotelAgent.class, "Hotel-" + proposal.getHotelId());
                if (otherHotel != null) {
                    HotelProviderRole otherRole = otherHotel.as(HotelProviderRole.class);
                    if (otherRole != null) {
                        sendMessage(MessageTypes.MSG_REJECT, "Another hotel was selected", otherRole.getIdentifier());
                    }
                }
            }
        }
    }

    /**
     * Handle Confirmation message from hotel.
     */
    @Action(type = ActionType.LOCAL, description = "Process reservation confirmation from hotel")
    public void handleConfirmMessage(Message<ReservationConfirmation> message) {
        confirmation = message.getPayload();
        state = CustomerState.COMPLETED;

        LOGGER.info("========================================");
        LOGGER.info("[{}] RESERVATION CONFIRMED!", getOwner().getName());
        LOGGER.info("  Confirmation #: {}", confirmation.getConfirmationNumber());
        LOGGER.info("  Hotel: {}", confirmation.getHotelName());
        LOGGER.info("  Price: ${}/night", confirmation.getPricePerNight());
        LOGGER.info("  Total: ${}", confirmation.getTotalPrice());
        LOGGER.info("  Status: {}", confirmation.getStatus());
        LOGGER.info("========================================");
    }

    // Getters for state inspection
    public CustomerState getCustomerState() { return state; }
    public Map<String, RoomProposal> getProposals() { return new HashMap<>(proposals); }
    public RoomProposal getSelectedProposal() { return selectedProposal; }
    public ReservationConfirmation getConfirmation() { return confirmation; }
    public String getDesiredLocation() { return desiredLocation; }
    public int getDesiredRank() { return desiredRank; }
    public double getMaxPrice() { return maxPrice; }
}
