package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Identifier;
import ai.scop.core.Role;
import ai.scop.core.messaging.Message;
import com.tnsai.actions.ActionParams;
import com.tnsai.actions.ActionResult;
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
import com.tnsai.integration.scop.SCOPBridge;
import com.tnsai.llm.LLMClient;
import java.time.LocalDate;
import java.time.Month;
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
    private LLMClient llmClient;
    private boolean llmClientResolved = false;

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

    // Market analytics (populated by @AfterAction hooks)
    @State(description = "Total proposals received for analytics")
    private int totalProposalCount = 0;

    @State(description = "Sum of all proposal prices for average calculation")
    private double proposalPriceSum = 0.0;

    @State(description = "Lowest proposal price seen")
    private double lowestProposalPrice = Double.MAX_VALUE;

    @State(description = "Highest proposal price seen")
    private double highestProposalPrice = 0.0;

    // Top-N candidate system
    private static final int MAX_CANDIDATES = EnvConfig.cnpMaxCandidates();

    @State(description = "Shortlisted hotel candidates for sequential negotiation")
    private List<RoomProposal> topCandidates = new ArrayList<>();

    @State(description = "Current candidate index being negotiated")
    private int currentCandidateIndex = 0;

    // ── Tick-driven protocol: handlers store data, tickCheck() processes one step per tick ──
    private NegotiationOffer pendingCounterOffer = null;
    private NegotiationOffer pendingNegAccept = null;
    private String pendingNegReject = null;
    private ReservationConfirmation pendingConfirm = null;
    private String pendingReservationRefused = null;  // REFUSE received during RESERVING
    private int reservingTickCount = 0;               // Timeout counter for RESERVING state
    private static final int RESERVING_TIMEOUT_TICKS = 10; // Max ticks to wait for CONFIRM

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

    private LLMClient getLLMClient() {
        if (!llmClientResolved) {
            llmClientResolved = true;
            llmClient = SCOPBridge.getInstance().resolveLLMClient(this).orElse(null);
        }
        return llmClient;
    }

    /**
     * Start the hotel search process.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Initiate hotel search based on criteria")
    public void startSearch() {
        if (state != CustomerState.IDLE && state != CustomerState.FAILED) {
            getLogger().warn("[{}] Cannot start search - already in state: {}",
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
        totalProposalCount = 0;
        proposalPriceSum = 0.0;
        lowestProposalPrice = Double.MAX_VALUE;
        highestProposalPrice = 0.0;
        pendingCounterOffer = null;
        pendingNegAccept = null;
        pendingNegReject = null;
        pendingConfirm = null;
        pendingReservationRefused = null;
        reservingTickCount = 0;
        searchStartTime = System.currentTimeMillis();

        getLogger().info("");
        getLogger().info("┌─ CNP: SEARCH ─────────────────────────────────────────┐");
        getLogger().info("│  Customer: {}                                         │", getOwner().getName());
        getLogger().info("│  Looking for: {}★ hotel in {} (max ${})              │", desiredRank, desiredLocation, maxPrice);
        getLogger().info("└──────────────────────────────────────────────────────┘");

        // Query Directory Facilitator for hotel agents
        queryDirectoryFacilitator();
    }

    /**
     * Query the DF for matching hotel agents and send CFP to each.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Query Directory Facilitator for matching hotels", excludeFromLLM = true)
    public void queryDirectoryFacilitator() {
        DirectoryFacilitator df = getOwner().getPlayground()
            .getAgent(DirectoryFacilitator.class, "DF");

        if (df == null) {
            getLogger().error("[{}] Directory Facilitator not found!", getOwner().getName());
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "Directory Facilitator not found — cannot search for hotels");
            state = CustomerState.FAILED;
            return;
        }

        // Search for matching hotels
        matchingHotels = df.search(desiredLocation, desiredRank, maxPrice);

        if (matchingHotels.isEmpty()) {
            getLogger().warn("[{}] No hotels found matching criteria", getOwner().getName());
            ActivityLog.log(getOwner().getName(), "DirectoryFacilitator", "FAIL",
                String.format("No hotels found matching criteria: %d★ in %s, max $%.0f",
                    desiredRank, desiredLocation, maxPrice));
            state = CustomerState.FAILED;
            return;
        }

        getLogger().info("[{}] Found {} matching hotels in DF", getOwner().getName(), matchingHotels.size());

        // Send CFP to each matching hotel
        state = CustomerState.WAITING_PROPOSALS;
        broadcastCFP(matchingHotels);
    }

    // ==========================================
    // HOOK: Seasonal Price Adjustment for CFP
    // ==========================================

    /**
     * Before broadcasting CFP, adjust maxPrice based on season.
     * Summer/holiday months (Jun-Aug, Dec): increase maxPrice by 15-20%
     * Shoulder season (Apr-May, Sep-Oct): no change
     * Off-season (Jan-Mar, Nov): decrease maxPrice by 10%
     */
    @BeforeActionSpec("broadcastCFP")
    private ActionParams beforeBroadcastCFP(ActionParams params) {
        Month currentMonth = LocalDate.now().getMonth();
        double effectiveMaxPrice = maxPrice;
        String seasonLabel;

        switch (currentMonth) {
            case JUNE, JULY, AUGUST -> {
                effectiveMaxPrice = maxPrice * 1.15; // Summer: +15%
                seasonLabel = "SUMMER (+15%)";
            }
            case DECEMBER -> {
                effectiveMaxPrice = maxPrice * 1.20; // Holiday: +20%
                seasonLabel = "HOLIDAY (+20%)";
            }
            case JANUARY, FEBRUARY, MARCH, NOVEMBER -> {
                effectiveMaxPrice = maxPrice * 0.90; // Off-season: -10%
                seasonLabel = "OFF_SEASON (-10%)";
            }
            default -> {
                seasonLabel = "SHOULDER (no change)";
            }
        }

        effectiveMaxPrice = Math.round(effectiveMaxPrice * 100.0) / 100.0;
        params.set("effectiveMaxPrice", effectiveMaxPrice);
        params.set("seasonLabel", seasonLabel);

        getLogger().info("[{}] SEASON_ADJUST: month={}, season={}, maxPrice ${} → effective ${}",
            getOwner().getName(), currentMonth, seasonLabel, maxPrice, effectiveMaxPrice);
        ActivityLog.log(getOwner().getName(), "System", "SEASON_ADJUST",
            String.format("Season: %s — Budget $%.0f → $%.0f", seasonLabel, maxPrice, effectiveMaxPrice));

        return params;
    }

    /**
     * Broadcast CFP to all matching hotel agents.
     * Uses seasonally adjusted maxPrice via @BeforeAction hook.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Send Call For Proposals to all matching hotels", excludeFromLLM = true)
    public void broadcastCFP(List<DFEntry> hotels) {
        // Before-hook: seasonal price adjustment
        ActionParams params = new ActionParams(new HashMap<>(), "broadcastCFP");
        params = beforeBroadcastCFP(params);
        double effectiveMaxPrice = params.getDouble("effectiveMaxPrice");

        RoomQuery query = new RoomQuery(
            getOwner().getName(),
            desiredLocation,
            desiredRank,
            effectiveMaxPrice
        );

        // Register ALL pending responses FIRST to prevent race condition
        // (a fast REFUSE could trigger evaluation before all CFPs are sent)
        for (DFEntry hotel : hotels) {
            pendingResponses.add(hotel.getAgentName());
        }

        // Now send CFPs
        for (DFEntry hotel : hotels) {
            getLogger().info("[{}] Sending CFP to {}", getOwner().getName(), hotel.getHotelName());
            ActivityLog.log(getOwner().getName(), hotel.getHotelName(), "CFP",
                String.format("Looking for %d★ in %s, max $%.0f", desiredRank, desiredLocation, effectiveMaxPrice));

            HotelAgent hotelAgent = getAgent(HotelAgent.class, hotel.getAgentName());
            if (hotelAgent != null) {
                HotelProviderRole hotelRole = hotelAgent.as(HotelProviderRole.class);
                if (hotelRole != null) {
                    sendMessage(MessageTypes.MSG_CFP, query, hotelRole.getIdentifier());
                }
            }
        }

        getLogger().info("[{}] CFP broadcast complete - waiting for {} responses",
            getOwner().getName(), pendingResponses.size());
    }

    // ==========================================
    // HOOK: Market Analytics for Proposals
    // ==========================================

    /**
     * After handling a proposal, update market analytics.
     * Tracks running average, min/max price, and detects price anomalies (>50% above average).
     */
    @AfterActionSpec("handleProposalMessage")
    private void afterHandleProposalMessage(ActionParams params) {
        double price = params.getDouble("proposalPrice");
        String hotelName = params.getString("hotelName");

        totalProposalCount++;
        proposalPriceSum += price;
        lowestProposalPrice = Math.min(lowestProposalPrice, price);
        highestProposalPrice = Math.max(highestProposalPrice, price);

        double avgPrice = proposalPriceSum / totalProposalCount;

        getLogger().info("[{}] Market analytics: {} proposals, avg=${}, range=[${}–${}]",
            getOwner().getName(), totalProposalCount,
            String.format("%.0f", avgPrice),
            String.format("%.0f", lowestProposalPrice),
            String.format("%.0f", highestProposalPrice));

        // Anomaly detection: price > 50% above average
        if (totalProposalCount > 1 && price > avgPrice * 1.5) {
            getLogger().warn("[{}] PRICE_ANOMALY: {} at ${} is {}% above average ${}",
                getOwner().getName(), hotelName, price,
                String.format("%.0f", ((price - avgPrice) / avgPrice) * 100),
                String.format("%.0f", avgPrice));
            ActivityLog.log(getOwner().getName(), hotelName, "PRICE_ANOMALY",
                String.format("$%.0f is %.0f%% above market average $%.0f",
                    price, ((price - avgPrice) / avgPrice) * 100, avgPrice));
        }
    }

    /**
     * Handle Proposal message from a hotel.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process incoming proposal from hotel")
    public void handleProposalMessage(Message<RoomProposal> message) {
        if (state != CustomerState.WAITING_PROPOSALS) {
            getLogger().warn("[{}] Received proposal in unexpected state: {}",
                getOwner().getName(), state);
            return;
        }

        RoomProposal proposal = message.getPayload();
        getLogger().info("[{}] Received proposal from {}: {} - ${}/night",
            getOwner().getName(), message.getSender(),
            proposal.getHotelName(), proposal.getPricePerNight());

        // Store proposal
        proposals.put(proposal.getProposalId(), proposal);
        pendingResponses.remove(message.getSender().getAgentName());

        // After-hook: market analytics
        ActionParams params = new ActionParams(new HashMap<>(), "handleProposalMessage");
        params.set("proposalPrice", proposal.getPricePerNight());
        params.set("hotelName", proposal.getHotelName());
        afterHandleProposalMessage(params);
        // Tick-driven: tickCheck() will handle deadline/evaluation in next tick
    }

    /**
     * Handle Refuse message from a hotel.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process refusal from hotel")
    public void handleRefuseMessage(Message<String> message) {
        getLogger().info("[{}] Received refusal from {}: {}",
            getOwner().getName(), message.getSender(), message.getPayload());

        if (state == CustomerState.RESERVING) {
            // Hotel refused during reservation (room no longer available)
            pendingReservationRefused = message.getPayload();
            return;
        }

        pendingResponses.remove(message.getSender().getAgentName());
        // Tick-driven: tickCheck() will handle deadline/evaluation in next tick
    }

    /**
     * Before evaluateProposals: prepare proposal summary as LLM parameters.
     * The framework's LLMRoleExecutor uses these parameters to build the prompt.
     */
    @BeforeActionSpec("evaluateProposals")
    private ActionParams beforeEvaluateProposals(ActionParams params) {
        StringBuilder proposalSummary = new StringBuilder();
        List<RoomProposal> proposalList = new ArrayList<>(proposals.values());
        for (int i = 0; i < proposalList.size(); i++) {
            RoomProposal p = proposalList.get(i);
            proposalSummary.append(String.format("%d. %s - %d stars - $%.0f/night\n",
                i + 1, p.getHotelName(), p.getRank(), p.getPricePerNight()));
        }
        params.set("customerCriteria", String.format("%s, %d+ stars, max $%.0f/night, desired $%.0f/night",
            desiredLocation, desiredRank, maxPrice, desiredPrice));
        params.set("proposals", proposalSummary.toString());
        return params;
    }

    /**
     * Evaluate collected proposals: shortlist top candidates for sequential negotiation.
     * Framework-orchestrated: LLMRoleExecutor calls LLM and passes ranking via ActionResult.
     * Fallback paths: (1) direct LLM ranking, (2) deterministic price-based sorting.
     *
     * @param llmResult the LLM response from framework (null when called directly from tick machine)
     */
    @ActionSpec(type = ActionType.LLM,
        description = "Evaluate and rank hotel proposals",
        llmTool = @LLMTool(
            systemPrompt = "You are evaluating hotel proposals for a customer. " +
                "Rank proposals from best to worst considering value-for-money, star rating, and price. " +
                "Return ONLY the ranking as comma-separated numbers (e.g., 2,1,3)."
        ))
    public void evaluateProposals(ActionResult llmResult) {
        state = CustomerState.EVALUATING;

        getLogger().info("[{}] Evaluating {} proposals", getOwner().getName(), proposals.size());

        if (proposals.isEmpty()) {
            getLogger().warn("[{}] No proposals received - reservation FAILED", getOwner().getName());
            ActivityLog.log(getOwner().getName(), "System", "FAIL",
                "No proposals received from any hotel — all hotels either refused or did not respond");
            state = CustomerState.FAILED;
            return;
        }

        // Rank proposals using available path
        List<RoomProposal> ranked;
        if (llmResult != null && !llmResult.isEmpty()) {
            // Path 1: Framework-orchestrated (ActionResult from LLMRoleExecutor)
            ranked = parseLLMRanking(llmResult.asString());
        } else if (getLLMClient() != null) {
            // Path 2: Direct call path (e.g., from tick machine)
            ranked = llmRankProposals();
        } else {
            // Path 3: Deterministic fallback (no LLM available)
            ranked = proposals.values().stream()
                .sorted(Comparator
                    .comparingDouble(RoomProposal::getPricePerNight)
                    .thenComparingLong(RoomProposal::getTimestamp))
                .collect(Collectors.toList());
        }

        topCandidates = ranked.stream()
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
        getLogger().info("[{}] Shortlisted {} candidates: {}", getOwner().getName(), topCandidates.size(), shortlistStr);
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
            getLogger().info("[{}] Best offer ${} <= desired ${} - accepting directly",
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
     * Parse LLM ranking response into ordered proposal list.
     * Expects comma-separated indices (e.g., "2,1,3").
     * Falls back to deterministic sort on parse failure.
     */
    private List<RoomProposal> parseLLMRanking(String response) {
        try {
            List<RoomProposal> proposalList = new ArrayList<>(proposals.values());
            List<RoomProposal> ranked = new ArrayList<>();
            String[] parts = response.replaceAll("[^0-9,]", "").split(",");
            Set<Integer> seen = new HashSet<>();
            for (String part : parts) {
                try {
                    int idx = Integer.parseInt(part.trim()) - 1;
                    if (idx >= 0 && idx < proposalList.size() && seen.add(idx)) {
                        ranked.add(proposalList.get(idx));
                    }
                } catch (NumberFormatException ignored) {}
            }
            // Add any proposals not mentioned by LLM
            for (RoomProposal p : proposalList) {
                if (!ranked.contains(p)) {
                    ranked.add(p);
                }
            }
            getLogger().info("[{}] LLM proposal ranking (framework): {}", getOwner().getName(), response);
            ActivityLog.log(getOwner().getName(), "System", "LLM_EVALUATE",
                "Used LLM (framework) to rank " + proposalList.size() + " proposals");
            return ranked;
        } catch (Exception e) {
            getLogger().warn("[{}] LLM ranking parse failed, falling back to price sort: {}",
                getOwner().getName(), e.getMessage());
            return proposals.values().stream()
                .sorted(Comparator
                    .comparingDouble(RoomProposal::getPricePerNight)
                    .thenComparingLong(RoomProposal::getTimestamp))
                .collect(Collectors.toList());
        }
    }

    /**
     * Use LLM to rank proposals based on subjective evaluation criteria:
     * price/quality ratio, location desirability, star rating, and overall value.
     * Falls back to price-based sorting on LLM failure.
     */
    private List<RoomProposal> llmRankProposals() {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are evaluating hotel proposals for a customer.\n");
            prompt.append(String.format("Customer criteria: %s, %d+ stars, max $%.0f/night, desired $%.0f/night.\n\n",
                desiredLocation, desiredRank, maxPrice, desiredPrice));
            prompt.append("Proposals:\n");

            List<RoomProposal> proposalList = new ArrayList<>(proposals.values());
            for (int i = 0; i < proposalList.size(); i++) {
                RoomProposal p = proposalList.get(i);
                prompt.append(String.format("%d. %s - %d stars - $%.0f/night\n",
                    i + 1, p.getHotelName(), p.getRank(), p.getPricePerNight()));
            }

            prompt.append("\nRank these proposals from best to worst considering value-for-money, ");
            prompt.append("star rating, and price. Return ONLY the ranking as comma-separated numbers (e.g., 2,1,3).");

            String response = getLLMClient().chat(prompt.toString()).getContent();
            getLogger().info("[{}] LLM proposal ranking: {}", getOwner().getName(), response);

            // Parse LLM response: extract comma-separated indices
            List<RoomProposal> ranked = new ArrayList<>();
            String[] parts = response.replaceAll("[^0-9,]", "").split(",");
            Set<Integer> seen = new HashSet<>();
            for (String part : parts) {
                try {
                    int idx = Integer.parseInt(part.trim()) - 1;
                    if (idx >= 0 && idx < proposalList.size() && seen.add(idx)) {
                        ranked.add(proposalList.get(idx));
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Add any proposals not mentioned by LLM
            for (RoomProposal p : proposalList) {
                if (!ranked.contains(p)) {
                    ranked.add(p);
                }
            }

            ActivityLog.log(getOwner().getName(), "System", "LLM_EVALUATE",
                "Used LLM to rank " + proposalList.size() + " proposals");
            return ranked;
        } catch (Exception e) {
            getLogger().warn("[{}] LLM evaluation failed, falling back to price sort: {}",
                getOwner().getName(), e.getMessage());
            return proposals.values().stream()
                .sorted(Comparator
                    .comparingDouble(RoomProposal::getPricePerNight)
                    .thenComparingLong(RoomProposal::getTimestamp))
                .collect(Collectors.toList());
        }
    }

    /**
     * Send REJECT to a proposal.
     */
    private void rejectProposal(RoomProposal proposal, String reason) {
        getLogger().info("[{}] Rejecting {} ({})", getOwner().getName(), proposal.getHotelName(), reason);
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
    @ActionSpec(type = ActionType.LOCAL, description = "Send acceptance to selected hotel", excludeFromLLM = true)
    public void makeReservation() {
        state = CustomerState.RESERVING;
        reservingTickCount = 0;

        ReservationRequest request = new ReservationRequest(
            getOwner().getName(),
            selectedProposal.getProposalId(),
            selectedProposal.getHotelId()
        );
        request.setCustomerName(getOwner().getName());
        request.setNumberOfNights(1);

        getLogger().info("[{}] Sending ACCEPT to {}", getOwner().getName(), selectedProposal.getHotelName());
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

    // ==========================================
    // HOOK: Negotiation Strategy Preparation
    // ==========================================

    /**
     * Before starting negotiation, calculate aggressiveness factor based on market data.
     * More candidates or price above market → more aggressive.
     * Fewer candidates or price at/below market → more conservative.
     */
    @BeforeActionSpec("startNegotiationWithCandidate")
    private ActionParams beforeStartNegotiation(ActionParams params) {
        double marketAvg = totalProposalCount > 0 ? proposalPriceSum / totalProposalCount : maxPrice;
        double candidatePrice = params.getDouble("candidatePrice");
        int candidateCount = params.getInt("candidateCount");

        // Aggressiveness factor: 0.0 (conservative) to 1.0 (aggressive)
        double aggressiveness = 0.5; // baseline

        // More candidates → more aggressive (leverage)
        if (candidateCount >= 3) {
            aggressiveness += 0.15;
        } else if (candidateCount == 1) {
            aggressiveness -= 0.20;
        }

        // Price above market average → more aggressive
        if (candidatePrice > marketAvg * 1.1) {
            aggressiveness += 0.15;
        } else if (candidatePrice < marketAvg * 0.9) {
            aggressiveness -= 0.10;
        }

        // Clamp to [0.1, 0.9]
        aggressiveness = Math.max(0.1, Math.min(0.9, aggressiveness));

        // Calculate initial offer based on aggressiveness
        // High aggressiveness → offer closer to desiredPrice
        // Low aggressiveness → offer closer to candidatePrice
        double offerPrice = desiredPrice + (candidatePrice - desiredPrice) * (1.0 - aggressiveness);
        offerPrice = Math.round(offerPrice * 100.0) / 100.0;

        params.set("aggressiveness", aggressiveness);
        params.set("offerPrice", offerPrice);
        params.set("marketAvg", marketAvg);

        String strategyLabel = aggressiveness >= 0.7 ? "AGGRESSIVE" :
                               aggressiveness >= 0.4 ? "MODERATE" : "CONSERVATIVE";
        params.set("strategyLabel", strategyLabel);

        getLogger().info("[{}] STRATEGY: {} (aggressiveness={}), marketAvg=${}, offer=${}",
            getOwner().getName(), strategyLabel,
            String.format("%.2f", aggressiveness),
            String.format("%.0f", marketAvg), offerPrice);
        ActivityLog.log(getOwner().getName(), "System", "STRATEGY",
            String.format("Strategy: %s (%.0f%% aggressive) — Market avg $%.0f, opening at $%.0f",
                strategyLabel, aggressiveness * 100, marketAvg, offerPrice));

        return params;
    }

    /**
     * Start price negotiation with the candidate at given index.
     * Uses strategy from @BeforeAction hook for initial offer calculation.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Start price negotiation with candidate hotel", excludeFromLLM = true)
    public void startNegotiationWithCandidate(int candidateIndex) {
        if (candidateIndex >= topCandidates.size()) {
            getLogger().warn("[{}] All {} candidate hotels exhausted — FAILED",
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

        // Before-hook: strategy preparation
        ActionParams strategyParams = new ActionParams(new HashMap<>(), "startNegotiationWithCandidate");
        strategyParams.set("candidatePrice", proposal.getPricePerNight());
        strategyParams.set("candidateCount", topCandidates.size() - candidateIndex);
        strategyParams = beforeStartNegotiation(strategyParams);

        // First offer: from strategy hook (replaces fixed desiredPrice)
        double offerPrice = strategyParams.getDouble("offerPrice");

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

        getLogger().info("");
        getLogger().info("┌─ NEGOTIATION START ──────────────────────────────────┐");
        getLogger().info("│  Customer: {}  [candidate {}/{}]                    │",
            getOwner().getName(), candidateIndex + 1, topCandidates.size());
        getLogger().info("│  Hotel: {} (${}/night)                              │", proposal.getHotelName(), proposal.getPricePerNight());
        getLogger().info("│  Our offer: ${}/night                               │", offerPrice);
        if (competing != null) {
            getLogger().info("│  Leverage: {} at ${}/night                          │", competing.getHotelName(), competing.getPricePerNight());
        }
        getLogger().info("│  Round: {}/{}                                        │", negotiationRound, maxNegotiationRounds);
        getLogger().info("└─────────────────────────────────────────────────────┘");

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
    @ActionSpec(type = ActionType.LOCAL, description = "Process hotel's counter-offer during negotiation")
    public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
        // Tick-driven: just store, tickCheck() will process in next tick
        NegotiationOffer hotelOffer = message.getPayload();
        negotiationHistory.add(hotelOffer);
        pendingCounterOffer = hotelOffer;
    }

    /**
     * Handle NegotiateAccept from hotel - hotel accepted our offer.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process hotel's acceptance of negotiation offer")
    public void handleNegotiateAcceptMessage(Message<NegotiationOffer> message) {
        // Tick-driven: just store, tickCheck() will process in next tick
        NegotiationOffer acceptance = message.getPayload();
        negotiationHistory.add(acceptance);
        pendingNegAccept = acceptance;
    }

    /**
     * Handle NegotiateReject from hotel - hotel rejected negotiation entirely.
     * Tries to accept at listed price, or falls back to next candidate.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process hotel's rejection of negotiation")
    public void handleNegotiateRejectMessage(Message<String> message) {
        // Tick-driven: just store, tickCheck() will process in next tick
        pendingNegReject = message.getPayload();
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
            getLogger().info("[{}] Moving to next candidate [{}/{}]: {}",
                getOwner().getName(), nextIdx + 1, topCandidates.size(),
                topCandidates.get(nextIdx).getHotelName());
            ActivityLog.log(getOwner().getName(), topCandidates.get(nextIdx).getHotelName(), "FALLBACK",
                String.format("Switching from %s to next candidate [%d/%d]",
                    currentHotel, nextIdx + 1, topCandidates.size()));
            startNegotiationWithCandidate(nextIdx);
        } else {
            getLogger().warn("[{}] All {} candidates exhausted — FAILED", getOwner().getName(), topCandidates.size());
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
        getLogger().info("");
        getLogger().info("┌─ NEGOTIATION COMPLETE ───────────────────────────────┐");
        getLogger().info("│  Hotel: {}                                           │", negotiatingWith.getHotelName());
        getLogger().info("│  Original: ${}/night                                │", negotiatingWith.getPricePerNight());
        getLogger().info("│  Agreed: ${}/night                                  │", agreedPrice);
        getLogger().info("│  Savings: ${}/night ({}% off)                       │",
            negotiatingWith.getPricePerNight() - agreedPrice,
            String.format("%.1f", ((negotiatingWith.getPricePerNight() - agreedPrice) / negotiatingWith.getPricePerNight()) * 100));
        getLogger().info("│  Rounds: {}  [candidate {}/{}]                      │",
            negotiationRound, currentCandidateIndex + 1, topCandidates.size());
        getLogger().info("└─────────────────────────────────────────────────────┘");

        // Update proposal with negotiated price
        negotiatingWith.setNegotiatedPrice(agreedPrice);
        negotiatingWith.setNegotiated(true);

        // Reject remaining candidates that we didn't negotiate with
        rejectRemainingCandidates(currentCandidateIndex);

        // Proceed to reservation
        state = CustomerState.RESERVING;
        reservingTickCount = 0;
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

        getLogger().info("[{}] Sending NegotiateAccept to {} at ${}/night",
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

    // ==========================================
    // HOOK: Reservation Audit Trail
    // ==========================================

    /**
     * After receiving confirmation, create an audit trail.
     * Calculates savings, market signal (BELOW/ABOVE/AT_MARKET).
     */
    @AfterActionSpec("handleConfirmMessage")
    private void afterHandleConfirmMessage(ActionParams params) {
        double finalPrice = params.getDouble("finalPrice");
        double originalPrice = params.getDouble("originalPrice");
        double marketAvg = totalProposalCount > 0 ? proposalPriceSum / totalProposalCount : finalPrice;

        // Calculate savings
        double savings = originalPrice > 0 ? originalPrice - finalPrice : 0;
        double savingsPercent = originalPrice > 0 ? (savings / originalPrice) * 100 : 0;

        // Market signal
        String marketSignal;
        if (finalPrice < marketAvg * 0.95) {
            marketSignal = "BELOW_MARKET";
        } else if (finalPrice > marketAvg * 1.05) {
            marketSignal = "ABOVE_MARKET";
        } else {
            marketSignal = "AT_MARKET";
        }

        getLogger().info("[{}] AUDIT: finalPrice=${}, originalPrice=${}, savings=${} ({}%), signal={}",
            getOwner().getName(), finalPrice, originalPrice, savings,
            String.format("%.1f", savingsPercent), marketSignal);
        ActivityLog.log(getOwner().getName(), params.getString("hotelName"), "AUDIT",
            String.format("Final $%.0f (was $%.0f), saved $%.0f (%.1f%%) — %s (avg $%.0f)",
                finalPrice, originalPrice, savings, savingsPercent, marketSignal, marketAvg));
    }

    /**
     * Handle Confirmation message from hotel.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process reservation confirmation from hotel")
    public void handleConfirmMessage(Message<ReservationConfirmation> message) {
        // Tick-driven: just store, tickCheck() will process in next tick
        pendingConfirm = message.getPayload();
    }

    /**
     * Tick-driven state machine — called by CustomerAgent.makeStep() each tick.
     * Advances ONE protocol step per tick so the simulation progresses visibly.
     *
     * SCOP's Mailbox.add() delivers messages synchronously (messageArrived() is
     * called within the sender's sendMessage). Message handlers only STORE data;
     * this method is the sole driver of state transitions.
     */
    public void tickCheck() {
        switch (state) {
            case WAITING_PROPOSALS: {
                boolean allResponded = pendingResponses.isEmpty();
                boolean deadlinePassed = System.currentTimeMillis() - searchStartTime > PROPOSAL_DEADLINE_MS;
                if (allResponded || deadlinePassed) {
                    if (deadlinePassed) {
                        getLogger().info("[{}] Proposal deadline reached", getOwner().getName());
                    }
                    // Just transition — evaluation happens next tick
                    state = CustomerState.EVALUATING;
                }
                break;
            }
            case EVALUATING:
                evaluateProposals(null);
                break;
            case NEGOTIATING:
                processNegotiationTick();
                break;
            case RESERVING:
                processReservingTick();
                break;
            default:
                break;
        }
    }

    /**
     * Process one negotiation step per tick.
     * Checks for pending hotel responses in priority order.
     */
    private void processNegotiationTick() {
        if (pendingNegAccept != null) {
            NegotiationOffer acceptance = pendingNegAccept;
            pendingNegAccept = null;
            getLogger().info("[{}] Hotel {} accepted negotiation at ${}/night!",
                getOwner().getName(), acceptance.getHotelName(), acceptance.getOfferedPrice());
            acceptNegotiation(acceptance.getOfferedPrice());
            return;
        }
        if (pendingNegReject != null) {
            String reason = pendingNegReject;
            pendingNegReject = null;
            getLogger().info("[{}] Hotel rejected negotiation: {}", getOwner().getName(), reason);
            if (negotiatingWith != null && negotiatingWith.getPricePerNight() <= maxPrice) {
                getLogger().info("[{}] Accepting original price ${}", getOwner().getName(), negotiatingWith.getPricePerNight());
                negotiatingWith = null;
                negotiationRound = 0;
                makeReservation();
            } else {
                rejectAndTryNext("Negotiation rejected by hotel");
            }
            return;
        }
        if (pendingCounterOffer != null) {
            NegotiationOffer hotelOffer = pendingCounterOffer;
            pendingCounterOffer = null;
            processCounterOffer(hotelOffer);
        }
    }

    /**
     * Process a counter-offer from the hotel.
     * Decides: accept, counter-counter, or give up and try next candidate.
     */
    private void processCounterOffer(NegotiationOffer hotelOffer) {
        getLogger().info("[{}] Processing counter-offer from {}: ${}/night (round {}/{})",
            getOwner().getName(), hotelOffer.getHotelName(),
            hotelOffer.getOfferedPrice(), hotelOffer.getRound(), hotelOffer.getMaxRounds());

        if (hotelOffer.getOfferedPrice() <= desiredPrice) {
            getLogger().info("[{}] Hotel offer ${} <= desired ${} - accepting!",
                getOwner().getName(), hotelOffer.getOfferedPrice(), desiredPrice);
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        negotiationRound = hotelOffer.getRound() + 1;

        if (negotiationRound > maxNegotiationRounds) {
            if (hotelOffer.getOfferedPrice() <= maxPrice) {
                getLogger().info("[{}] Max rounds reached. Accepting last offer: ${}",
                    getOwner().getName(), hotelOffer.getOfferedPrice());
                acceptNegotiation(hotelOffer.getOfferedPrice());
            } else {
                getLogger().info("[{}] Max rounds reached, offer ${} > max ${}. Trying next candidate.",
                    getOwner().getName(), hotelOffer.getOfferedPrice(), maxPrice);
                rejectAndTryNext("Price too high after maximum negotiation rounds");
            }
            return;
        }

        double counterPrice = pricingStrategy.counterOffer(desiredPrice, maxPrice, negotiationRound, maxNegotiationRounds);

        if (counterPrice >= hotelOffer.getOfferedPrice()) {
            getLogger().info("[{}] Our counter ${} >= hotel offer ${} - accepting hotel offer",
                getOwner().getName(), counterPrice, hotelOffer.getOfferedPrice());
            acceptNegotiation(hotelOffer.getOfferedPrice());
            return;
        }

        RoomProposal competing = getCompetingCandidate();
        String leverageMsg = "";
        if (competing != null && competing.getPricePerNight() < hotelOffer.getOfferedPrice()) {
            leverageMsg = String.format(" We have a competing offer at $%.0f/night.", competing.getPricePerNight());
        }

        getLogger().info("[{}] Counter-offering ${} (round {}/{})",
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
     * Process pending confirmation in RESERVING state.
     * Handles: confirm received, refuse received (room gone), or timeout.
     */
    private void processReservingTick() {
        reservingTickCount++;

        if (pendingConfirm != null) {
            confirmation = pendingConfirm;
            pendingConfirm = null;
            state = CustomerState.COMPLETED;

            getLogger().info("========================================");
            getLogger().info("[{}] RESERVATION CONFIRMED!", getOwner().getName());
            getLogger().info("  Confirmation #: {}", confirmation.getConfirmationNumber());
            getLogger().info("  Hotel: {}", confirmation.getHotelName());
            getLogger().info("  Price: ${}/night", confirmation.getPricePerNight());
            getLogger().info("  Total: ${}", confirmation.getTotalPrice());
            getLogger().info("  Status: {}", confirmation.getStatus());
            getLogger().info("========================================");

            // After-hook: audit trail
            ActionParams auditParams = new ActionParams(new HashMap<>(), "handleConfirmMessage");
            auditParams.set("finalPrice", confirmation.getPricePerNight());
            auditParams.set("originalPrice", confirmation.getOriginalPrice() > 0
                ? confirmation.getOriginalPrice() : confirmation.getPricePerNight());
            auditParams.set("hotelName", confirmation.getHotelName());
            afterHandleConfirmMessage(auditParams);
            return;
        }

        if (pendingReservationRefused != null) {
            String reason = pendingReservationRefused;
            pendingReservationRefused = null;
            String hotel = selectedProposal != null ? selectedProposal.getHotelName() : "unknown";

            getLogger().warn("[{}] Reservation REFUSED by {}: {}", getOwner().getName(), hotel, reason);
            ActivityLog.log(getOwner().getName(), hotel, "RESERVATION_REFUSED", reason);

            state = CustomerState.FAILED;
            return;
        }

        if (reservingTickCount >= RESERVING_TIMEOUT_TICKS) {
            String hotel = selectedProposal != null ? selectedProposal.getHotelName() : "unknown";
            getLogger().warn("[{}] Reservation TIMEOUT after {} ticks waiting for {} confirmation",
                getOwner().getName(), reservingTickCount, hotel);
            ActivityLog.log(getOwner().getName(), hotel, "RESERVATION_TIMEOUT",
                String.format("No confirmation received after %d ticks", reservingTickCount));

            state = CustomerState.FAILED;
        }
    }

    // ==========================================
    // LLM Helper: Ollama REST API
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
