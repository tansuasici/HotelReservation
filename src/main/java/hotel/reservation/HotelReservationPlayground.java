package hotel.reservation;

import ai.scop.core.Environment;
import ai.scop.core.Playground;
import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.df.DirectoryFacilitator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hotel Reservation Playground - Main simulation environment.
 * Sets up the Directory Facilitator, Hotel Agents, and Customer Agents.
 *
 * IMPORTANT: Hotel Data API must be running before starting the simulation.
 * HotelAgents fetch their data from: http://localhost:8080/api/data/hotels/{id}
 */
public class HotelReservationPlayground extends Playground {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelReservationPlayground.class);

    private DirectoryFacilitator directoryFacilitator;

    @Override
    protected void setup() {
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════╗");
        LOGGER.info("║     HOTEL RESERVATION MULTI-AGENT SYSTEM               ║");
        LOGGER.info("╚════════════════════════════════════════════════════════╝");
        LOGGER.info("");

        // Create main environment
        create(new Environment("HotelEnv"));

        // Create Directory Facilitator
        directoryFacilitator = create(new DirectoryFacilitator("DF"));

        // Create Hotel Agents - they will fetch data from Hotel Data API
        createHotelAgents();

        // Create Customer Agent
        createCustomerAgent();

        LOGGER.info("");
        LOGGER.info("┌─ SETUP COMPLETE ─────────────────────────────────────┐");
        LOGGER.info("│  Hotels: {}                                           │", directoryFacilitator.getRegisteredCount());
        LOGGER.info("│  Agents: 10 (7 hotel + 1 customer + DF + env)        │");
        LOGGER.info("└──────────────────────────────────────────────────────┘");
        LOGGER.info("");
    }

    /**
     * Create hotel agents - each agent fetches its data from Hotel Data API.
     * The API must be running at http://localhost:8080/api/data/hotels
     */
    private void createHotelAgents() {
        LOGGER.info("Loading hotels from API...");

        String[] hotelIds = {"h001", "h002", "h003", "h004", "h005", "h006", "h007"};
        int successCount = 0;

        for (String hotelId : hotelIds) {
            HotelAgent agent = create(new HotelAgent(hotelId));
            if (agent.isDataLoaded()) {
                successCount++;
                LOGGER.info("  ✓ {} - {} ({}★ ${}/night)",
                    agent.getHotelId(), agent.getHotelName(),
                    agent.getRank(), agent.getBasePrice());
            }
        }

        LOGGER.info("Loaded {}/{} hotels from API", successCount, hotelIds.length);
    }

    /**
     * Create customer agent based on configuration.
     */
    private void createCustomerAgent() {
        // Default customer looking for 5-star hotel in Istanbul with max $500/night
        CustomerAgent customer = create(new CustomerAgent(
            "Customer-1",
            "Istanbul",
            5,
            500
        ));

        LOGGER.info("Customer: {} → {}★ {} (max ${})",
            customer.getName(),
            customer.getDesiredRank(),
            customer.getDesiredLocation(),
            customer.getMaxPrice());
    }

    /**
     * Add a hotel agent dynamically (fetches from API).
     */
    public HotelAgent addHotel(String hotelId) {
        HotelAgent hotel = create(new HotelAgent(hotelId));
        LOGGER.info("Added hotel: {}", hotel);
        return hotel;
    }

    /**
     * Add a hotel agent with pre-loaded data (offline mode).
     */
    public HotelAgent addHotelOffline(String hotelId, String hotelName, String location,
                                       int rank, double basePrice) {
        HotelAgent hotel = create(new HotelAgent(hotelId, hotelName, location, rank, basePrice));
        LOGGER.info("Added hotel (offline): {}", hotel);
        return hotel;
    }

    /**
     * Add a customer agent dynamically.
     */
    public CustomerAgent addCustomer(String name, String location, int minRank, double maxPrice) {
        CustomerAgent customer = create(new CustomerAgent(name, location, minRank, maxPrice));
        LOGGER.info("Added customer: {}", customer);
        return customer;
    }

    /**
     * Get the Directory Facilitator.
     */
    public DirectoryFacilitator getDirectoryFacilitator() {
        return directoryFacilitator;
    }

    /**
     * Trigger search for a specific customer.
     */
    public void triggerSearch(String customerName) {
        CustomerAgent customer = (CustomerAgent) findAgent(customerName);
        if (customer != null) {
            customer.startSearch();
        } else {
            LOGGER.warn("Customer not found: {}", customerName);
        }
    }
}
