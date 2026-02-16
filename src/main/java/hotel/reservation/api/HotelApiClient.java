package hotel.reservation.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tnsai.annotations.Action;
import com.tnsai.annotations.WebService;
import com.tnsai.enums.ActionType;
import com.tnsai.enums.HttpMethod;
import com.tnsai.enums.role.ParamType;
import hotel.reservation.data.model.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * HTTP client for the Hotel Data API, annotated with TnsAI {@code @Action} +
 * {@code @WebService} to demonstrate the {@code ActionType.WEB_SERVICE} pattern.
 *
 * <p>Uses JDK built-in {@link HttpClient} (zero external dependency).
 *
 * <p>This class is a standalone client; the annotations serve as machine-readable
 * metadata for the TnsAI framework (endpoint, method, timeout, param style).
 */
public class HotelApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelApiClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HotelApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public HotelApiClient(int port) {
        this("http://localhost:" + port);
    }

    /**
     * Fetch all hotels from the Hotel Data API.
     */
    @Action(
        type = ActionType.WEB_SERVICE,
        description = "Fetch all hotels from Hotel Data API",
        webService = @WebService(
            endpoint = "http://localhost:7070/api/hotels",
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
                LOGGER.info("Fetched {} hotels from API", hotels.size());
                return hotels;
            } else {
                LOGGER.warn("API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch hotels from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Search hotels by criteria via the Hotel Data API.
     */
    @Action(
        type = ActionType.WEB_SERVICE,
        description = "Search hotels by criteria from Hotel Data API",
        webService = @WebService(
            endpoint = "http://localhost:7070/api/hotels/search",
            method = HttpMethod.GET,
            paramType = ParamType.QUERY,
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
                LOGGER.info("Search returned {} hotels (city={}, minRank={}, maxPrice={})",
                        hotels.size(), city, minRank, maxPrice);
                return hotels;
            } else {
                LOGGER.warn("Search API returned status {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to search hotels from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a single hotel by ID.
     */
    @Action(
        type = ActionType.WEB_SERVICE,
        description = "Fetch a single hotel by ID from Hotel Data API",
        webService = @WebService(
            endpoint = "http://localhost:7070/api/hotels/{id}",
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
                LOGGER.warn("Hotel API returned status {} for id={}", response.statusCode(), id);
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch hotel {}: {}", id, e.getMessage());
            return null;
        }
    }
}
