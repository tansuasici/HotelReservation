package hotel.reservation;

import ai.scop.core.Agent;
import ai.scop.core.Params;
import ai.scop.core.Playground;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.core.env.network.NetworkMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.repository.HotelRepository;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.CustomerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hotel Reservation Playground - Main simulation environment.
 * Sets up the Directory Facilitator, Hotel Agents, and Customer Agents.
 *
 * Hotel data is loaded directly from HotelRepository (hotel-data.json).
 * No external API dependency required.
 */
public class HotelReservationPlayground extends Playground {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelReservationPlayground.class);

    private DirectoryFacilitator directoryFacilitator;
    private NetworkEnvironment hotelEnv;
    private final List<HotelAgent> hotelAgents = new ArrayList<>();
    private final List<CustomerAgent> customerAgents = new ArrayList<>();

    @Override
    protected void initializeParameters() {
        super.initializeParameters();
        Params.setParameter(Playground.P_TIME_OUT_TICK, "100000");
        Params.setParameter(Playground.P_STEP_DELAY, "100");
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

        // Create Hotel Agents - data loaded directly from HotelRepository
        createHotelAgents();

        // Create Customer Agents
        createCustomerAgents();

        // Generate network topology from runtime CSV
        regenerateNetwork();

        LOGGER.info("");
        LOGGER.info("┌─ SETUP COMPLETE ─────────────────────────────────────┐");
        LOGGER.info("│  Hotels: {}                                           │", directoryFacilitator.getRegisteredCount());
        LOGGER.info("│  Customers: {}                                        │", customerAgents.size());
        LOGGER.info("│  Agents: {} ({} hotel + {} customer + DF + env)       │",
            hotelAgents.size() + customerAgents.size() + 2, hotelAgents.size(), customerAgents.size());
        LOGGER.info("└──────────────────────────────────────────────────────┘");
        LOGGER.info("");
    }

    /**
     * Create hotel agents by loading data directly from HotelRepository.
     */
    private void createHotelAgents() {
        LOGGER.info("Loading hotels from repository...");

        HotelRepository hotelRepository = new HotelRepository();
        hotelRepository.initialize();

        List<Hotel> hotels = hotelRepository.findAll();
        int successCount = 0;

        for (Hotel hotel : hotels) {
            HotelAgent agent = create(new HotelAgent(
                hotel.getId(),
                hotel.getName(),
                hotel.getCity(),
                hotel.getRank(),
                hotel.getPricePerNight()
            ));
            hotelEnv.add(agent);
            hotelAgents.add(agent);
            if (agent.isDataLoaded()) {
                successCount++;
                LOGGER.info("  ✓ {} - {} ({}★ ${}/night)",
                    agent.getHotelId(), agent.getHotelName(),
                    agent.getRank(), agent.getBasePrice());
            }
        }

        LOGGER.info("Loaded {}/{} hotels from repository", successCount, hotels.size());
    }

    /**
     * Create customer agents with diverse search criteria.
     */
    private void createCustomerAgents() {
        // name, city, minStars, maxPrice
        Object[][] specs = {
            {"Customer-1", "Istanbul", 5, 480.0},   // 2 Istanbul 5★ match → picks cheapest, negotiates
            {"Customer-2", "Ankara",   3, 280.0},   // Ankara Business Hotel $250 → negotiation
            {"Customer-3", "Istanbul", 3, 180.0},   // Budget Inn Istanbul $150 → negotiation
            {"Customer-4", "Izmir",    3, 350.0},   // Sea View Resort $300 → negotiation
            {"Customer-5", "Antalya",  4, 550.0},   // Antalya Beach Resort $500 → negotiation
        };

        for (Object[] s : specs) {
            CustomerAgent customer = create(new CustomerAgent(
                (String) s[0], (String) s[1], (int) s[2], (double) s[3]
            ));
            hotelEnv.add(customer);
            customerAgents.add(customer);

            LOGGER.info("Customer: {} → {}★ {} (max ${})",
                customer.getName(), customer.getDesiredRank(),
                customer.getDesiredLocation(), customer.getMaxPrice());
        }
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
    // OUTPUT FILE WRITING
    // ==========================================

    /**
     * Write JSON output files after simulation.
     * Writes topology.json, hotels.json, customers.json, activity.json, agents.json
     * to the output-data/ directory.
     */
    public void writeOutputFiles() {
        Path outputDir = Path.of("output-data");
        try {
            Files.createDirectories(outputDir);
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            // topology.json - nodes + edges
            writeTopology(mapper, outputDir);

            // hotels.json
            writeHotels(mapper, outputDir);

            // customers.json
            writeCustomers(mapper, outputDir);

            // activity.json
            writeActivity(mapper, outputDir);

            // agents.json
            writeAgents(mapper, outputDir);

            LOGGER.info("Output files written to: {}", outputDir.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to write output files: {}", e.getMessage(), e);
        }
    }

    /**
     * Write topology.json with nodes and edges.
     */
    private void writeTopology(ObjectMapper mapper, Path outputDir) throws IOException {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> edgeSet = new HashSet<>();
        List<Map<String, String>> edges = new ArrayList<>();

        var agents = getScenarioAgents();
        if (agents != null) {
            for (var agent : agents) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", agent.getName());
                node.put("displayName", agent.getDisplayName());
                node.put("type", agent.getClass().getSimpleName());

                if (agent instanceof HotelAgent ha) {
                    node.put("hotelId", ha.getHotelId());
                    node.put("location", ha.getLocation());
                    node.put("rank", ha.getRank());
                    node.put("basePrice", ha.getBasePrice());
                } else if (agent instanceof CustomerAgent ca) {
                    node.put("location", ca.getDesiredLocation());
                    node.put("desiredRank", ca.getDesiredRank());
                    node.put("maxPrice", ca.getMaxPrice());
                }

                nodes.add(node);

                // Get neighbors for edges
                try {
                    var neighbors = getAgentNeighbors(agent);
                    if (neighbors != null) {
                        for (var neighbor : neighbors) {
                            String edgeKey = agent.getName().compareTo(neighbor.getName()) < 0
                                ? agent.getName() + "→" + neighbor.getName()
                                : neighbor.getName() + "→" + agent.getName();
                            if (edgeSet.add(edgeKey)) {
                                edges.add(Map.of(
                                    "from", agent.getName(),
                                    "to", neighbor.getName()
                                ));
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not get neighbors for {}: {}", agent.getName(), e.getMessage());
                }
            }
        }

        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("nodes", nodes);
        topology.put("edges", edges);
        mapper.writeValue(outputDir.resolve("topology.json").toFile(), topology);
        LOGGER.info("  Written: topology.json ({} nodes, {} edges)", nodes.size(), edges.size());
    }

    /**
     * Write hotels.json from HotelRepository data.
     */
    private void writeHotels(ObjectMapper mapper, Path outputDir) throws IOException {
        HotelRepository repo = new HotelRepository();
        repo.initialize();
        List<Hotel> hotels = repo.findAll();
        mapper.writeValue(outputDir.resolve("hotels.json").toFile(), hotels);
        LOGGER.info("  Written: hotels.json ({} hotels)", hotels.size());
    }

    /**
     * Write customers.json with customer state information.
     */
    private void writeCustomers(ObjectMapper mapper, Path outputDir) throws IOException {
        List<Map<String, Object>> customerList = new ArrayList<>();

        for (CustomerAgent customer : customerAgents) {
            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("customerId", customer.getName());
            cMap.put("desiredLocation", customer.getDesiredLocation());
            cMap.put("desiredRank", customer.getDesiredRank());
            cMap.put("maxPrice", customer.getMaxPrice());
            cMap.put("desiredPrice", customer.getDesiredPrice());

            try {
                CustomerRole role = customer.as(CustomerRole.class);
                if (role != null) {
                    cMap.put("state", role.getCustomerState().name());
                    cMap.put("negotiationRound", role.getNegotiationRound());
                    if (role.getSelectedProposal() != null) {
                        cMap.put("selectedHotel", role.getSelectedProposal().getHotelName());
                        cMap.put("selectedPrice", role.getSelectedProposal().getPricePerNight());
                    }
                    if (role.getConfirmation() != null) {
                        cMap.put("confirmationNumber", role.getConfirmation().getConfirmationNumber());
                    }
                } else {
                    cMap.put("state", "UNKNOWN");
                }
            } catch (Exception e) {
                cMap.put("state", "ENDED");
            }

            customerList.add(cMap);
        }

        mapper.writeValue(outputDir.resolve("customers.json").toFile(), customerList);
        LOGGER.info("  Written: customers.json ({} customers)", customerList.size());
    }

    /**
     * Write activity.json from ActivityLog entries.
     */
    private void writeActivity(ObjectMapper mapper, Path outputDir) throws IOException {
        List<Map<String, Object>> activityList = ActivityLog.getEntries().stream()
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("timestamp", e.timestamp());
                m.put("from", e.from());
                m.put("to", e.to());
                m.put("type", e.type());
                m.put("detail", e.detail());
                return m;
            })
            .collect(Collectors.toList());

        mapper.writeValue(outputDir.resolve("activity.json").toFile(), activityList);
        LOGGER.info("  Written: activity.json ({} entries)", activityList.size());
    }

    /**
     * Write agents.json with basic agent information.
     */
    private void writeAgents(ObjectMapper mapper, Path outputDir) throws IOException {
        List<Map<String, String>> agentList = new ArrayList<>();

        var agents = getScenarioAgents();
        if (agents != null) {
            for (var agent : agents) {
                agentList.add(Map.of(
                    "name", agent.getName(),
                    "displayName", agent.getDisplayName(),
                    "type", agent.getClass().getSimpleName()
                ));
            }
        }

        mapper.writeValue(outputDir.resolve("agents.json").toFile(), agentList);
        LOGGER.info("  Written: agents.json ({} agents)", agentList.size());
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
