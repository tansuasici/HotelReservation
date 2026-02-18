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
import hotel.reservation.config.EnvConfig;
import hotel.reservation.role.pricing.BuyerPricingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    private final BuyerPricingStrategy pricingStrategy;

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
    private static final long PROPOSAL_DEADLINE_MS = EnvConfig.cnpProposalDeadlineMs();

    // Store DFEntry list for sending messages
    private List<DFEntry> matchingHotels = new ArrayList<>();

    // Negotiation state
    @State(description = "Current negotiation round")
    private int negotiationRound = 0;

    @State(description = "Maximum negotiation rounds")
    private int maxNegotiationRounds = EnvConfig.cnpMaxNegotiationRounds();

    @State(description = "Proposal being negotiated")
    private RoomProposal negotiatingWith = null;

    @State(description = "Negotiation history")
    private final List<NegotiationOffer> negotiationHistory = new ArrayList<>();

    // Top-N candidate system
    private static final int MAX_CANDIDATES = EnvConfig.cnpMaxCandidates();

    @State(description = "Shortlisted hotel candidates for sequential negotiation")
    private List<RoomProposal> topCandidates = new ArrayList<>();

    @State(description = "Current candidate index being negotiated")
    private int currentCandidateIndex = 0;

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
                        double maxPrice, double desiredPrice,
                        BuyerPricingStrategy pricingStrategy) {
        super(owner, envName);
        this.desiredLocation = desiredLocation;
        this.desiredRank = desiredRank;
        this.maxPrice = maxPrice;
        this.desiredPrice = desiredPrice;
        this.pricingStrategy = pricingStrategy;
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

        // Register ALL pending responses FIRST to prevent race condition
        // (a fast REFUSE could trigger evaluation before all CFPs are sent)
        for (DFEntry hotel : hotels) {
            pendingResponses.add(hotel.getAgentName());
        }

        // Now send CFPs
        for (DFEntry hotel : hotels) {
            LOGGER.info("[{}] Sending CFP to {}", getOwner().getName(), hotel.getHotelName());
            ActivityLog.log(getOwner().getName(), hotel.getHotelName(), "CFP",
                String.format("Looking for %d★ in %s, max $%.0f", desiredRank, desiredLocation, maxPrice));

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
     * Evaluate collected proposals: shortlist top candidates for sequential negotiation.
     */
    @Action(type = ActionType.LOCAL, description = "Evaluate all proposals and shortlist top candidates")
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

        // Shortlist top N candidates sorted by price
        topCandidates = proposals.values().stream()
            .sorted(Comparator
                .comparingDouble(RoomProposal::getPricePerNight)
                .thenComparingLong(RoomProposal::getTimestamp))
            .limit(MAX_CANDIDATES)
            .collect(Collectors.toList());
        currentCandidateIndex = 0;

        if (topCandidates.isEmpty()) {
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "Could not select any candidate from received offers");
            state = CustomerState.FAILED;
            return;
        }

        selectedProposal = topCandidates.get(0);

        // Log shortlist
        String shortlistStr = topCandidates.stream()
            .map(p -> String.format("%s ($%.0f)", p.getHotelName(), p.getPricePerNight()))
            .collect(Collectors.joining(", "));
        LOGGER.info("[{}] Shortlisted {} candidates: {}", getOwner().getName(), topCandidates.size(), shortlistStr);
        ActivityLog.log(getOwner().getName(), "System", "SHORTLIST",
            String.format("Top %d candidates: %s", topCandidates.size(), shortlistStr));

        // Reject proposals NOT in shortlist
        Set<String> topIds = topCandidates.stream()
            .map(RoomProposal::getProposalId)
            .collect(Collectors.toSet());
        for (RoomProposal other : proposals.values()) {
            if (!topIds.contains(other.getProposalId())) {
                rejectProposal(other, "Not in shortlist");
            }
        }

        // Best candidate price acceptable? → accept directly
        if (selectedProposal.getPricePerNight() <= desiredPrice) {
            LOGGER.info("[{}] Best offer ${} <= desired ${} - accepting directly",
                getOwner().getName(), selectedProposal.getPricePerNight(), desiredPrice);
            ActivityLog.log(getOwner().getName(), selectedProposal.getHotelName(), "EVALUATE",
                String.format("Best price $%.0f ≤ desired $%.0f — accepting directly",
                    selectedProposal.getPricePerNight(), desiredPrice));
            rejectRemainingCandidates(0);
            makeReservation();
        } else {
            // Start negotiation with first candidate
            startNegotiationWithCandidate(0);
        }
    }

    /**
     * Send REJECT to a proposal.
     */
    private void rejectProposal(RoomProposal proposal, String reason) {
        LOGGER.info("[{}] Rejecting {} ({})", getOwner().getName(), proposal.getHotelName(), reason);
        ActivityLog.log(getOwner().getName(), proposal.getHotelName(), "REJECT", reason);
        HotelAgent hotel = getAgent(HotelAgent.class, "Hotel-" + proposal.getHotelId());
        if (hotel != null) {
            HotelProviderRole role = hotel.as(HotelProviderRole.class);
            if (role != null) {
                sendMessage(MessageTypes.MSG_REJECT, reason, role.getIdentifier());
            }
        }
    }

    /**
     * Reject remaining shortlisted candidates (except the one at acceptedIndex).
     */
    private void rejectRemainingCandidates(int acceptedIndex) {
        for (int i = 0; i < topCandidates.size(); i++) {
            if (i != acceptedIndex) {
                rejectProposal(topCandidates.get(i), "Another hotel was selected");
            }
        }
    }

    /**
     * Get the next competing candidate's price for leverage (if available).
     */
    private RoomProposal getCompetingCandidate() {
        int nextIdx = currentCandidateIndex + 1;
        return nextIdx < topCandidates.size() ? topCandidates.get(nextIdx) : null;
    }

    /**
     * Make reservation with selected hotel (direct accept, no negotiation needed).
     */
    @Action(type = ActionType.LOCAL, description = "Send acceptance to selected hotel")
    private void makeReservation() {
        state = CustomerState.RESERVING;

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

        HotelAgent selectedHotel = getAgent(HotelAgent.class, "Hotel-" + selectedProposal.getHotelId());
        if (selectedHotel != null) {
            HotelProviderRole hotelRole = selectedHotel.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_ACCEPT, request, hotelRole.getIdentifier());
            }
        }
    }

    // ==========================================
    // NEGOTIATION METHODS
    // ==========================================

    /**
     * Start price negotiation with the candidate at given index.
     * Uses competing candidate's price as leverage if available.
     */
    @Action(type = ActionType.LOCAL, description = "Start price negotiation with candidate hotel")
    private void startNegotiationWithCandidate(int candidateIndex) {
        if (candidateIndex >= topCandidates.size()) {
            LOGGER.warn("[{}] All {} candidate hotels exhausted — FAILED",
                getOwner().getName(), topCandidates.size());
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                String.format("All %d candidate hotels exhausted after sequential negotiation", topCandidates.size()));
            state = CustomerState.FAILED;
            return;
        }

        currentCandidateIndex = candidateIndex;
        RoomProposal proposal = topCandidates.get(candidateIndex);
        selectedProposal = proposal;
        state = CustomerState.NEGOTIATING;
        negotiatingWith = proposal;
        negotiationRound = 1;
        negotiationHistory.clear();

        // First offer: desiredPrice
        double offerPrice = desiredPrice;

        // Leverage: mention competing offer
        RoomProposal competing = getCompetingCandidate();
        String leverageMsg = "";
        if (competing != null) {
            leverageMsg = String.format(" We have a competing offer from another hotel at $%.0f/night.",
                competing.getPricePerNight());
        }

        NegotiationOffer offer = new NegotiationOffer(
            proposal.getProposalId(),
            proposal.getHotelId(),
            proposal.getHotelName(),
            offerPrice,
            proposal.getPricePerNight(),
            negotiationRound,
            maxNegotiationRounds,
            String.format("We'd like to offer $%.2f per night for %s.%s", offerPrice, proposal.getHotelName(), leverageMsg)
        );
        negotiationHistory.add(offer);

        LOGGER.info("");
        LOGGER.info("┌─ NEGOTIATION START ──────────────────────────────────┐");
        LOGGER.info("│  Customer: {}  [candidate {}/{}]                    │",
            getOwner().getName(), candidateIndex + 1, topCandidates.size());
        LOGGER.info("│  Hotel: {} (${}/night)                              │", proposal.getHotelName(), proposal.getPricePerNight());
        LOGGER.info("│  Our offer: ${}/night                               │", offerPrice);
        if (competing != null) {
            LOGGER.info("│  Leverage: {} at ${}/night                          │", competing.getHotelName(), competing.getPricePerNight());
        }
        LOGGER.info("│  Round: {}/{}                                        │", negotiationRound, maxNegotiationRounds);
        LOGGER.info("└─────────────────────────────────────────────────────┘");

        String logDetail = String.format("Negotiation [%d/%d]: offer $%.0f (listed $%.0f)",
            candidateIndex + 1, topCandidates.size(), offerPrice, proposal.getPricePerNight());
        if (competing != null) {
            logDetail += String.format(" — leverage: %s at $%.0f", competing.getHotelName(), competing.getPricePerNight());
        }
        ActivityLog.log(getOwner().getName(), proposal.getHotelName(), "NEGOTIATE", logDetail);

        // Send to the hotel's role
        HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + proposal.getHotelId());
        if (hotelAgent != null) {
            HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
            if (hotelRole != null) {
                sendMessage(MessageTypes.MSG_NEGOTIATE_START, offer, hotelRole.getIdentifier());
            }
        }
    }

    /**
     * Handle CounterOffer from hotel during negotiation.
     * Uses leverage (competing candidate price) in counter-offers.
     */
    @Action(type = ActionType.LOCAL, description = "Process hotel's counter-offer during negotiation")
    public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
        NegotiationOffer hotelOffer = message.getPayload();
        negotiationHistory.add(hotelOffer);

        LOGGER.info("[{}] Received counter-offer from {}: ${}/night (round {}/{})",
            getOwner().getName(), hotelOffer.getHotelName(),
            hotelOffer.getOfferedPrice(), hotelOffer.getRound(), hotelOffer.getMaxRounds());

        if (hotelOffer.getOfferedPrice() <= desiredPrice) {
            LOGGER.info("[{}] Hotel offer ${} <= desired ${} - accepting!",
                getOwner().getName(), hotelOffer.getOfferedPrice(), desiredPrice);
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        negotiationRound = hotelOffer.getRound() + 1;

        if (negotiationRound > maxNegotiationRounds) {
            if (hotelOffer.getOfferedPrice() <= maxPrice) {
                LOGGER.info("[{}] Max rounds reached. Accepting last offer: ${}",
                    getOwner().getName(), hotelOffer.getOfferedPrice());
                acceptNegotiation(hotelOffer.getOfferedPrice());
            } else {
                LOGGER.info("[{}] Max rounds reached, offer ${} > max ${}. Trying next candidate.",
                    getOwner().getName(), hotelOffer.getOfferedPrice(), maxPrice);
                // Reject this hotel and try next candidate
                rejectAndTryNext("Price too high after maximum negotiation rounds");
            }
            return;
        }

        // Counter-offer strategy with leverage
        double counterPrice = pricingStrategy.counterOffer(desiredPrice, maxPrice, negotiationRound, maxNegotiationRounds);

        // Don't offer more than the hotel is asking
        if (counterPrice >= hotelOffer.getOfferedPrice()) {
            LOGGER.info("[{}] Our counter ${} >= hotel offer ${} - accepting hotel offer",
                getOwner().getName(), counterPrice, hotelOffer.getOfferedPrice());
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        // Leverage message: mention competing offer
        RoomProposal competing = getCompetingCandidate();
        String leverageMsg = "";
        if (competing != null && competing.getPricePerNight() < hotelOffer.getOfferedPrice()) {
            leverageMsg = String.format(" We have a competing offer at $%.0f/night.", competing.getPricePerNight());
        }

        LOGGER.info("[{}] Counter-offering ${} (round {}/{})",
            getOwner().getName(), counterPrice, negotiationRound, maxNegotiationRounds);
        String logDetail = String.format("Counter: $%.0f/night (round %d/%d)", counterPrice, negotiationRound, maxNegotiationRounds);
        if (!leverageMsg.isEmpty()) {
            logDetail += " — with leverage";
        }
        ActivityLog.log(getOwner().getName(), hotelOffer.getHotelName(), "COUNTER_OFFER", logDetail);

        NegotiationOffer counter = new NegotiationOffer(
            hotelOffer.getProposalId(),
            hotelOffer.getHotelId(),
            hotelOffer.getHotelName(),
            counterPrice,
            hotelOffer.getOriginalPrice(),
            negotiationRound,
            maxNegotiationRounds,
            String.format("How about $%.2f per night?%s", counterPrice, leverageMsg)
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
     * Tries to accept at listed price, or falls back to next candidate.
     */
    @Action(type = ActionType.LOCAL, description = "Process hotel's rejection of negotiation")
    public void handleNegotiateRejectMessage(Message<String> message) {
        LOGGER.info("[{}] Hotel rejected negotiation: {}", getOwner().getName(), message.getPayload());

        // Option 1: Accept at original listed price if within maxPrice
        if (negotiatingWith != null && negotiatingWith.getPricePerNight() <= maxPrice) {
            LOGGER.info("[{}] Accepting original price ${}", getOwner().getName(), negotiatingWith.getPricePerNight());
            state = CustomerState.EVALUATING;
            negotiatingWith = null;
            negotiationRound = 0;
            makeReservation();
            return;
        }

        // Option 2: Try next candidate
        rejectAndTryNext("Negotiation rejected by hotel");
    }

    /**
     * Reject current negotiation and try the next candidate in the shortlist.
     */
    private void rejectAndTryNext(String reason) {
        String currentHotel = negotiatingWith != null ? negotiatingWith.getHotelName() : "unknown";

        if (negotiatingWith != null) {
            ActivityLog.log(getOwner().getName(), currentHotel, "NEGOTIATE_REJECT", reason);
            HotelAgent hotelAgent = getAgent(HotelAgent.class, "Hotel-" + negotiatingWith.getHotelId());
            if (hotelAgent != null) {
                HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
                if (hotelRole != null) {
                    sendMessage(MessageTypes.MSG_NEGOTIATE_REJECT, reason, hotelRole.getIdentifier());
                }
            }
        }

        int nextIdx = currentCandidateIndex + 1;
        if (nextIdx < topCandidates.size()) {
            LOGGER.info("[{}] Moving to next candidate [{}/{}]: {}",
                getOwner().getName(), nextIdx + 1, topCandidates.size(),
                topCandidates.get(nextIdx).getHotelName());
            ActivityLog.log(getOwner().getName(), topCandidates.get(nextIdx).getHotelName(), "FALLBACK",
                String.format("Switching from %s to next candidate [%d/%d]",
                    currentHotel, nextIdx + 1, topCandidates.size()));
            startNegotiationWithCandidate(nextIdx);
        } else {
            LOGGER.warn("[{}] All {} candidates exhausted — FAILED", getOwner().getName(), topCandidates.size());
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                String.format("All %d candidate hotels exhausted", topCandidates.size()));
            state = CustomerState.FAILED;
            negotiatingWith = null;
            negotiationRound = 0;
        }
    }

    /**
     * Accept a negotiated price, reject remaining candidates, and proceed to reservation.
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
        LOGGER.info("│  Rounds: {}  [candidate {}/{}]                      │",
            negotiationRound, currentCandidateIndex + 1, topCandidates.size());
        LOGGER.info("└─────────────────────────────────────────────────────┘");

        // Update proposal with negotiated price
        negotiatingWith.setNegotiatedPrice(agreedPrice);
        negotiatingWith.setNegotiated(true);

        // Reject remaining candidates that we didn't negotiate with
        rejectRemainingCandidates(currentCandidateIndex);

        // Proceed to reservation
        state = CustomerState.RESERVING;
        makeNegotiatedReservation(agreedPrice);
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
    public List<RoomProposal> getTopCandidates() { return new ArrayList<>(topCandidates); }
    public int getCurrentCandidateIndex() { return currentCandidateIndex; }
}
