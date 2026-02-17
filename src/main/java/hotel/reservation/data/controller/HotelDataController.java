package hotel.reservation.data.controller;

import hotel.reservation.data.model.CustomerSpec;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.repository.CustomerRepository;
import hotel.reservation.data.repository.HotelRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for hotel and customer data.
 * Replaces the Javalin-based HotelDataServer.
 */
@RestController
@RequestMapping("/api")
public class HotelDataController {

    private final HotelRepository hotelRepository = new HotelRepository();
    private final CustomerRepository customerRepository = new CustomerRepository();

    @PostConstruct
    public void init() {
        hotelRepository.initialize();
        customerRepository.initialize();
    }

    // ==========================================
    // HOTEL ENDPOINTS
    // ==========================================

    @GetMapping({"/hotels", "/data/hotels"})
    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    @GetMapping("/hotels/search")
    public List<Hotel> searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer minRank,
            @RequestParam(required = false) Double maxPrice) {
        return hotelRepository.search(city, minRank, maxPrice);
    }

    @GetMapping("/hotels/{id}")
    public ResponseEntity<?> getHotelById(@PathVariable String id) {
        return hotelRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Hotel not found: " + id)));
    }

    @GetMapping("/cities")
    public List<String> getCities() {
        return hotelRepository.getAvailableCities();
    }

    // ==========================================
    // CUSTOMER ENDPOINTS
    // ==========================================

    @GetMapping("/customers")
    public List<CustomerSpec> getAllCustomers() {
        return customerRepository.findAll();
    }

    @GetMapping("/customers/search")
    public List<CustomerSpec> searchCustomers(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer minRank,
            @RequestParam(required = false) Double maxPrice) {
        return customerRepository.search(city, minRank, maxPrice);
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable String id) {
        return customerRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Customer not found: " + id)));
    }

    // Expose repositories for other components
    public HotelRepository getHotelRepository() {
        return hotelRepository;
    }

    public CustomerRepository getCustomerRepository() {
        return customerRepository;
    }
}
