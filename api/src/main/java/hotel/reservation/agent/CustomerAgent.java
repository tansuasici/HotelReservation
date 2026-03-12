package hotel.reservation.agent;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.annotations.LLMSpec.Provider;
import hotel.reservation.role.CustomerRole;
import hotel.reservation.role.pricing.LinearPricingStrategy;

import java.util.Collections;
import java.util.List;

/**
 * Customer Agent - Represents a customer looking for hotel rooms.
 * Implements the CNP initiator role.
 */
@AgentSpec(
    description = "Hotel reservation customer agent that searches and books hotels",
    llm = @LLMSpec(
        provider = Provider.OLLAMA,
        model = "glm-5:cloud",
        temperature = 0.7f
    )
)
public class CustomerAgent extends Agent {

    private final String desiredLocation;
    private final int desiredRank;
    private final double maxPrice;
    private final double desiredPrice;  // For negotiation
    private final int numberOfRooms;
    private final List<String> amenities;

    /**
     * Create a customer agent.
     */
    public CustomerAgent(String name, String desiredLocation, int desiredRank, double maxPrice) {
        this(name, desiredLocation, desiredRank, maxPrice, 1, Collections.emptyList());
    }

    /**
     * Create a customer agent with room count and amenities.
     */
    public CustomerAgent(String name, String desiredLocation, int desiredRank,
                         double maxPrice, int numberOfRooms, List<String> amenities) {
        super(name);
        this.desiredLocation = desiredLocation;
        this.desiredRank = desiredRank;
        this.maxPrice = maxPrice;
        this.desiredPrice = maxPrice * 0.8;
        this.numberOfRooms = numberOfRooms;
        this.amenities = amenities != null ? amenities : Collections.emptyList();
    }

    @Override
    protected void setup() {
        getLogger().info("[{}] Customer Agent starting - Looking for {} star hotel in {} (max ${}/night)",
            getName(), desiredRank, desiredLocation, maxPrice);

        // Adopt the customer role
        adopt(new CustomerRole(this, "HotelEnv", desiredLocation, desiredRank, maxPrice, desiredPrice,
            numberOfRooms, amenities, new LinearPricingStrategy()));

        // Conversation role - for chat (like LifeAgent)
        adopt(new Conversation(this, getPlayground()));

        getLogger().info("[{}] Customer Agent ready", getName());
    }

    /**
     * Called every simulation tick. Checks proposal deadline
     * so customers don't get stuck waiting for unresponsive hotels.
     */
    @Override
    protected void makeStep() {
        super.makeStep();
        CustomerRole role = as(CustomerRole.class);
        if (role != null) {
            if (role.getCustomerState() == CustomerRole.CustomerState.IDLE) {
                role.startSearch();
            } else {
                // Tick-driven: only one action per tick
                // startSearch already did work this tick, so tickCheck runs on next tick
                role.tickCheck();
            }
        }
    }

    @Override
    public String getDisplayName() {
        return getName();
    }

    // Getters
    public String getDesiredLocation() { return desiredLocation; }
    public int getDesiredRank() { return desiredRank; }
    public double getMaxPrice() { return maxPrice; }
    public double getDesiredPrice() { return desiredPrice; }
    public int getNumberOfRooms() { return numberOfRooms; }
    public List<String> getAmenities() { return amenities; }

    /**
     * Start the hotel search process.
     */
    public void startSearch() {
        CustomerRole role = as(CustomerRole.class);
        if (role != null) {
            role.startSearch();
        }
    }

    @Override
    public String toString() {
        return String.format("CustomerAgent[%s] looking for %d star in %s (max $%.2f)",
            getName(), desiredRank, desiredLocation, maxPrice);
    }
}
