package hotel.reservation.df;

import ai.scop.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Directory Facilitator Environment.
 * Acts as a yellow pages service for hotel agents.
 * Hotel Agents register their services here, and Customer Agents search for available hotels.
 */
public class DirectoryFacilitator extends Environment {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryFacilitator.class);

    /**
     * Registry of hotel agents.
     * Key: agentId, Value: DFEntry
     */
    private final Map<String, DFEntry> registry = new ConcurrentHashMap<>();

    public DirectoryFacilitator(String name) {
        super(name);
    }

    @Override
    protected void setup() {
        getLogger().info("[DF] Directory Facilitator '{}' initialized", getName());
    }

    /**
     * Register a hotel agent with the DF.
     *
     * @param entry The DF entry containing hotel agent information
     * @return true if registration successful
     */
    public synchronized boolean register(DFEntry entry) {
        if (entry == null || entry.getAgentId() == null) {
            getLogger().warn("[DF] Invalid registration attempt - null entry or agentId");
            return false;
        }

        registry.put(entry.getAgentId(), entry);
        getLogger().info("[DF] Registered: {} - {} ({} star, ${}/night) in {}",
            entry.getAgentId(),
            entry.getHotelName(),
            entry.getRank(),
            entry.getBasePrice(),
            entry.getLocation());

        return true;
    }

    /**
     * Deregister a hotel agent from the DF.
     *
     * @param agentId The agent ID to deregister
     * @return true if deregistration successful
     */
    public synchronized boolean deregister(String agentId) {
        DFEntry removed = registry.remove(agentId);
        if (getLogger() != null) {
            getLogger().info("[DF] Deregistered: {} - {}", agentId, removed.getHotelName());
            return true;
        }
        return false;
    }

    /**
     * Search for hotel agents matching the criteria.
     *
     * @param location Desired city (null for any)
     * @param minRank Minimum star rating (null for any)
     * @param maxPrice Maximum price per night (null for any)
     * @return List of matching DF entries
     */
    public List<DFEntry> search(String location, Integer minRank, Double maxPrice) {
        getLogger().info("[DF] Search request: location={}, minRank={}, maxPrice={}",
            location, minRank, maxPrice);

        List<DFEntry> results = registry.values().stream()
            .filter(entry -> entry.matches(location, minRank, maxPrice))
            .sorted(Comparator.comparingDouble(DFEntry::getBasePrice))
            .collect(Collectors.toList());

        getLogger().info("[DF] Search found {} matching hotels", results.size());
        return results;
    }

    /**
     * Get all registered entries.
     */
    public List<DFEntry> getAllEntries() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Get entry by agent ID.
     */
    public Optional<DFEntry> getEntry(String agentId) {
        return Optional.ofNullable(registry.get(agentId));
    }

    /**
     * Get all agent IDs matching criteria.
     */
    public List<String> searchAgentIds(String location, Integer minRank, Double maxPrice) {
        return search(location, minRank, maxPrice).stream()
            .map(DFEntry::getAgentId)
            .collect(Collectors.toList());
    }

    /**
     * Update agent availability.
     */
    public boolean updateAvailability(String agentId, boolean available) {
        DFEntry entry = registry.get(agentId);
        if (entry != null) {
            entry.setAvailable(available);
            getLogger().info("[DF] Updated availability for {}: {}", agentId, available);
            return true;
        }
        return false;
    }

    /**
     * Get count of registered agents.
     */
    public int getRegisteredCount() {
        return registry.size();
    }

    /**
     * Get count of available agents.
     */
    public int getAvailableCount() {
        return (int) registry.values().stream()
            .filter(DFEntry::isAvailable)
            .count();
    }

    /**
     * Get available locations (cities).
     */
    public Set<String> getAvailableLocations() {
        return registry.values().stream()
            .map(DFEntry::getLocation)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return String.format("DirectoryFacilitator[%s] - %d registered agents",
            getName(), registry.size());
    }
}
