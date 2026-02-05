package hotel.reservation.agent;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.annotations.LLMSpec.Provider;
import hotel.reservation.config.AppConfig;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.df.DFEntry;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.HotelProviderRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Hotel Agent - Represents a hotel in the multi-agent system.
 * Fetches hotel data from Hotel Data API and registers with Directory Facilitator.
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

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelAgent.class);

    private final String hotelId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // These will be populated from API
    private String hotelName;
    private String location;
    private int rank;
    private double basePrice;
    private boolean dataLoaded = false;

    /**
     * Create a hotel agent that fetches its data from Hotel Data API.
     *
     * @param hotelId Unique hotel identifier (e.g., "h001")
     */
    public HotelAgent(String hotelId) {
        super("Hotel-" + hotelId);
        this.hotelId = hotelId;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a hotel agent with pre-loaded data (for testing or offline mode).
     */
    public HotelAgent(String hotelId, String hotelName, String location, int rank, double basePrice) {
        super("Hotel-" + hotelId);
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.basePrice = basePrice;
        this.dataLoaded = true;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void setup() {
        LOGGER.info("[{}] Hotel Agent starting for hotelId: {}", getName(), hotelId);

        // Fetch hotel data from API if not pre-loaded
        if (!dataLoaded) {
            boolean success = fetchHotelDataFromAPI();
            if (!success) {
                LOGGER.error("[{}] Failed to fetch hotel data from API - agent will not be functional", getName());
                return;
            }
        }

        LOGGER.info("[{}] Hotel data loaded: {} ({} star) in {} - ${}/night",
            getName(), hotelName, rank, location, basePrice);

        // Adopt the hotel provider role
        adopt(new HotelProviderRole(this, "HotelEnv", hotelId, hotelName, location, rank, basePrice));

        // Conversation role - for chat (like LifeAgent)
        adopt(new Conversation(this, getPlayground()));

        // Register with Directory Facilitator
        registerWithDF();

        LOGGER.info("[{}] Hotel Agent ready", getName());
    }

    /**
     * Fetch hotel data from Hotel Data API.
     * This demonstrates the WEB_SERVICE pattern - calling an external REST API.
     */
    private boolean fetchHotelDataFromAPI() {
        String apiUrl = AppConfig.getHotelApiBase() + "/" + hotelId;
        LOGGER.info("[{}] Fetching hotel data from API: {}", getName(), apiUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Hotel hotel = objectMapper.readValue(response.body(), Hotel.class);

                this.hotelName = hotel.getName();
                this.location = hotel.getCity();
                this.rank = hotel.getRank();
                this.basePrice = hotel.getPricePerNight();
                this.dataLoaded = true;

                LOGGER.info("[{}] Successfully fetched hotel data from API", getName());
                return true;
            } else {
                LOGGER.error("[{}] API returned status {}: {}", getName(), response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to fetch hotel data from API: {}", getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Register this hotel with the Directory Facilitator.
     */
    private void registerWithDF() {
        DirectoryFacilitator df = getPlayground().getAgent(DirectoryFacilitator.class, "DF");
        if (df != null) {
            DFEntry entry = new DFEntry(
                getName(),
                getName(),
                hotelId,
                hotelName,
                location,
                rank,
                basePrice
            );
            df.register(entry);
            LOGGER.info("[{}] Registered with Directory Facilitator", getName());
        } else {
            LOGGER.warn("[{}] Directory Facilitator not found!", getName());
        }
    }

    @Override
    public String getDisplayName() {
        return hotelName != null ? hotelName : getName();
    }

    // Getters
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getLocation() { return location; }
    public int getRank() { return rank; }
    public double getBasePrice() { return basePrice; }
    public boolean isDataLoaded() { return dataLoaded; }

    @Override
    public String toString() {
        return String.format("HotelAgent[%s] %s - %d star - $%.2f - %s",
            hotelId, hotelName, rank, basePrice, location);
    }
}
