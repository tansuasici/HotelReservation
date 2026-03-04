package hotel.reservation.agent;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.annotations.LLMSpec.Provider;
import hotel.reservation.role.HotelProviderRole;
import hotel.reservation.role.pricing.LinearPricingStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Hotel Agent - Represents a hotel in the multi-agent system.
 * Data is loaded directly from HotelRepository (no HTTP calls).
 * Registers with Directory Facilitator on setup.
 */
@AgentSpec(
    description = "Hotel service provider agent that handles room reservations",
    llm = @LLMSpec(
        provider = Provider.OLLAMA,
        model = "minimax-m2.1:cloud",
        temperature = 0.5f
    )
)
public class HotelAgent extends Agent {

    private final String hotelId;

    // Pre-loaded hotel data
    private String hotelName;
    private String location;
    private int rank;
    private double basePrice;
    private List<String> amenities;
    private boolean dataLoaded = false;

    // Room capacity (1-3 rooms per hotel)
    private final int totalRooms;
    private int availableRooms;

    /**
     * Create a hotel agent with pre-loaded data.
     *
     * @param hotelId   Unique hotel identifier (e.g., "h001")
     * @param hotelName Display name of the hotel
     * @param location  City/location of the hotel
     * @param rank      Star rating (1-5)
     * @param basePrice Base price per night
     */
    public HotelAgent(String hotelId, String hotelName, String location, int rank, double basePrice) {
        this(hotelId, hotelName, location, rank, basePrice, 0);
    }

    public HotelAgent(String hotelId, String hotelName, String location, int rank, double basePrice, int totalRooms) {
        this(hotelId, hotelName, location, rank, basePrice, totalRooms, Collections.emptyList());
    }

    /**
     * Create a hotel agent with pre-loaded data, room count, and amenities.
     *
     * @param hotelId    Unique hotel identifier (e.g., "h001")
     * @param hotelName  Display name of the hotel
     * @param location   City/location of the hotel
     * @param rank       Star rating (1-5)
     * @param basePrice  Base price per night
     * @param totalRooms Total room capacity (0 = random 1-3)
     * @param amenities  Available amenities (e.g., "wifi", "pool", "spa")
     */
    public HotelAgent(String hotelId, String hotelName, String location, int rank, double basePrice,
                      int totalRooms, List<String> amenities) {
        super("Hotel-" + hotelId);
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.basePrice = basePrice;
        this.amenities = amenities != null ? amenities : Collections.emptyList();
        this.dataLoaded = true;
        this.totalRooms = totalRooms > 0 ? totalRooms : new Random().nextInt(3) + 1;
        this.availableRooms = this.totalRooms;
    }

    @Override
    protected void setup() {
        getLogger().info("[{}] Hotel Agent starting for hotelId: {}", getName(), hotelId);

        getLogger().info("[{}] Hotel data loaded: {} ({} star) in {} - ${}/night",
            getName(), hotelName, rank, location, basePrice);

        // Adopt the hotel provider role
        HotelProviderRole providerRole = new HotelProviderRole(this, "HotelEnv",
            hotelId, hotelName, location, rank, basePrice, amenities, new LinearPricingStrategy());
        adopt(providerRole);

        // Conversation role - for chat (like LifeAgent)
        adopt(new Conversation(this, getPlayground()));

        // Register with Directory Facilitator (delegated to role with validation hooks)
        providerRole.registerWithDF();

        getLogger().info("[{}] Hotel Agent ready", getName());
    }

    @Override
    public String getDisplayName() {
        return hotelName != null ? hotelName : getName();
    }

    // Room capacity
    public int getTotalRooms() { return totalRooms; }
    public int getAvailableRooms() { return availableRooms; }

    public synchronized boolean reserveRoom() {
        if (availableRooms <= 0) return false;
        availableRooms--;
        return true;
    }

    // Getters
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getLocation() { return location; }
    public int getRank() { return rank; }
    public double getBasePrice() { return basePrice; }
    public List<String> getAmenities() { return amenities; }
    public boolean isDataLoaded() { return dataLoaded; }

    @Override
    public String toString() {
        return String.format("HotelAgent[%s] %s - %d star - $%.2f - %s",
            hotelId, hotelName, rank, basePrice, location);
    }
}
