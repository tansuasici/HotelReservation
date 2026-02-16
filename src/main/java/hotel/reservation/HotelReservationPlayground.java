package hotel.reservation;

import ai.scop.core.Agent;
import ai.scop.core.Params;
import ai.scop.core.Playground;
import ai.scop.core.env.network.NetworkEnvironment;
import ai.scop.core.env.network.NetworkMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.tnsai.annotations.AgentSpec;
import com.tnsai.annotations.LLMSpec;
import com.tnsai.integration.scop.SCOPBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.agent.HotelAgent;
import hotel.reservation.api.HotelApiClient;
import hotel.reservation.api.HotelDataServer;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.df.DirectoryFacilitator;
import hotel.reservation.role.CustomerRole;
import hotel.reservation.role.HotelProviderRole;
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
    private HotelDataServer hotelDataServer;
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

        // Start Hotel Data API server
        hotelDataServer = new HotelDataServer(API_PORT);
        try {
            hotelDataServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopDataServer));
        } catch (Exception e) {
            LOGGER.warn("Could not start Hotel Data API on port {}: {}", API_PORT, e.getMessage());
            hotelDataServer = null;
        }

        // Create NetworkEnvironment - provides JGraphT graph topology + messaging
        hotelEnv = create(new NetworkEnvironment("HotelEnv"));

        // Create Directory Facilitator
        directoryFacilitator = create(new DirectoryFacilitator("DF"));

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
        LOGGER.info("│  Agents: {} ({} hotel + {} customer + DF + env)       │",
            hotelAgents.size() + customerAgents.size() + 2, hotelAgents.size(), customerAgents.size());
        LOGGER.info("└──────────────────────────────────────────────────────┘");
        LOGGER.info("");
    }

    /**
     * Create hotel agents by fetching data from the Hotel Data API.
     */
    private void createHotelAgents() {
        HotelApiClient apiClient = new HotelApiClient(API_PORT);
        List<Hotel> hotels = apiClient.fetchAllHotels();
        LOGGER.info("Fetched {} hotels from API (http://localhost:{})", hotels.size(), API_PORT);

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
                LOGGER.info("  {} - {} ({}★ ${}/night)",
                    agent.getHotelId(), agent.getHotelName(),
                    agent.getRank(), agent.getBasePrice());
            }
        }

        LOGGER.info("Loaded {}/{} hotels from API", successCount, hotels.size());
    }

    /**
     * Create customer agents with diverse search criteria.
     */
    private void createCustomerAgents() {
        // name, city, minStars, maxPrice
        Object[][] specs = {
            // --- Istanbul cluster (5 customers competing for 3 hotels) ---
            {"Customer-1",  "Istanbul", 5, 480.0},   // Luxury Palace $400 or Grand $450 → negotiation
            {"Customer-2",  "Istanbul", 5, 470.0},   // Same 5★ Istanbul → competes with C1
            {"Customer-3",  "Istanbul", 3, 180.0},   // Budget Inn $150 → negotiation
            {"Customer-4",  "Istanbul", 3, 170.0},   // Same Budget Inn → competes with C3 for rooms
            {"Customer-5",  "Istanbul", 4, 320.0},   // Mid-range → may fail (no 4★ Istanbul hotel)

            // --- Ankara (2 customers, 1 hotel) ---
            {"Customer-6",  "Ankara",   3, 280.0},   // Ankara Business $250 → negotiation
            {"Customer-7",  "Ankara",   3, 260.0},   // Same hotel → competes with C6

            // --- Izmir (2 customers, 1 hotel) ---
            {"Customer-8",  "Izmir",    3, 350.0},   // Sea View Resort $300 → negotiation
            {"Customer-9",  "Izmir",    3, 320.0},   // Same resort → competes for rooms

            // --- Antalya (2 customers, 1 hotel) ---
            {"Customer-10", "Antalya",  4, 550.0},   // Antalya Beach $500 → negotiation
            {"Customer-11", "Antalya",  3, 280.0},   // Budget Antalya → likely FAIL (no 3★ match)

            // --- Nevsehir (2 customers, 1 hotel) ---
            {"Customer-12", "Nevsehir", 4, 400.0},   // Cappadocia Cave $350 → negotiation
            {"Customer-13", "Nevsehir", 4, 380.0},   // Same hotel → competes with C12

            // --- Mugla (2 customers, 1 hotel) ---
            {"Customer-14", "Mugla",    3, 300.0},   // Bodrum Boutique $280 → negotiation
            {"Customer-15", "Mugla",    3, 290.0},   // Same hotel → competes for last room
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
     * Stop the Hotel Data API server if it is running.
     */
    public void stopDataServer() {
        if (hotelDataServer != null) {
            hotelDataServer.stop();
            hotelDataServer = null;
        }
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

            // prompts/ - system prompts from SCOPBridge
            writePrompts(outputDir);

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

                // LLM model from @AgentSpec annotation
                AgentSpec spec = agent.getClass().getAnnotation(AgentSpec.class);
                if (spec != null && !spec.llm().model().isEmpty()) {
                    node.put("model", spec.llm().model());
                }

                if (agent instanceof HotelAgent ha) {
                    node.put("hotelId", ha.getHotelId());
                    node.put("location", ha.getLocation());
                    node.put("rank", ha.getRank());
                    node.put("basePrice", ha.getBasePrice());
                    node.put("availableRooms", ha.getAvailableRooms());
                    node.put("totalRooms", ha.getTotalRooms());
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
     * Write hotels.json by fetching data from the Hotel Data API.
     */
    private void writeHotels(ObjectMapper mapper, Path outputDir) throws IOException {
        HotelApiClient apiClient = new HotelApiClient(API_PORT);
        List<Hotel> hotels = apiClient.fetchAllHotels();
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
                    if (role.getNegotiatingWith() != null) {
                        cMap.put("negotiatingHotel", role.getNegotiatingWith().getHotelName());
                    }
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
     * Write agents.json with agent information including LLM config.
     */
    private void writeAgents(ObjectMapper mapper, Path outputDir) throws IOException {
        List<Map<String, Object>> agentList = new ArrayList<>();

        var agents = getScenarioAgents();
        if (agents != null) {
            for (var agent : agents) {
                Map<String, Object> agentMap = new LinkedHashMap<>();
                agentMap.put("name", agent.getName());
                agentMap.put("displayName", agent.getDisplayName());
                agentMap.put("type", agent.getClass().getSimpleName());

                // Extract LLM config from @AgentSpec annotation
                AgentSpec spec = agent.getClass().getAnnotation(AgentSpec.class);
                if (spec != null) {
                    LLMSpec llm = spec.llm();
                    if (llm != null && !llm.model().isEmpty()) {
                        Map<String, Object> llmMap = new LinkedHashMap<>();
                        llmMap.put("provider", llm.provider().name());
                        llmMap.put("model", llm.model());
                        llmMap.put("temperature", llm.temperature());
                        agentMap.put("llm", llmMap);
                    }
                }

                agentList.add(agentMap);
            }
        }

        mapper.writeValue(outputDir.resolve("agents.json").toFile(), agentList);
        LOGGER.info("  Written: agents.json ({} agents)", agentList.size());
    }

    /**
     * Write system prompts generated by SCOPBridge from role annotations.
     * Each agent's prompt is written to output-data/prompts/{agentName}.txt
     */
    private void writePrompts(Path outputDir) {
        Path promptsDir = outputDir.resolve("prompts");
        try {
            Files.createDirectories(promptsDir);
            SCOPBridge bridge = SCOPBridge.getInstance();
            int count = 0;

            for (CustomerAgent customer : customerAgents) {
                CustomerRole role = customer.as(CustomerRole.class);
                if (role != null) {
                    String prompt = bridge.buildSystemPromptFromAnnotations(role);
                    Files.writeString(promptsDir.resolve(customer.getName() + ".txt"), prompt);
                    count++;
                }
            }

            for (HotelAgent hotel : hotelAgents) {
                HotelProviderRole role = hotel.as(HotelProviderRole.class);
                if (role != null) {
                    String prompt = bridge.buildSystemPromptFromAnnotations(role);
                    Files.writeString(promptsDir.resolve(hotel.getName() + ".txt"), prompt);
                    count++;
                }
            }

            LOGGER.info("  Written: prompts/ ({} agent prompts)", count);
        } catch (Exception e) {
            LOGGER.warn("Failed to write prompts: {}", e.getMessage());
        }
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
