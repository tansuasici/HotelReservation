package hotel.reservation.data.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hotel.reservation.data.model.CustomerSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for customer data.
 * Loads data from customer-data.json on initialization.
 */
public class CustomerRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerRepository.class);
    private final Map<String, CustomerSpec> customers = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void initialize() {
        loadFromJson();
        LOGGER.info("CustomerRepository initialized with {} customers", customers.size());
    }

    private void loadFromJson() {
        try {
            InputStream is = getClass().getResourceAsStream("/customer-data.json");
            if (is == null) {
                LOGGER.warn("customer-data.json not found, using empty repository");
                return;
            }

            JsonNode root = objectMapper.readTree(is);
            JsonNode customersNode = root.get("customers");

            if (customersNode != null && customersNode.isArray()) {
                List<CustomerSpec> customerList = objectMapper.convertValue(
                    customersNode,
                    new TypeReference<List<CustomerSpec>>() {}
                );
                customerList.forEach(c -> customers.put(c.getId(), c));
            }

            LOGGER.info("Loaded {} customers from customer-data.json", customers.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load customer data: {}", e.getMessage(), e);
        }
    }

    /**
     * Get all customers.
     */
    public List<CustomerSpec> findAll() {
        return new ArrayList<>(customers.values());
    }

    /**
     * Find customer by ID.
     */
    public Optional<CustomerSpec> findById(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    /**
     * Search customers by criteria.
     */
    public List<CustomerSpec> search(String city, Integer minRank, Double maxPrice) {
        return customers.values().stream()
            .filter(c -> c.matchesCriteria(city, minRank, maxPrice))
            .sorted(Comparator.comparing(CustomerSpec::getName))
            .collect(Collectors.toList());
    }

    /**
     * Get count of customers.
     */
    public int count() {
        return customers.size();
    }
}
