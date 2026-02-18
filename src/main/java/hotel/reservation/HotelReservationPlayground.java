package hotel.reservation;

import ai.scop.core.Agent;
import ai.scop.core.Params;
import ai.scop.core.Playground;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.core.env.network.NetworkMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.DataFetcherAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.data.model.CustomerSpec;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.repository.CustomerRepository;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.DataFetcherRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hotel Reservation Playground - Main simulation environment.
 * Sets up the Directory Facilitator, Hotel Agents, and Customer Agents.
 *
 * Hotel data is fetched from the Hotel Data API (Javalin REST server on port 7070).
 */
public class HotelReservationPlayground extends Playground {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelReservationPlayground.class);
    private static final int API_PORT = 7070;

    private DirectoryFacilitator directoryFacilitator;
    private NetworkEnvironment hotelEnv;
    private DataFetcherAgent dataFetcherAgent;
    private final List<HotelAgent> hotelAgents = new ArrayList<>();
    private final List<CustomerAgent> customerAgents = new ArrayList<>();

    @Override
    protected void initializeParameters() {
        super.initializeParameters();
        Params.setParameter(Playground.P_TIME_OUT_TICK, "100000");
        Params.setParameter(Playground.P_STEP_DELAY, "1500");
    }

    @Override
    protected void setup() {
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════╗");
        LOGGER.info("║     HOTEL RESERVATION MULTI-AGENT SYSTEM               ║");
        LOGGER.info("╚════════════════════════════════════════════════════════╝");
        LOGGER.info("");

        // Create NetworkEnvironment - provides JGraphT graph topology + messaging
        hotelEnv = create(new NetworkEnvironment("HotelEnv"));

        // Create Directory Facilitator
        directoryFacilitator = create(new DirectoryFacilitator("DF"));

        // Create DataFetcher Agent - SCOP Role for API data retrieval
        dataFetcherAgent = create(new DataFetcherAgent(API_PORT));
        hotelEnv.add(dataFetcherAgent);

        // Create Hotel Agents - data fetched from API (fallback: local repository)
        createHotelAgents();

        // Create Customer Agents
        createCustomerAgents();

        // Generate network topology from runtime CSV
        regenerateNetwork();

        LOGGER.info("");
        LOGGER.info("┌─ SETUP COMPLETE ─────────────────────────────────────┐");
        LOGGER.info("│  Hotels: {}                                           │", directoryFacilitator.getRegisteredCount());
        LOGGER.info("│  Customers: {}                                        │", customerAgents.size());
        LOGGER.info("│  Agents: {} ({} hotel + {} customer + DF + DataFetcher + env) │",
            hotelAgents.size() + customerAgents.size() + 3, hotelAgents.size(), customerAgents.size());
        LOGGER.info("└──────────────────────────────────────────────────────┘");
        LOGGER.info("");
    }

    /**
     * Create hotel agents by fetching data from the Hotel Data API via DataFetcherRole.
     */
    private void createHotelAgents() {
        DataFetcherRole fetcherRole = dataFetcherAgent.as(DataFetcherRole.class);
        List<Hotel> hotels = fetcherRole.fetchAllHotels();
        LOGGER.info("Fetched {} hotels from API (http://localhost:{})", hotels.size(), API_PORT);

        int successCount = 0;
        for (Hotel hotel : hotels) {
            HotelAgent agent = create(new HotelAgent(
                hotel.getId(),
                hotel.getName(),
                hotel.getCity(),
                hotel.getRank(),
                hotel.getPricePerNight(),
                hotel.getTotalRooms()
            ));
            hotelEnv.add(agent);
            hotelAgents.add(agent);
            if (agent.isDataLoaded()) {
                successCount++;
                LOGGER.info("  {} - {} ({}★ ${}/night)",
                    agent.getHotelId(), agent.getHotelName(),
                    agent.getRank(), agent.getBasePrice());
            }
        }

        LOGGER.info("Loaded {}/{} hotels from API", successCount, hotels.size());
    }

    /**
     * Create customer agents by fetching data from the Customer Data API.
     * Falls back to local CustomerRepository if the API is unavailable.
     */
    private void createCustomerAgents() {
        List<CustomerSpec> specs = fetchCustomerSpecs();
        LOGGER.info("Fetched {} customers from {}", specs.size(),
            specs.isEmpty() ? "none" : "API/repository");

        for (CustomerSpec spec : specs) {
            CustomerAgent customer = create(new CustomerAgent(
                spec.getName(), spec.getDesiredLocation(),
                spec.getDesiredRank(), spec.getMaxPrice()
            ));
            hotelEnv.add(customer);
            customerAgents.add(customer);

            LOGGER.info("Customer: {} → {}★ {} (max ${})",
                customer.getName(), customer.getDesiredRank(),
                customer.getDesiredLocation(), customer.getMaxPrice());
        }
    }

    /**
     * Fetch customer specs from API via DataFetcherRole, with fallback to local repository.
     */
    private List<CustomerSpec> fetchCustomerSpecs() {
        // Try API via DataFetcherRole first
        try {
            DataFetcherRole fetcherRole = dataFetcherAgent.as(DataFetcherRole.class);
            List<CustomerSpec> customers = fetcherRole.fetchAllCustomers();
            if (!customers.isEmpty()) {
                LOGGER.info("Fetched {} customers from API (http://localhost:{})", customers.size(), API_PORT);
                return customers;
            }
        } catch (Exception e) {
            LOGGER.warn("Customer API unavailable: {}", e.getMessage());
        }

        // Fallback: load directly from repository
        LOGGER.info("Falling back to local CustomerRepository");
        CustomerRepository repo = new CustomerRepository();
        repo.initialize();
        return repo.findAll();
    }

    /**
     * Add a hotel agent with pre-loaded data.
     */
    public HotelAgent addHotelOffline(String hotelId, String hotelName, String location,
                                       int rank, double basePrice) {
        HotelAgent hotel = create(new HotelAgent(hotelId, hotelName, location, rank, basePrice));
        hotelEnv.add(hotel);
        hotelAgents.add(hotel);
        regenerateNetwork();
        LOGGER.info("Added hotel: {}", hotel);
        return hotel;
    }

    /**
     * Add a customer agent dynamically.
     */
    public CustomerAgent addCustomer(String name, String location, int minRank, double maxPrice) {
        CustomerAgent customer = create(new CustomerAgent(name, location, minRank, maxPrice));
        hotelEnv.add(customer);
        customerAgents.add(customer);
        regenerateNetwork();
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

    /**
     * Trigger search for all customer agents.
     */
    public void triggerAllSearches() {
        LOGGER.info("Triggering search for {} customers", customerAgents.size());
        for (CustomerAgent customer : customerAgents) {
            customer.startSearch();
        }
    }

    /**
     * Get all customer agents.
     */
    public List<CustomerAgent> getCustomerAgents() {
        return new ArrayList<>(customerAgents);
    }

    /**
     * Get the hotel network environment.
     */
    public NetworkEnvironment getHotelNetwork() {
        return hotelEnv;
    }

    /**
     * Get neighbors of an agent in the hotel network.
     */
    public List<Agent> getAgentNeighbors(Agent agent) {
        return hotelEnv.getNeighbors(agent);
    }

    // ==========================================
    // NETWORK TOPOLOGY
    // ==========================================

    /**
     * Regenerate network topology by creating a CSV from current agents
     * and loading it via NetworkEnvironment.generateNetwork().
     */
    private void regenerateNetwork() {
        try {
            Path csvPath = generateTopologyCsv();
            hotelEnv.generateNetwork(csvPath, "from", "to", ',');
            logNetworkMetrics();
        } catch (IOException e) {
            LOGGER.error("Failed to generate network topology: {}", e.getMessage());
        }
    }

    /**
     * Generate a CSV file describing the network topology at runtime.
     *
     * Rules:
     * - Customer <-> Hotel: every customer connects to every hotel
     * - Hotel <-> Hotel: connected if in the same city
     */
    private Path generateTopologyCsv() throws IOException {
        Path csvPath = Files.createTempFile("hotel-network-", ".csv");
        csvPath.toFile().deleteOnExit();

        List<String> lines = new ArrayList<>();
        lines.add("from,to");

        // Customer <-> Hotel
        for (CustomerAgent customer : customerAgents) {
            for (HotelAgent hotel : hotelAgents) {
                lines.add(customer.getName() + "," + hotel.getName());
            }
        }

        // Hotel <-> Hotel (same city)
        for (int i = 0; i < hotelAgents.size(); i++) {
            for (int j = i + 1; j < hotelAgents.size(); j++) {
                HotelAgent h1 = hotelAgents.get(i);
                HotelAgent h2 = hotelAgents.get(j);
                if (h1.getLocation() != null
                        && h1.getLocation().equalsIgnoreCase(h2.getLocation())) {
                    lines.add(h1.getName() + "," + h2.getName());
                }
            }
        }

        Files.write(csvPath, lines);
        LOGGER.info("Generated topology CSV: {} edges", lines.size() - 1);
        return csvPath;
    }

    /**
     * Log network metrics to console.
     */
    private void logNetworkMetrics() {
        try {
            NetworkMetrics metrics = hotelEnv.getNetworkMetrics();
            LOGGER.info("");
            LOGGER.info("┌─ NETWORK METRICS ────────────────────────────────────┐");
            LOGGER.info("│  Nodes: {}  Edges: {}  Components: {}",
                metrics.getNodeCount(), metrics.getEdgeCount(), metrics.getConnectedComponents());
            LOGGER.info("│  Avg Degree: {}  Density: {}",
                String.format("%.2f", metrics.getAverageDegree()),
                String.format("%.4f", metrics.getDensity()));
            LOGGER.info("│  Clustering: {}  Avg Path: {}  Diameter: {}",
                String.format("%.4f", metrics.getAverageClusteringCoefficient()),
                String.format("%.2f", metrics.getAveragePathLength()),
                metrics.getDiameter());
            LOGGER.info("│  Small-world: {} ({})",
                metrics.isSmallWorld(), metrics.getSmallWorldExplanation());
            LOGGER.info("└──────────────────────────────────────────────────────┘");
        } catch (Exception e) {
            LOGGER.warn("Could not compute network metrics: {}", e.getMessage());
        }
    }
}
