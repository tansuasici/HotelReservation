package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Identifier;
import ai.scop.core.Role;
import ai.scop.core.messaging.Message;
import com.tnsai.annotations.*;
import com.tnsai.annotations.LLMSpec.Provider;
import com.tnsai.enums.ActionType;
import hotel.reservation.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelProviderRole.class);

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
    private double responseRate = 0.8;  // 80% chance to respond

    @State(description = "Whether hotel has available rooms")
    private boolean available = true;

    public HotelProviderRole(Agent owner, String envName,
                             String hotelId, String hotelName, String location,
                             int rank, double basePrice) {
        super(owner, envName);
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.basePrice = basePrice;
    }

    /**
     * Handle CFP (Call For Proposals) message.
     * Decides whether to respond with a proposal or refuse.
     */
    @Action(type = ActionType.LOCAL, description = "Process incoming CFP and decide whether to make a proposal")
    public void handleCFPMessage(Message<RoomQuery> message) {
        RoomQuery query = message.getPayload();
        LOGGER.info("[{}] Received CFP from {}: {}",
            getOwner().getName(), message.getSender(), query);

        // Check if hotel matches the query criteria
        if (!matchesQuery(query)) {
            LOGGER.info("[{}] Query does not match hotel criteria - ignoring",
                getOwner().getName());
            return;
        }

        // Simulate random decision to respond (availability simulation)
        if (!shouldRespond()) {
            LOGGER.info("[{}] Decided not to respond to CFP (simulating unavailability)",
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
    @Action(type = ActionType.LOCAL, description = "Verify if hotel matches customer search criteria")
    private boolean matchesQuery(RoomQuery query) {
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

    /**
     * Send a proposal to the customer.
     */
    @Action(type = ActionType.LOCAL, description = "Generate and send a room proposal to customer")
    private void sendProposal(Identifier customer) {
        RoomProposal proposal = new RoomProposal(
            hotelId,
            hotelName,
            location,
            rank,
            basePrice,
            "standard"  // Default room type
        );
        proposal.setAmenities(List.of("wifi", "breakfast"));
        proposal.setRating(4.5);

        LOGGER.info("[{}] Sending proposal to {}: {} - ${}/night",
            getOwner().getName(), customer, hotelName, basePrice);

        // Use Role's sendMessage method
        sendMessage(MessageTypes.MSG_PROPOSAL, proposal, customer);
    }

    /**
     * Send a refusal to the customer.
     */
    @Action(type = ActionType.LOCAL, description = "Send refusal message to customer")
    private void sendRefusal(Identifier customer, String reason) {
        LOGGER.info("[{}] Sending refusal to {}: {}",
            getOwner().getName(), customer, reason);

        sendMessage(MessageTypes.MSG_REFUSE, reason, customer);
    }

    /**
     * Handle Accept message - Customer accepted our proposal.
     */
    @Action(type = ActionType.LOCAL, description = "Process reservation acceptance and send confirmation")
    public void handleAcceptMessage(Message<ReservationRequest> message) {
        ReservationRequest request = message.getPayload();
        LOGGER.info("[{}] Received ACCEPT from {}: {}",
            getOwner().getName(), message.getSender(), request);

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

        LOGGER.info("[{}] Reservation CONFIRMED: {} - Total: ${}",
            getOwner().getName(),
            confirmation.getConfirmationNumber(),
            confirmation.getTotalPrice());

        // Send confirmation using Role's sendMessage
        sendMessage(MessageTypes.MSG_CONFIRM, confirmation, message.getSender());

        // Mark as temporarily unavailable (optional)
        // this.available = false;
    }

    /**
     * Handle Reject message - Customer rejected our proposal.
     */
    @Action(type = ActionType.LOCAL, description = "Process proposal rejection from customer")
    public void handleRejectMessage(Message<String> message) {
        LOGGER.info("[{}] Proposal REJECTED by {}: {}",
            getOwner().getName(), message.getSender(), message.getPayload());
        // Clean up if needed
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

    // Getters
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getLocation() { return location; }
    public int getRank() { return rank; }
    public double getBasePrice() { return basePrice; }
}
