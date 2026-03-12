package hotel.reservation.role;

import ai.scop.core.Agent;
import ai.scop.core.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tnsai.actions.ActionParams;
import com.tnsai.annotations.ActionSpec;
import com.tnsai.annotations.BeforeActionSpec;
import com.tnsai.annotations.Responsibility;
import com.tnsai.annotations.RoleSpec;
import com.tnsai.annotations.WebService;
import com.tnsai.enums.ActionType;
import com.tnsai.enums.HttpMethod;
import hotel.reservation.config.EnvConfig;
import hotel.reservation.data.model.CustomerSpec;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.model.WeatherInfo;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

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
        ),
        @Responsibility(
            name = "WeatherDataFetch",
            description = "Fetch weather data from OpenWeather API",
            actions = {"fetchWeather"}
        )
    }
)
public class DataFetcherRole extends Role {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, WeatherInfo> weatherCache = new ConcurrentHashMap<>();

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

    // ==========================================
    // WEATHER ENDPOINT
    // ==========================================

    /**
     * Security hook: inject API credentials before weather fetch.
     * Credentials are resolved from environment and injected into ActionParams,
     * ensuring they never appear in LLM-visible contexts.
     */
    @BeforeActionSpec("fetchWeather")
    private ActionParams beforeFetchWeather(ActionParams params) {
        String apiKey = EnvConfig.openWeatherApiKey();
        String weatherBase = EnvConfig.weatherApiBase();
        params.set("apiKey", apiKey);
        params.set("weatherBase", weatherBase);
        return params;
    }

    /**
     * Fetch current weather for a city from OpenWeather API.
     * Results are cached per city to avoid redundant API calls within a simulation run.
     * Returns null if API key is not configured or the API call fails (graceful skip).
     * Credentials are injected via {@code @BeforeActionSpec} hook (security-by-construction).
     */
    @ActionSpec(
        type = ActionType.WEB_SERVICE,
        description = "Fetch current weather for a city from OpenWeather API",
        webService = @WebService(
            endpoint = "https://api.openweathermap.org/data/2.5/weather",
            method = HttpMethod.GET,
            timeout = 5000
        )
    )
    public WeatherInfo fetchWeather(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }

        // Check cache first
        WeatherInfo cached = weatherCache.get(city.toLowerCase());
        if (cached != null) {
            getLogger().debug("Weather cache hit for {}", city);
            return cached;
        }

        // Invoke credential injection hook
        ActionParams params = new ActionParams(new java.util.HashMap<>(), "fetchWeather");
        params = beforeFetchWeather(params);
        String apiKey = params.getString("apiKey");
        String weatherBase = params.getString("weatherBase");

        if (apiKey == null || apiKey.isEmpty() || "your_openweather_api_key_here".equals(apiKey)) {
            getLogger().debug("OpenWeather API key not configured — skipping weather fetch");
            return null;
        }

        try {
            String url = String.format("%s?q=%s&appid=%s&units=metric",
                weatherBase,
                URLEncoder.encode(city, StandardCharsets.UTF_8),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);

                @SuppressWarnings("unchecked")
                Map<String, Object> main = (Map<String, Object>) json.get("main");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> weatherList = (List<Map<String, Object>>) json.get("weather");
                @SuppressWarnings("unchecked")
                Map<String, Object> wind = (Map<String, Object>) json.get("wind");

                String condition = weatherList != null && !weatherList.isEmpty()
                    ? (String) weatherList.get(0).get("main") : "Unknown";
                String description = weatherList != null && !weatherList.isEmpty()
                    ? (String) weatherList.get(0).get("description") : "";

                double temp = main != null ? ((Number) main.get("temp")).doubleValue() : 0;
                double feelsLike = main != null ? ((Number) main.get("feels_like")).doubleValue() : 0;
                int humidity = main != null ? ((Number) main.get("humidity")).intValue() : 0;
                double windSpeed = wind != null ? ((Number) wind.get("speed")).doubleValue() : 0;

                WeatherInfo weather = new WeatherInfo(city, condition, description,
                    temp, feelsLike, humidity, windSpeed);
                weatherCache.put(city.toLowerCase(), weather);

                getLogger().info("Weather for {}: {} ({}), {}°C", city, condition, description, String.format("%.1f", temp));
                return weather;
            } else {
                getLogger().warn("Weather API returned status {} for city={}", response.statusCode(), city);
                return null;
            }
        } catch (Exception e) {
            getLogger().error("Failed to fetch weather for {}: {}", city, e.getMessage());
            return null;
        }
    }
}
