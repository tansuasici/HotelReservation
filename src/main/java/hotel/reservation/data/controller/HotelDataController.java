package hotel.reservation.data.controller;

import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.model.Room;
import hotel.reservation.data.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Hotel Data.
 * This serves as the external Hotel Data API that agents can call via WEB_SERVICE action type.
 *
 * Base URL: /api/data/hotels
 */
@RestController
@RequestMapping("/api/data/hotels")
@CrossOrigin(origins = "*")
public class HotelDataController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelDataController.class);

    @Autowired
    private HotelRepository hotelRepository;

    /**
     * GET /api/data/hotels
     * List all hotels.
     */
    @GetMapping
    public ResponseEntity<List<Hotel>> getAllHotels() {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels");
        List<Hotel> hotels = hotelRepository.findAll();
        return ResponseEntity.ok(hotels);
    }

    /**
     * GET /api/data/hotels/{id}
     * Get hotel by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Hotel> getHotelById(@PathVariable String id) {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/{}", id);
        return hotelRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/data/hotels/search
     * Search hotels by criteria.
     * Query params: city, minRank, maxPrice
     */
    @GetMapping("/search")
    public ResponseEntity<List<Hotel>> searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer minRank,
            @RequestParam(required = false) Double maxPrice) {

        LOGGER.info("[Hotel Data API] GET /api/data/hotels/search?city={}&minRank={}&maxPrice={}",
            city, minRank, maxPrice);

        List<Hotel> results = hotelRepository.search(city, minRank, maxPrice);
        LOGGER.info("[Hotel Data API] Found {} hotels matching criteria", results.size());

        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/data/hotels/city/{city}
     * Get hotels by city.
     */
    @GetMapping("/city/{city}")
    public ResponseEntity<List<Hotel>> getHotelsByCity(@PathVariable String city) {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/city/{}", city);
        List<Hotel> hotels = hotelRepository.findByCity(city);
        return ResponseEntity.ok(hotels);
    }

    /**
     * GET /api/data/hotels/{id}/rooms
     * Get available rooms for a hotel.
     * (Simplified - returns room types with prices)
     */
    @GetMapping("/{id}/rooms")
    public ResponseEntity<?> getHotelRooms(@PathVariable String id) {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/{}/rooms", id);

        return hotelRepository.findById(id)
            .map(hotel -> {
                // Generate room list from room types
                List<Room> rooms = hotel.getRoomTypes().stream()
                    .map(type -> {
                        double priceMultiplier = switch (type.toLowerCase()) {
                            case "suite", "penthouse", "villa" -> 2.0;
                            case "deluxe", "executive", "sea_view" -> 1.5;
                            default -> 1.0;
                        };
                        return new Room(
                            hotel.getId() + "-" + type,
                            type,
                            hotel.getPricePerNight() * priceMultiplier,
                            type.contains("family") ? 4 : 2,
                            true
                        );
                    })
                    .toList();
                return ResponseEntity.ok(rooms);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/data/hotels/{id}/amenities
     * Get hotel amenities.
     */
    @GetMapping("/{id}/amenities")
    public ResponseEntity<List<String>> getHotelAmenities(@PathVariable String id) {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/{}/amenities", id);

        return hotelRepository.findById(id)
            .map(hotel -> ResponseEntity.ok(hotel.getAmenities()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/data/hotels/cities
     * Get list of available cities.
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> getAvailableCities() {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/cities");
        List<String> cities = hotelRepository.getAvailableCities();
        return ResponseEntity.ok(cities);
    }

    /**
     * POST /api/data/hotels
     * Add a new hotel.
     */
    @PostMapping
    public ResponseEntity<Hotel> addHotel(@RequestBody Hotel hotel) {
        LOGGER.info("[Hotel Data API] POST /api/data/hotels - Adding: {}", hotel.getName());
        Hotel saved = hotelRepository.save(hotel);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * PUT /api/data/hotels/{id}/availability
     * Update hotel availability.
     */
    @PutMapping("/{id}/availability")
    public ResponseEntity<Map<String, Object>> updateAvailability(
            @PathVariable String id,
            @RequestParam boolean available) {

        LOGGER.info("[Hotel Data API] PUT /api/data/hotels/{}/availability?available={}", id, available);

        boolean updated = hotelRepository.updateAvailability(id, available);
        if (updated) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "hotelId", id,
                "available", available
            ));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * GET /api/data/hotels/stats
     * Get repository statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        LOGGER.info("[Hotel Data API] GET /api/data/hotels/stats");

        List<Hotel> all = hotelRepository.findAll();
        long availableCount = all.stream().filter(Hotel::isAvailable).count();

        return ResponseEntity.ok(Map.of(
            "totalHotels", all.size(),
            "availableHotels", availableCount,
            "cities", hotelRepository.getAvailableCities().size()
        ));
    }
}
