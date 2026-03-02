package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Identifier;
import ai.scop.core.Role;
import ai.scop.core.messaging.Message;
import com.tnsai.actions.ActionParams;
import com.tnsai.annotations.*;
import com.tnsai.annotations.LLMSpec.Provider;
import com.tnsai.enums.ActionType;
import hotel.reservation.ActivityLog;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DFEntry;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.config.EnvConfig;
import hotel.reservation.message.*;
import hotel.reservation.role.pricing.SellerPricingStrategy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hotel Provider Role - Implements the CNP participant behavior.
 * Handles CFP messages and responds with proposals or refusals.
 */
@RoleSpec(
    description = "Hotel service provider that responds to reservation requests",
    responsibilities = {
        @Responsibility(
            name = "ProposalGeneration",
            description = "Evaluate customer requests and generate competitive proposals",
            actions = {"handleCFPMessage", "sendProposal"}
        ),
        @Responsibility(
            name = "ReservationManagement",
            description = "Process reservation acceptances and confirmations",
            actions = {"handleAcceptMessage", "handleRejectMessage"}
        ),
        @Responsibility(
            name = "Negotiation",
            description = "Handle price negotiations with customers",
            actions = {"handleNegotiateStartMessage", "handleCounterOfferMessage", "handleNegotiateAcceptMessage"}
        ),
        @Responsibility(
            name = "Registration",
            description = "Register hotel with Directory Facilitator after validation",
            actions = {"registerWithDF"}
        )
    },
    llm = @LLMSpec(
        provider = Provider.OLLAMA,
        model = "minimax-m2.1:cloud",
        temperature = 0.5f
    ),
    communication = @Communication(
        style = @Communication.Style(
            tone = Communication.Tone.PROFESSIONAL,
            verbosity = Communication.Verbosity.CONCISE
        ),
        languages = {"tr", "en"}
    )
)
public class HotelProviderRole extends Role {

    @State(description = "Unique hotel identifier")
    private final String hotelId;

    @State(description = "Hotel display name")
    private final String hotelName;

    @State(description = "Hotel location/city")
    private final String location;

    @State(description = "Hotel star rating (1-5)")
    private final int rank;

    @State(description = "Base price per night in USD")
    private final double basePrice;

    // Simulated behavior parameters
    private final Random random = new Random();

    @State(description = "Probability of responding to requests (0.0-1.0)")
    private double responseRate = 1.0;  // 100% for deterministic testing

    @State(description = "Whether hotel has available rooms")
    private boolean available = true;

    // Negotiation parameters
    @State(description = "Base minimum acceptable price (basePrice * 0.85)")
    private final double baseMinPrice;

    @State(description = "How flexible the hotel is in negotiation (0.0-1.0)")
    private double negotiationFlexibility;

    private final SellerPricingStrategy pricingStrategy;

    private final Map<String, Integer> currentNegotiations = new ConcurrentHashMap<>();

    public HotelProviderRole(Agent owner, String envName,
                             String hotelId, String hotelName, String location,
                             int rank, double basePrice,
                             SellerPricingStrategy pricingStrategy) {
        super(owner, envName);
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.basePrice = basePrice;
        this.baseMinPrice = basePrice * 0.85;
        this.negotiationFlexibility = 0.3 + random.nextDouble() * 0.5; // 0.3 - 0.8
        this.pricingStrategy = pricingStrategy;
    }

    /**
     * Calculate effective minimum acceptable price based on room scarcity.
     * When rooms are scarce, the hotel demands higher prices.
     *
     * Occupancy 0% → baseMinPrice (full discount available)
     * Occupancy 50% → baseMinPrice + 25% of gap to basePrice
     * Occupancy 100% (1 room left) → basePrice (no discount)
     */
    private double getEffectiveMinPrice() {
        HotelAgent ha = (HotelAgent) getOwner();
        int total = ha.getTotalRooms();
        int available = ha.getAvailableRooms();
        if (total <= 0 || available <= 0) return basePrice;

        double occupancyRate = 1.0 - ((double) available / total);
        // Scarcity premium: occupancy² for non-linear increase
        double scarcityFactor = occupancyRate * occupancyRate;
        return baseMinPrice + (basePrice - baseMinPrice) * scarcityFactor;
    }

    /**
     * Handle CFP (Call For Proposals) message.
     * Decides whether to respond with a proposal or refuse.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process incoming CFP and decide whether to make a proposal")
    public void handleCFPMessage(Message<RoomQuery> message) {
        RoomQuery query = message.getPayload();
        getLogger().info("[{}] Received CFP from {}: {}",
            getOwner().getName(), message.getSender(), query);

        // Check if hotel matches the query criteria
        if (!matchesQuery(query)) {
            getLogger().info("[{}] Query does not match hotel criteria - sending refusal",
                getOwner().getName());
            sendRefusal(message.getSender(), "Does not match criteria");
            return;
        }

        // Check room availability
        HotelAgent hotelAgent = (HotelAgent) getOwner();
        if (hotelAgent.getAvailableRooms() <= 0) {
            getLogger().info("[{}] No rooms available - sending refusal",
                getOwner().getName());
            sendRefusal(message.getSender(), "No rooms available");
            return;
        }

        // Simulate random decision to respond (availability simulation)
        if (!shouldRespond()) {
            getLogger().info("[{}] Decided not to respond to CFP (simulating unavailability)",
                getOwner().getName());
            sendRefusal(message.getSender(), "Hotel currently unavailable");
            return;
        }

        // Create and send proposal
        sendProposal(message.getSender());
    }

    /**
     * Check if this hotel matches the query criteria.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Verify if hotel matches customer search criteria", excludeFromLLM = true)
    public boolean matchesQuery(RoomQuery query) {
        // Check location
        if (query.getLocation() != null &&
            !query.getLocation().equalsIgnoreCase(location)) {
            return false;
        }

        // Check rank
        if (rank < query.getMinRank()) {
            return false;
        }

        // Check price
        if (basePrice > query.getMaxPrice()) {
            return false;
        }

        return available;
    }

    /**
     * Simulate decision to respond (for realistic behavior).
     */
    private boolean shouldRespond() {
        return random.nextDouble() < responseRate;
    }

    // ==========================================
    // LLM: Pricing Strategy Decision
    // ==========================================

    /**
     * Use LLM to decide the pricing strategy for a customer query.
     * Considers occupancy, demand level, customer criteria, and competitive positioning.
     * Fallback: returns the deterministic multiplier from occupancy-based calculation.
     *
     * @param query the customer's room query
     * @return price multiplier to apply to base price (e.g., 0.95 for discount, 1.15 for premium)
     */
    @ActionSpec(type = ActionType.LLM, description = "Decide pricing strategy based on market conditions and customer profile")
    public double decidePricingStrategy(RoomQuery query) {
        HotelAgent ha = (HotelAgent) getOwner();
        int total = ha.getTotalRooms();
        int avail = ha.getAvailableRooms();
        double occupancyRate = total > 0 ? 1.0 - ((double) avail / total) : 0.0;

        if (EnvConfig.llmEnabled()) {
            try {
                String prompt = String.format(
                    "You are a hotel pricing strategist for %s (%d-star, base $%.0f/night in %s).\n" +
                    "Current occupancy: %.0f%% (%d/%d rooms occupied).\n" +
                    "Customer wants: %s, %d+ stars, max $%.0f/night.\n\n" +
                    "Decide a price multiplier (0.85 to 1.30) to apply to the base price.\n" +
                    "Consider: demand level, customer's budget, competitive positioning.\n" +
                    "Return ONLY a decimal number (e.g., 1.05).",
                    hotelName, rank, basePrice, location,
                    occupancyRate * 100, total - avail, total,
                    query.getLocation(), query.getMinRank(), query.getMaxPrice()
                );

                String response = callOllama(prompt);
                double multiplier = Double.parseDouble(response.replaceAll("[^0-9.]", ""));
                // Clamp to safe range
                multiplier = Math.max(0.85, Math.min(1.30, multiplier));

                getLogger().info("[{}] LLM pricing strategy: multiplier={} for query from {}",
                    getOwner().getName(), String.format("%.2f", multiplier), query.getLocation());
                ActivityLog.log(hotelName, "System", "LLM_PRICING",
                    String.format("LLM decided multiplier %.2f (occupancy %.0f%%)", multiplier, occupancyRate * 100));
                return multiplier;
            } catch (Exception e) {
                getLogger().warn("[{}] LLM pricing failed, using deterministic fallback: {}",
                    getOwner().getName(), e.getMessage());
            }
        }

        // Deterministic fallback
        if (occupancyRate < 0.3) return 0.95;
        if (occupancyRate < 0.7) return 1.0 + (occupancyRate - 0.3) * 0.25;
        return 1.10 + (occupancyRate - 0.7) * 0.50;
    }

    // ==========================================
    // HOOK: Dynamic Pricing for sendProposal
    // ==========================================

    /**
     * Before sending a proposal, adjust price based on occupancy (dynamic pricing).
     * Low demand (occupancy < 30%): discount 5%
     * Normal demand (30-70%): base price or slight increase
     * High demand (>70%): premium 10-25%
     */
    @BeforeActionSpec("sendProposal")
    private ActionParams beforeSendProposal(ActionParams params) {
        HotelAgent ha = (HotelAgent) getOwner();
        int total = ha.getTotalRooms();
        int avail = ha.getAvailableRooms();
        double occupancyRate = total > 0 ? 1.0 - ((double) avail / total) : 0.0;

        double multiplier;
        String demandLevel;
        if (occupancyRate < 0.3) {
            multiplier = 0.95; // Low demand discount
            demandLevel = "LOW";
        } else if (occupancyRate < 0.7) {
            multiplier = 1.0 + (occupancyRate - 0.3) * 0.25; // 1.0 to 1.10
            demandLevel = "NORMAL";
        } else {
            multiplier = 1.10 + (occupancyRate - 0.7) * 0.50; // 1.10 to 1.25
            demandLevel = "HIGH";
        }

        double dynamicPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;
        params.set("dynamicPrice", dynamicPrice);
        params.set("demandLevel", demandLevel);
        params.set("occupancyRate", occupancyRate);

        getLogger().info("[{}] Dynamic pricing: occupancy={}%, demand={}, multiplier={}, base=${} → dynamic=${}",
            getOwner().getName(),
            String.format("%.0f", occupancyRate * 100), demandLevel,
            String.format("%.2f", multiplier), basePrice, dynamicPrice);

        return params;
    }

    /**
     * After sending a proposal, log the sent proposal details to ActivityLog.
     */
    @AfterActionSpec("sendProposal")
    private void afterSendProposal(ActionParams params) {
        double dynamicPrice = params.getDouble("dynamicPrice");
        String demandLevel = params.getString("demandLevel");
        String customerName = params.getString("customerName");

        ActivityLog.log(hotelName, customerName, "DYNAMIC_PRICING",
            String.format("Demand: %s — Base $%.0f → Offered $%.0f", demandLevel, basePrice, dynamicPrice));
    }

    /**
     * Send a proposal to the customer.
     * Uses dynamic pricing based on occupancy via @BeforeAction hook.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Generate and send a room proposal to customer", excludeFromLLM = true)
    public void sendProposal(Identifier customer) {
        // Create ActionParams and invoke before-hook
        ActionParams params = new ActionParams(new HashMap<>(), "sendProposal");
        params.set("customerName", customer.getAgentName());
        params = beforeSendProposal(params);

        double dynamicPrice = params.getDouble("dynamicPrice");

        RoomProposal proposal = new RoomProposal(
            hotelId,
            hotelName,
            location,
            rank,
            dynamicPrice,
            "standard"  // Default room type
        );
        proposal.setAmenities(List.of("wifi", "breakfast"));
        proposal.setRating(4.5);

        getLogger().info("[{}] Sending proposal to {}: {} - ${}/night",
            getOwner().getName(), customer, hotelName, dynamicPrice);
        ActivityLog.log(hotelName, customer.getAgentName(), "PROPOSAL",
            String.format("%s - $%.0f/night, %d★", hotelName, dynamicPrice, rank));

        // Use Role's sendMessage method
        sendMessage(MessageTypes.MSG_PROPOSAL, proposal, customer);

        // Invoke after-hook
        afterSendProposal(params);
    }

    /**
     * Send a refusal to the customer.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Send refusal message to customer", excludeFromLLM = true)
    public void sendRefusal(Identifier customer, String reason) {
        getLogger().info("[{}] Sending refusal to {}: {}",
            getOwner().getName(), customer, reason);
        ActivityLog.log(hotelName, customer.getAgentName(), "REFUSE", reason);

        sendMessage(MessageTypes.MSG_REFUSE, reason, customer);
    }

    /**
     * Handle Accept message - Customer accepted our proposal.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process reservation acceptance and send confirmation")
    public void handleAcceptMessage(Message<ReservationRequest> message) {
        ReservationRequest request = message.getPayload();
        getLogger().info("[{}] Received ACCEPT from {}: {}",
            getOwner().getName(), message.getSender(), request);

        // Try to reserve a room
        HotelAgent hotelAgent = (HotelAgent) getOwner();
        if (!hotelAgent.reserveRoom()) {
            getLogger().info("[{}] Room no longer available - sending refusal",
                getOwner().getName());
            sendRefusal(message.getSender(), "Room no longer available");
            return;
        }

        // Process the reservation
        ReservationConfirmation confirmation = new ReservationConfirmation(
            request.getRequestId(),
            request.getCustomerId(),
            hotelId,
            hotelName,
            basePrice,
            request.getNumberOfNights()
        );
        confirmation.setRoomType("standard");
        confirmation.setCheckInDate(request.getCheckInDate());
        confirmation.setCheckOutDate(request.getCheckOutDate());

        getLogger().info("[{}] Reservation CONFIRMED: {} - Total: ${}",
            getOwner().getName(),
            confirmation.getConfirmationNumber(),
            confirmation.getTotalPrice());
        ActivityLog.log(hotelName, message.getSender().getAgentName(), "CONFIRM",
            String.format("Confirmed! #%s - $%.0f total", confirmation.getConfirmationNumber(), confirmation.getTotalPrice()));

        // Send confirmation using Role's sendMessage
        sendMessage(MessageTypes.MSG_CONFIRM, confirmation, message.getSender());

        // Mark as temporarily unavailable (optional)
        // this.available = false;
    }

    /**
     * Handle Reject message - Customer rejected our proposal.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process proposal rejection from customer")
    public void handleRejectMessage(Message<String> message) {
        getLogger().info("[{}] Proposal REJECTED by {}: {}",
            getOwner().getName(), message.getSender(), message.getPayload());
        // Clean up if needed
    }

    // ==========================================
    // NEGOTIATION HANDLERS
    // ==========================================

    /**
     * Handle NegotiateStart message - Customer initiates price negotiation.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process customer's negotiation start request")
    public void handleNegotiateStartMessage(Message<NegotiationOffer> message) {
        NegotiationOffer offer = message.getPayload();
        getLogger().info("[{}] Received NegotiateStart from {}: offered ${} for {} (original ${})",
            getOwner().getName(), message.getSender(), offer.getOfferedPrice(),
            hotelName, offer.getOriginalPrice());

        currentNegotiations.put(offer.getProposalId(), 1);

        double effectiveMin = getEffectiveMinPrice();
        HotelAgent ha = (HotelAgent) getOwner();
        if (effectiveMin > baseMinPrice) {
            getLogger().info("[{}] Demand pressure: {}/{} rooms occupied → min price raised from ${} to ${}",
                getOwner().getName(), ha.getTotalRooms() - ha.getAvailableRooms(), ha.getTotalRooms(),
                String.format("%.0f", baseMinPrice), String.format("%.0f", effectiveMin));
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "DEMAND_PRESSURE",
                String.format("High demand: %d/%d rooms occupied — minimum price raised to $%.0f (was $%.0f)",
                    ha.getTotalRooms() - ha.getAvailableRooms(), ha.getTotalRooms(), effectiveMin, baseMinPrice));
        }

        if (offer.getOfferedPrice() >= effectiveMin) {
            // Customer's offer is acceptable - accept negotiation
            getLogger().info("[{}] Customer offer ${} is acceptable (min: ${}). Accepting negotiation.",
                getOwner().getName(), offer.getOfferedPrice(), effectiveMin);
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "NEGOTIATE_ACCEPT",
                String.format("Accepted offer $%.0f/night", offer.getOfferedPrice()));

            NegotiationOffer acceptance = new NegotiationOffer(
                offer.getProposalId(), hotelId, hotelName,
                offer.getOfferedPrice(), offer.getOriginalPrice(),
                1, offer.getMaxRounds(),
                String.format("We accept your offer of $%.2f per night.", offer.getOfferedPrice())
            );
            sendMessage(MessageTypes.MSG_NEGOTIATE_ACCEPT, acceptance, message.getSender());
            currentNegotiations.remove(offer.getProposalId());
        } else {
            // Counter-offer: start from basePrice and reduce based on flexibility
            double counterPrice = calculateHotelCounterOffer(1, offer.getMaxRounds());
            getLogger().info("[{}] Counter-offering ${} (customer offered ${})",
                getOwner().getName(), counterPrice, offer.getOfferedPrice());
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "COUNTER_OFFER",
                String.format("Counter: $%.0f/night (round 1)", counterPrice));

            NegotiationOffer counter = new NegotiationOffer(
                offer.getProposalId(), hotelId, hotelName,
                counterPrice, offer.getOriginalPrice(),
                1, offer.getMaxRounds(),
                String.format("We can offer $%.2f per night for %s.", counterPrice, hotelName)
            );
            sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, message.getSender());
        }
    }

    /**
     * Handle CounterOffer message - Customer sends a counter-offer.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process customer's counter-offer during negotiation")
    public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
        NegotiationOffer offer = message.getPayload();
        int round = offer.getRound();
        getLogger().info("[{}] Received CounterOffer from {}: ${} (round {}/{})",
            getOwner().getName(), message.getSender(), offer.getOfferedPrice(),
            round, offer.getMaxRounds());

        currentNegotiations.put(offer.getProposalId(), round);

        double effectiveMin = getEffectiveMinPrice();

        if (offer.getOfferedPrice() >= effectiveMin) {
            // Accept the counter-offer
            getLogger().info("[{}] Accepting counter-offer of ${}", getOwner().getName(), offer.getOfferedPrice());
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "NEGOTIATE_ACCEPT",
                String.format("Accepted $%.0f/night (round %d)", offer.getOfferedPrice(), round));

            NegotiationOffer acceptance = new NegotiationOffer(
                offer.getProposalId(), hotelId, hotelName,
                offer.getOfferedPrice(), offer.getOriginalPrice(),
                round, offer.getMaxRounds(),
                String.format("Deal! We accept $%.2f per night.", offer.getOfferedPrice())
            );
            sendMessage(MessageTypes.MSG_NEGOTIATE_ACCEPT, acceptance, message.getSender());
            currentNegotiations.remove(offer.getProposalId());
        } else if (round >= offer.getMaxRounds()) {
            // Last round - send final offer or reject
            double finalPrice = effectiveMin;
            getLogger().info("[{}] Final round. Sending last offer: ${}", getOwner().getName(), finalPrice);
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "COUNTER_OFFER",
                String.format("Final offer: $%.0f/night", finalPrice));

            NegotiationOffer finalOffer = new NegotiationOffer(
                offer.getProposalId(), hotelId, hotelName,
                finalPrice, offer.getOriginalPrice(),
                round, offer.getMaxRounds(),
                String.format("Our final offer: $%.2f per night. This is the best we can do.", finalPrice)
            );
            sendMessage(MessageTypes.MSG_COUNTER_OFFER, finalOffer, message.getSender());
            currentNegotiations.remove(offer.getProposalId());
        } else {
            // Counter with a lower price
            double counterPrice = calculateHotelCounterOffer(round, offer.getMaxRounds());
            getLogger().info("[{}] Counter-offering ${} in round {}", getOwner().getName(), counterPrice, round);
            ActivityLog.log(hotelName, message.getSender().getAgentName(), "COUNTER_OFFER",
                String.format("Counter: $%.0f/night (round %d)", counterPrice, round));

            NegotiationOffer counter = new NegotiationOffer(
                offer.getProposalId(), hotelId, hotelName,
                counterPrice, offer.getOriginalPrice(),
                round, offer.getMaxRounds(),
                String.format("How about $%.2f per night?", counterPrice)
            );
            sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, message.getSender());
        }
    }

    /**
     * Handle NegotiateAccept message - Customer accepted a negotiated price.
     * Creates reservation confirmation with the negotiated price.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Process negotiation acceptance and confirm reservation")
    public void handleNegotiateAcceptMessage(Message<ReservationRequest> message) {
        ReservationRequest request = message.getPayload();
        getLogger().info("[{}] Received NegotiateAccept from {}: {}",
            getOwner().getName(), message.getSender(), request);

        // Try to reserve a room
        HotelAgent hotelAgent = (HotelAgent) getOwner();
        if (!hotelAgent.reserveRoom()) {
            getLogger().info("[{}] Room no longer available - sending refusal",
                getOwner().getName());
            sendRefusal(message.getSender(), "Room no longer available");
            return;
        }

        double negotiatedPrice = request.getNegotiatedPrice() > 0
            ? request.getNegotiatedPrice() : basePrice;

        ReservationConfirmation confirmation = new ReservationConfirmation(
            request.getRequestId(),
            request.getCustomerId(),
            hotelId,
            hotelName,
            negotiatedPrice,
            request.getNumberOfNights()
        );
        confirmation.setRoomType("standard");
        confirmation.setCheckInDate(request.getCheckInDate());
        confirmation.setCheckOutDate(request.getCheckOutDate());
        confirmation.setOriginalPrice(basePrice);
        if (basePrice > 0) {
            confirmation.setDiscountPercent(((basePrice - negotiatedPrice) / basePrice) * 100);
        }

        getLogger().info("[{}] Negotiated reservation CONFIRMED: {} - ${}/night (was ${}, {}% off)",
            getOwner().getName(), confirmation.getConfirmationNumber(),
            negotiatedPrice, basePrice, String.format("%.1f", confirmation.getDiscountPercent()));
        ActivityLog.log(hotelName, message.getSender().getAgentName(), "CONFIRM",
            String.format("Confirmed! #%s - $%.0f/night (was $%.0f, %.1f%% off)",
                confirmation.getConfirmationNumber(), negotiatedPrice, basePrice, confirmation.getDiscountPercent()));

        sendMessage(MessageTypes.MSG_CONFIRM, confirmation, message.getSender());
        currentNegotiations.remove(request.getProposalId());
    }

    /**
     * Calculate the hotel's counter-offer price based on round progress and demand.
     * Strategy: start from basePrice, reduce towards effectiveMinPrice as rounds progress.
     * Higher flexibility = faster price reduction. Scarce rooms = higher floor.
     */
    private double calculateHotelCounterOffer(int round, int maxRounds) {
        return pricingStrategy.counterOffer(basePrice, getEffectiveMinPrice(), negotiationFlexibility, round, maxRounds);
    }

    // Configuration methods
    public void setResponseRate(double rate) {
        this.responseRate = Math.max(0, Math.min(1, rate));
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }

    // ==========================================
    // HOOK: DF Registration Validation
    // ==========================================

    /**
     * Validate hotel data before registering with Directory Facilitator.
     * Checks: basePrice > 0, totalRooms > 0, location != null, rank 1-5.
     */
    @BeforeActionSpec("registerWithDF")
    private ActionParams beforeRegisterWithDF(ActionParams params) {
        HotelAgent ha = (HotelAgent) getOwner();
        boolean valid = true;
        StringBuilder issues = new StringBuilder();

        if (basePrice <= 0) {
            issues.append("basePrice must be > 0 (got ").append(basePrice).append("); ");
            valid = false;
        }
        if (ha.getTotalRooms() <= 0) {
            issues.append("totalRooms must be > 0 (got ").append(ha.getTotalRooms()).append("); ");
            valid = false;
        }
        if (location == null || location.isBlank()) {
            issues.append("location must not be null/blank; ");
            valid = false;
        }
        if (rank < 1 || rank > 5) {
            issues.append("rank must be 1-5 (got ").append(rank).append("); ");
            valid = false;
        }

        params.set("valid", valid);
        params.set("issues", issues.toString());

        if (!valid) {
            getLogger().warn("[{}] VALIDATION_FAIL for DF registration: {}",
                getOwner().getName(), issues);
            ActivityLog.log(hotelName, "DirectoryFacilitator", "VALIDATION_FAIL", issues.toString());
        } else {
            getLogger().info("[{}] DF registration validation passed", getOwner().getName());
        }

        return params;
    }

    /**
     * Log the result of DF registration (success or failure).
     */
    @AfterActionSpec("registerWithDF")
    private void afterRegisterWithDF(ActionParams params) {
        boolean registered = params.getBoolean("registered");
        if (registered) {
            getLogger().info("[{}] REGISTERED with Directory Facilitator successfully", getOwner().getName());
            ActivityLog.log(hotelName, "DirectoryFacilitator", "REGISTERED",
                String.format("%s (%d★, $%.0f/night) in %s", hotelName, rank, basePrice, location));
        } else {
            String reason = params.has("issues") ? params.getString("issues") : "Unknown reason";
            getLogger().warn("[{}] Failed to register with DF: {}", getOwner().getName(), reason);
            ActivityLog.log(hotelName, "DirectoryFacilitator", "REGISTER_FAIL", reason);
        }
    }

    /**
     * Register this hotel with the Directory Facilitator.
     * Moved from HotelAgent to HotelProviderRole for proper hook support.
     */
    @ActionSpec(type = ActionType.LOCAL, description = "Register hotel with Directory Facilitator after validation")
    public void registerWithDF() {
        // Before-hook: validate data
        ActionParams params = new ActionParams(new HashMap<>(), "registerWithDF");
        params = beforeRegisterWithDF(params);

        if (!params.getBoolean("valid")) {
            params.set("registered", false);
            afterRegisterWithDF(params);
            return;
        }

        // Perform registration
        DirectoryFacilitator df = getOwner().getPlayground()
            .getAgent(DirectoryFacilitator.class, "DF");
        boolean registered = false;
        if (df != null) {
            DFEntry entry = new DFEntry(
                getOwner().getName(),
                getOwner().getName(),
                hotelId,
                hotelName,
                location,
                rank,
                basePrice
            );
            registered = df.register(entry);
        } else {
            getLogger().warn("[{}] Directory Facilitator not found!", getOwner().getName());
        }

        // After-hook: log result
        params.set("registered", registered);
        afterRegisterWithDF(params);
    }

    // ==========================================
    // LLM Helper: Ollama REST API
    // ==========================================

    /**
     * Call Ollama REST API for LLM inference.
     * Uses the model configured in @RoleSpec @LLMSpec annotation.
     */
    private String callOllama(String prompt) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        String model = "minimax-m2.1:cloud"; // from @RoleSpec @LLMSpec
        String json = String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
            model, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:11434/api/generate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(EnvConfig.llmTimeoutMs()))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                int idx = body.indexOf("\"response\":\"");
                if (idx >= 0) {
                    int start = idx + 12;
                    int end = body.indexOf("\"", start);
                    return body.substring(start, end).replace("\\n", "\n");
                }
            }
            throw new RuntimeException("Ollama returned status " + response.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    // Getters
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getLocation() { return location; }
    public int getRank() { return rank; }
    public double getBasePrice() { return basePrice; }
}
