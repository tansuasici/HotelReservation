package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Identifier;
import ai.scop.core.Role;
import ai.scop.core.messaging.Message;
import com.tnsai.annotations.*;
import com.tnsai.annotations.LLMSpec.Provider;
import com.tnsai.enums.ActionType;
import hotel.reservation.ActivityLog;
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
        ),
        @Responsibility(
            name = "Negotiation",
            description = "Negotiate price with hotels when above desired price",
            actions = {"startNegotiation", "handleCounterOfferMessage", "handleNegotiateAcceptMessage", "handleNegotiateRejectMessage"}
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

    // Negotiation state
    @State(description = "Current negotiation round")
    private int negotiationRound = 0;

    @State(description = "Maximum negotiation rounds")
    private int maxNegotiationRounds = 5;

    @State(description = "Proposal being negotiated")
    private RoomProposal negotiatingWith = null;

    @State(description = "Negotiation history")
    private final List<NegotiationOffer> negotiationHistory = new ArrayList<>();

    public enum CustomerState {
        IDLE,
        SEARCHING,
        WAITING_PROPOSALS,
        EVALUATING,
        NEGOTIATING,
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
        negotiatingWith = null;
        negotiationRound = 0;
        negotiationHistory.clear();
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
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "Directory Facilitator not found — cannot search for hotels");
            state = CustomerState.FAILED;
            return;
        }

        // Search for matching hotels
        matchingHotels = df.search(desiredLocation, desiredRank, maxPrice);

        if (matchingHotels.isEmpty()) {
            LOGGER.warn("[{}] No hotels found matching criteria", getOwner().getName());
            ActivityLog.log(getOwner().getName(), "DirectoryFacilitator", "FAIL",
                String.format("No hotels found matching criteria: %d★ in %s, max $%.0f",
                    desiredRank, desiredLocation, maxPrice));
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
            ActivityLog.log(getOwner().getName(), hotel.getHotelName(), "CFP",
                String.format("Looking for %d★ in %s, max $%.0f", desiredRank, desiredLocation, maxPrice));

            pendingResponses.add(hotel.getAgentName());

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
        pendingResponses.remove(message.getSender().getAgentName());

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

        pendingResponses.remove(message.getSender().getAgentName());
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
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "No proposals received from any hotel — all hotels either refused or did not respond");
            state = CustomerState.FAILED;
            return;
        }

        // Select best proposal (lowest price, FCFS for ties)
        selectedProposal = selectBestProposal();

        if (selectedProposal != null) {
            LOGGER.info("[{}] Selected: {} - ${}/night (desired: ${})",
                getOwner().getName(),
                selectedProposal.getHotelName(),
                selectedProposal.getPricePerNight(),
                desiredPrice);
            ActivityLog.log(getOwner().getName(), selectedProposal.getHotelName(), "EVALUATE",
                String.format("Selected best: %s at $%.0f/night", selectedProposal.getHotelName(), selectedProposal.getPricePerNight()));

            if (selectedProposal.getPricePerNight() <= desiredPrice) {
                // Price is within desired range - accept directly
                LOGGER.info("[{}] Price ${} <= desired ${} - accepting directly",
                    getOwner().getName(), selectedProposal.getPricePerNight(), desiredPrice);
                makeReservation();
            } else {
                // Price is above desired but within max - start negotiation
                LOGGER.info("[{}] Price ${} > desired ${} - starting negotiation",
                    getOwner().getName(), selectedProposal.getPricePerNight(), desiredPrice);
                startNegotiation(selectedProposal);
            }
        } else {
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "Could not select a best proposal from received offers");
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
        ActivityLog.log(getOwner().getName(), selectedProposal.getHotelName(), "ACCEPT",
            String.format("Accepting proposal $%.0f/night", selectedProposal.getPricePerNight()));

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
                ActivityLog.log(getOwner().getName(), proposal.getHotelName(), "REJECT",
                    "Another hotel was selected");

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

    // ==========================================
    // NEGOTIATION METHODS
    // ==========================================

    /**
     * Start price negotiation with the best proposal.
     * Sends initial offer at desiredPrice.
     */
    @Action(type = ActionType.LOCAL, description = "Start price negotiation with selected hotel")
    private void startNegotiation(RoomProposal proposal) {
        state = CustomerState.NEGOTIATING;
        negotiatingWith = proposal;
        negotiationRound = 1;
        negotiationHistory.clear();

        // First offer: desiredPrice
        double offerPrice = desiredPrice;

        NegotiationOffer offer = new NegotiationOffer(
            proposal.getProposalId(),
            proposal.getHotelId(),
            proposal.getHotelName(),
            offerPrice,
            proposal.getPricePerNight(),
            negotiationRound,
            maxNegotiationRounds,
            String.format("We'd like to offer $%.2f per night for %s.", offerPrice, proposal.getHotelName())
        );
        negotiationHistory.add(offer);

        LOGGER.info("");
        LOGGER.info("┌─ NEGOTIATION START ──────────────────────────────────┐");
        LOGGER.info("│  Customer: {}                                        │", getOwner().getName());
        LOGGER.info("│  Hotel: {} (${}/night)                              │", proposal.getHotelName(), proposal.getPricePerNight());
        LOGGER.info("│  Our offer: ${}/night                               │", offerPrice);
        LOGGER.info("│  Round: {}/{}                                        │", negotiationRound, maxNegotiationRounds);
        LOGGER.info("└─────────────────────────────────────────────────────┘");
        ActivityLog.log(getOwner().getName(), proposal.getHotelName(), "NEGOTIATE",
            String.format("Opening negotiation: offer $%.0f (listed $%.0f)", offerPrice, proposal.getPricePerNight()));

        // Send to the hotel's role
        HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + proposal.getHotelId());
        if (hotelAgent != null) {
            HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_NEGOTIATE_START, offer, hotelRole.getIdentifier());
            }
        }

        // Reject other proposals
        for (RoomProposal other : proposals.values()) {
            if (!other.getProposalId().equals(proposal.getProposalId())) {
                LOGGER.info("[{}] Sending REJECT to {}", getOwner().getName(), other.getHotelName());
                ActivityLog.log(getOwner().getName(), other.getHotelName(), "REJECT",
                    "Negotiating with another hotel");
                HotelAgent otherHotel = getAgent(HotelAgent.class, "Hotel-" + other.getHotelId());
                if (otherHotel != null) {
                    HotelProviderRole otherRole = otherHotel.as(HotelProviderRole.class);
                    if (otherRole != null) {
                        sendMessage(MessageTypes.MSG_REJECT, "Negotiating with another hotel", otherRole.getIdentifier());
                    }
                }
            }
        }
    }

    /**
     * Handle CounterOffer from hotel during negotiation.
     */
    @Action(type = ActionType.LOCAL, description = "Process hotel's counter-offer during negotiation")
    public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
        NegotiationOffer hotelOffer = message.getPayload();
        negotiationHistory.add(hotelOffer);

        LOGGER.info("[{}] Received counter-offer from {}: ${}/night (round {}/{})",
            getOwner().getName(), hotelOffer.getHotelName(),
            hotelOffer.getOfferedPrice(), hotelOffer.getRound(), hotelOffer.getMaxRounds());

        if (hotelOffer.getOfferedPrice() <= desiredPrice) {
            // Hotel's price is at or below our desired price - accept!
            LOGGER.info("[{}] Hotel offer ${} <= desired ${} - accepting!",
                getOwner().getName(), hotelOffer.getOfferedPrice(), desiredPrice);
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        negotiationRound = hotelOffer.getRound() + 1;

        if (negotiationRound > maxNegotiationRounds) {
            // Max rounds exceeded - accept if within maxPrice, otherwise fail
            if (hotelOffer.getOfferedPrice() <= maxPrice) {
                LOGGER.info("[{}] Max rounds reached. Accepting last offer: ${}",
                    getOwner().getName(), hotelOffer.getOfferedPrice());
                acceptNegotiation(hotelOffer.getOfferedPrice());
            } else {
                LOGGER.info("[{}] Max rounds reached. Hotel offer ${} > maxPrice ${}. Rejecting.",
                    getOwner().getName(), hotelOffer.getOfferedPrice(), maxPrice);
                rejectNegotiation("Price too high after maximum negotiation rounds");
            }
            return;
        }

        // Calculate our counter-offer
        // Strategy: desiredPrice + (maxPrice - desiredPrice) * (round / maxRounds)
        double progress = (double) negotiationRound / maxNegotiationRounds;
        double counterPrice = desiredPrice + (maxPrice - desiredPrice) * progress;

        // Don't offer more than the hotel is asking
        if (counterPrice >= hotelOffer.getOfferedPrice()) {
            LOGGER.info("[{}] Our counter ${} >= hotel offer ${} - accepting hotel offer",
                getOwner().getName(), counterPrice, hotelOffer.getOfferedPrice());
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        LOGGER.info("[{}] Counter-offering ${} (round {}/{})",
            getOwner().getName(), counterPrice, negotiationRound, maxNegotiationRounds);
        ActivityLog.log(getOwner().getName(), hotelOffer.getHotelName(), "COUNTER_OFFER",
            String.format("Counter: $%.0f/night (round %d/%d)", counterPrice, negotiationRound, maxNegotiationRounds));

        NegotiationOffer counter = new NegotiationOffer(
            hotelOffer.getProposalId(),
            hotelOffer.getHotelId(),
            hotelOffer.getHotelName(),
            counterPrice,
            hotelOffer.getOriginalPrice(),
            negotiationRound,
            maxNegotiationRounds,
            String.format("How about $%.2f per night?", counterPrice)
        );
        negotiationHistory.add(counter);

        HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + hotelOffer.getHotelId());
        if (hotelAgent != null) {
            HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, hotelRole.getIdentifier());
            }
        }
    }

    /**
     * Handle NegotiateAccept from hotel - hotel accepted our offer.
     */
    @Action(type = ActionType.LOCAL, description = "Process hotel's acceptance of negotiation offer")
    public void handleNegotiateAcceptMessage(Message<NegotiationOffer> message) {
        NegotiationOffer acceptance = message.getPayload();
        negotiationHistory.add(acceptance);

        LOGGER.info("[{}] Hotel {} accepted negotiation at ${}/night!",
            getOwner().getName(), acceptance.getHotelName(), acceptance.getOfferedPrice());

        acceptNegotiation(acceptance.getOfferedPrice());
    }

    /**
     * Handle NegotiateReject from hotel - hotel rejected negotiation entirely.
     */
    @Action(type = ActionType.LOCAL, description = "Process hotel's rejection of negotiation")
    public void handleNegotiateRejectMessage(Message<String> message) {
        LOGGER.info("[{}] Hotel rejected negotiation: {}", getOwner().getName(), message.getPayload());

        // Try to accept at original price if within maxPrice
        if (negotiatingWith != null && negotiatingWith.getPricePerNight() <= maxPrice) {
            LOGGER.info("[{}] Accepting original price ${}", getOwner().getName(), negotiatingWith.getPricePerNight());
            state = CustomerState.EVALUATING;
            negotiatingWith = null;
            negotiationRound = 0;
            makeReservation();
        } else {
            ActivityLog.log(getOwner().getName(), negotiatingWith != null ? negotiatingWith.getHotelName() : "System",
                "FAIL", "Negotiation rejected by hotel and no acceptable fallback price available");
            state = CustomerState.FAILED;
            LOGGER.warn("[{}] Negotiation failed and no acceptable fallback", getOwner().getName());
        }
    }

    /**
     * Accept a negotiated price and proceed to reservation.
     */
    private void acceptNegotiation(double agreedPrice) {
        LOGGER.info("");
        LOGGER.info("┌─ NEGOTIATION COMPLETE ───────────────────────────────┐");
        LOGGER.info("│  Hotel: {}                                           │", negotiatingWith.getHotelName());
        LOGGER.info("│  Original: ${}/night                                │", negotiatingWith.getPricePerNight());
        LOGGER.info("│  Agreed: ${}/night                                  │", agreedPrice);
        LOGGER.info("│  Savings: ${}/night ({}% off)                       │",
            negotiatingWith.getPricePerNight() - agreedPrice,
            String.format("%.1f", ((negotiatingWith.getPricePerNight() - agreedPrice) / negotiatingWith.getPricePerNight()) * 100));
        LOGGER.info("│  Rounds: {}                                          │", negotiationRound);
        LOGGER.info("└─────────────────────────────────────────────────────┘");

        // Update proposal with negotiated price
        negotiatingWith.setNegotiatedPrice(agreedPrice);
        negotiatingWith.setNegotiated(true);

        // Proceed to reservation
        state = CustomerState.RESERVING;
        makeNegotiatedReservation(agreedPrice);
    }

    /**
     * Reject the negotiation.
     */
    private void rejectNegotiation(String reason) {
        if (negotiatingWith != null) {
            ActivityLog.log(getOwner().getName(), negotiatingWith.getHotelName(), "NEGOTIATE_REJECT", reason);
            HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + negotiatingWith.getHotelId());
            if (hotelAgent != null) {
                HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
                if (hotelRole != null) {
                    sendMessage(MessageTypes.MSG_NEGOTIATE_REJECT, reason, hotelRole.getIdentifier());
                }
            }
        }
        state = CustomerState.FAILED;
        negotiatingWith = null;
        negotiationRound = 0;
    }

    /**
     * Make reservation with the negotiated price.
     */
    private void makeNegotiatedReservation(double negotiatedPrice) {
        ReservationRequest request = new ReservationRequest(
            getOwner().getName(),
            negotiatingWith.getProposalId(),
            negotiatingWith.getHotelId()
        );
        request.setCustomerName(getOwner().getName());
        request.setNumberOfNights(1);
        request.setNegotiatedPrice(negotiatedPrice);

        LOGGER.info("[{}] Sending NegotiateAccept to {} at ${}/night",
            getOwner().getName(), negotiatingWith.getHotelName(), negotiatedPrice);
        ActivityLog.log(getOwner().getName(), negotiatingWith.getHotelName(), "NEGOTIATE_ACCEPT",
            String.format("Deal at $%.0f/night", negotiatedPrice));

        HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + negotiatingWith.getHotelId());
        if (hotelAgent != null) {
            HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_NEGOTIATE_ACCEPT, request, hotelRole.getIdentifier());
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
    public double getDesiredPrice() { return desiredPrice; }
    public int getNegotiationRound() { return negotiationRound; }
    public int getMaxNegotiationRounds() { return maxNegotiationRounds; }
    public RoomProposal getNegotiatingWith() { return negotiatingWith; }
    public List<NegotiationOffer> getNegotiationHistory() { return new ArrayList<>(negotiationHistory); }
}
