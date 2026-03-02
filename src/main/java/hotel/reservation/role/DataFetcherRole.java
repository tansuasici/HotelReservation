package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tnsai.annotations.*;
import com.tnsai.enums.ActionType;
import com.tnsai.enums.HttpMethod;
import hotel.reservation.data.model.CustomerSpec;
import hotel.reservation.data.model.Hotel;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * Data Fetcher Role - Fetches hotel and customer data from the REST API.
 *
 * <p>SCOP Role that wraps HTTP calls to HotelDataServer endpoints.
 * WEB_SERVICE actions are routed through ActionExecutor pipeline via SCOPBridge.
 *
 * <p>Note: {@code @WebService} endpoint values are compile-time constants (Java annotation
 * limitation) and default to port 8000. The actual runtime port is determined by
 * {@link hotel.reservation.config.EnvConfig#apiPort()} and injected via the constructor.
 * Method bodies use the dynamic {@code baseUrl} field, not the annotation endpoint.
 */
@RoleSpec(
    description = "Fetches hotel and customer data from REST API",
    responsibilities = {
        @Responsibility(
            name = "HotelDataFetch",
            description = "Fetch hotel data from API",
            actions = {"fetchAllHotels", "fetchHotelById", "searchHotels"}
        ),
        @Responsibility(
            name = "CustomerDataFetch",
            description = "Fetch customer data from API",
            actions = {"fetchAllCustomers", "fetchCustomerById", "searchCustomers"}
        )
    }
)
public class DataFetcherRole extends Role {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DataFetcherRole(Agent owner, String envName, int port) {
        super(owner, envName);
        this.baseUrl = "http://localhost:" + port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==========================================
    // HOTEL ENDPOINTS
    // ==========================================

    /**
     * Fetch all hotels from the Hotel Data API.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Fetch all hotels from Hotel Data API",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/hotels",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public List<Hotel> fetchAllHotels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/hotels"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Hotel> hotels = objectMapper.readValue(response.body(),
                        new TypeReference<>() {});
                getLogger().info("Fetched {} hotels from API", hotels.size());
                return hotels;
            } else {
                getLogger().warn("API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            getLogger().error("Failed to fetch hotels from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a single hotel by ID.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Fetch a single hotel by ID from Hotel Data API",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/hotels/{id}",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public Hotel fetchHotelById(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/hotels/" +
                            URLEncoder.encode(id, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Hotel.class);
            } else {
                getLogger().warn("Hotel API returned status {} for id={}", response.statusCode(), id);
                return null;
            }
        } catch (Exception e) {
            getLogger().error("Failed to fetch hotel {}: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Search hotels by criteria via the Hotel Data API.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Search hotels by city, minimum rank, and maximum price",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/hotels/search",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public List<Hotel> searchHotels(String city, Integer minRank, Double maxPrice) {
        try {
            StringJoiner params = new StringJoiner("&");
            if (city != null && !city.isBlank()) {
                params.add("city=" + URLEncoder.encode(city, StandardCharsets.UTF_8));
            }
            if (minRank != null) {
                params.add("minRank=" + minRank);
            }
            if (maxPrice != null) {
                params.add("maxPrice=" + maxPrice);
            }

            String url = baseUrl + "/api/hotels/search";
            if (params.length() > 0) {
                url += "?" + params;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Hotel> hotels = objectMapper.readValue(response.body(),
                        new TypeReference<>() {});
                getLogger().info("Search returned {} hotels (city={}, minRank={}, maxPrice={})",
                        hotels.size(), city, minRank, maxPrice);
                return hotels;
            } else {
                getLogger().warn("Search API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            getLogger().error("Failed to search hotels from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==========================================
    // CUSTOMER ENDPOINTS
    // ==========================================

    /**
     * Fetch all customers from the Customer Data API.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Fetch all customers from Customer Data API",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/customers",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public List<CustomerSpec> fetchAllCustomers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/customers"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<CustomerSpec> customers = objectMapper.readValue(response.body(),
                        new TypeReference<>() {});
                getLogger().info("Fetched {} customers from API", customers.size());
                return customers;
            } else {
                getLogger().warn("API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            getLogger().error("Failed to fetch customers from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a single customer by ID.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Fetch a single customer by ID from Customer Data API",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/customers/{id}",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public CustomerSpec fetchCustomerById(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/customers/" +
                            URLEncoder.encode(id, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), CustomerSpec.class);
            } else {
                getLogger().warn("Customer API returned status {} for id={}", response.statusCode(), id);
                return null;
            }
        } catch (Exception e) {
            getLogger().error("Failed to fetch customer {}: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Search customers by criteria via the Customer Data API.
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Search customers by city, minimum rank, and maximum price",
        webService = @WebService(
            endpoint = "http://localhost:8000/api/customers/search",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public List<CustomerSpec> searchCustomers(String city, Integer minRank, Double maxPrice) {
        try {
            StringJoiner params = new StringJoiner("&");
            if (city != null && !city.isBlank()) {
                params.add("city=" + URLEncoder.encode(city, StandardCharsets.UTF_8));
            }
            if (minRank != null) {
                params.add("minRank=" + minRank);
            }
            if (maxPrice != null) {
                params.add("maxPrice=" + maxPrice);
            }

            String url = baseUrl + "/api/customers/search";
            if (params.length() > 0) {
                url += "?" + params;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<CustomerSpec> customers = objectMapper.readValue(response.body(),
                        new TypeReference<>() {});
                getLogger().info("Search returned {} customers (city={}, minRank={}, maxPrice={})",
                        customers.size(), city, minRank, maxPrice);
                return customers;
            } else {
                getLogger().warn("Search API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            getLogger().error("Failed to search customers from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
