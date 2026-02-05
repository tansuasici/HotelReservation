package hotel.reservation.data.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hotel.reservation.data.model.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for hotel data.
 * Loads data from hotel-data.json on initialization.
 */
@Repository
public class HotelRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelRepository.class);
    private final Map<String, Hotel> hotels = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initialize() {
        loadFromJson();
        LOGGER.info("HotelRepository initialized with {} hotels", hotels.size());
    }

    /**
     * Load hotels from the JSON file.
     */
    private void loadFromJson() {
        try {
            InputStream is = getClass().getResourceAsStream("/hotel-data.json");
            if (is == null) {
                LOGGER.warn("hotel-data.json not found, using empty repository");
                return;
            }

            JsonNode root = objectMapper.readTree(is);
            JsonNode hotelsNode = root.get("hotels");

            if (hotelsNode != null && hotelsNode.isArray()) {
                List<Hotel> hotelList = objectMapper.convertValue(
                    hotelsNode,
                    new TypeReference<List<Hotel>>() {}
                );
                hotelList.forEach(h -> hotels.put(h.getId(), h));
            }

            LOGGER.info("Loaded {} hotels from hotel-data.json", hotels.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load hotel data: {}", e.getMessage(), e);
        }
    }

    /**
     * Get all hotels.
     */
    public List<Hotel> findAll() {
        return new ArrayList<>(hotels.values());
    }

    /**
     * Find hotel by ID.
     */
    public Optional<Hotel> findById(String id) {
        return Optional.ofNullable(hotels.get(id));
    }

    /**
     * Find hotel by name.
     */
    public Optional<Hotel> findByName(String name) {
        return hotels.values().stream()
            .filter(h -> h.getName().equalsIgnoreCase(name))
            .findFirst();
    }

    /**
     * Find hotels by city.
     */
    public List<Hotel> findByCity(String city) {
        return hotels.values().stream()
            .filter(h -> h.getCity() != null && h.getCity().equalsIgnoreCase(city))
            .collect(Collectors.toList());
    }

    /**
     * Find hotels by minimum rank.
     */
    public List<Hotel> findByMinRank(int minRank) {
        return hotels.values().stream()
            .filter(h -> h.getRank() >= minRank)
            .collect(Collectors.toList());
    }

    /**
     * Search hotels by criteria.
     */
    public List<Hotel> search(String city, Integer minRank, Double maxPrice) {
        return hotels.values().stream()
            .filter(h -> h.matchesCriteria(city, minRank, maxPrice))
            .sorted(Comparator.comparingDouble(Hotel::getPricePerNight))
            .collect(Collectors.toList());
    }

    /**
     * Get all available cities.
     */
    public List<String> getAvailableCities() {
        return hotels.values().stream()
            .map(Hotel::getCity)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Add a new hotel.
     */
    public Hotel save(Hotel hotel) {
        if (hotel.getId() == null) {
            hotel.setId("h" + String.format("%03d", hotels.size() + 1));
        }
        hotels.put(hotel.getId(), hotel);
        LOGGER.info("Saved hotel: {}", hotel);
        return hotel;
    }

    /**
     * Update hotel availability.
     */
    public boolean updateAvailability(String hotelId, boolean available) {
        Hotel hotel = hotels.get(hotelId);
        if (hotel != null) {
            hotel.setAvailable(available);
            return true;
        }
        return false;
    }

    /**
     * Delete a hotel.
     */
    public boolean delete(String id) {
        return hotels.remove(id) != null;
    }

    /**
     * Get count of hotels.
     */
    public int count() {
        return hotels.size();
    }
}
