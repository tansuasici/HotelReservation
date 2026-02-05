package hotel.reservation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hotel.reservation.api.dto.WeatherDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Weather API.
 * Calls OpenWeatherMap API - requires OPENWEATHER_API_KEY environment variable.
 * NO MOCK DATA - API key is required.
 */
@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherController.class);

    private static final String WEATHER_API_BASE = "https://api.openweathermap.org/data/2.5";

    @Value("${weather.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /api/weather/{city}
     * Get current weather for a city from OpenWeatherMap API.
     * Requires OPENWEATHER_API_KEY environment variable.
     */
    @GetMapping("/{city}")
    public ResponseEntity<?> getWeather(@PathVariable String city) {
        LOGGER.info("[Weather API] GET /api/weather/{}", city);

        // Check if API key is configured
        String key = getApiKey();
        if (key == null || key.isEmpty()) {
            LOGGER.error("[Weather API] OPENWEATHER_API_KEY not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Weather service not configured",
                    "message", "OPENWEATHER_API_KEY environment variable is required",
                    "hint", "Set OPENWEATHER_API_KEY=your_api_key before starting the application"
                ));
        }

        try {
            // Call OpenWeatherMap API
            String url = String.format("%s/weather?q=%s&units=metric&appid=%s",
                WEATHER_API_BASE, city, key);

            LOGGER.info("[Weather API] Calling OpenWeatherMap API for city: {}", city);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            WeatherDTO dto = parseWeatherResponse(root, city);
            dto.setRecommendation(dto.generateRecommendation());

            LOGGER.info("[Weather API] Weather for {}: {}°C, {}",
                city, dto.getMain().getTemp(),
                dto.getWeather().get(0).getDescription());

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            LOGGER.error("[Weather API] Error fetching weather for {}: {}", city, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "error", "Failed to fetch weather data",
                    "city", city,
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * GET /api/weather/forecast/{city}
     * Get weather forecast for a city from OpenWeatherMap API.
     */
    @GetMapping("/forecast/{city}")
    public ResponseEntity<?> getForecast(
            @PathVariable String city,
            @RequestParam(defaultValue = "5") int days) {

        LOGGER.info("[Weather API] GET /api/weather/forecast/{}?days={}", city, days);

        String key = getApiKey();
        if (key == null || key.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Weather service not configured",
                    "message", "OPENWEATHER_API_KEY environment variable is required"
                ));
        }

        try {
            String url = String.format("%s/forecast?q=%s&units=metric&cnt=%d&appid=%s",
                WEATHER_API_BASE, city, days * 8, key);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<Map<String, Object>> forecastList = new ArrayList<>();
            JsonNode list = root.get("list");
            if (list != null && list.isArray()) {
                for (int i = 0; i < Math.min(list.size(), days); i++) {
                    JsonNode item = list.get(i * 8);
                    if (item != null) {
                        forecastList.add(Map.of(
                            "date", item.get("dt_txt").asText(),
                            "temp", item.get("main").get("temp").asDouble(),
                            "description", item.get("weather").get(0).get("description").asText()
                        ));
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "city", city,
                "country", root.has("city") ? root.get("city").get("country").asText() : "Unknown",
                "forecast", forecastList
            ));

        } catch (Exception e) {
            LOGGER.error("[Weather API] Error fetching forecast: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "error", "Failed to fetch forecast data",
                    "city", city,
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * GET /api/weather/status
     * Check if weather service is configured.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String key = getApiKey();
        boolean configured = key != null && !key.isEmpty();

        return ResponseEntity.ok(Map.of(
            "configured", configured,
            "message", configured ? "Weather API is ready" : "OPENWEATHER_API_KEY not set"
        ));
    }

    /**
     * Parse OpenWeatherMap response to WeatherDTO.
     */
    private WeatherDTO parseWeatherResponse(JsonNode root, String city) {
        WeatherDTO dto = new WeatherDTO();
        dto.setCity(city);

        if (root.has("sys") && root.get("sys").has("country")) {
            dto.setCountry(root.get("sys").get("country").asText());
        }

        if (root.has("main")) {
            WeatherDTO.Main main = new WeatherDTO.Main();
            JsonNode mainNode = root.get("main");
            main.setTemp(mainNode.get("temp").asDouble());
            main.setFeels_like(mainNode.has("feels_like") ? mainNode.get("feels_like").asDouble() : main.getTemp());
            main.setHumidity(mainNode.has("humidity") ? mainNode.get("humidity").asInt() : 0);
            main.setPressure(mainNode.has("pressure") ? mainNode.get("pressure").asInt() : 0);
            dto.setMain(main);
        }

        if (root.has("weather") && root.get("weather").isArray()) {
            List<WeatherDTO.Weather> weatherList = new ArrayList<>();
            for (JsonNode wNode : root.get("weather")) {
                WeatherDTO.Weather w = new WeatherDTO.Weather();
                w.setMain(wNode.get("main").asText());
                w.setDescription(wNode.get("description").asText());
                w.setIcon(wNode.has("icon") ? wNode.get("icon").asText() : "");
                weatherList.add(w);
            }
            dto.setWeather(weatherList);
        }

        if (root.has("wind")) {
            WeatherDTO.Wind wind = new WeatherDTO.Wind();
            JsonNode windNode = root.get("wind");
            wind.setSpeed(windNode.has("speed") ? windNode.get("speed").asDouble() : 0);
            wind.setDeg(windNode.has("deg") ? windNode.get("deg").asInt() : 0);
            dto.setWind(wind);
        }

        return dto;
    }

    /**
     * Get API key from .env file (via Spring property).
     */
    private String getApiKey() {
        return apiKey;
    }
}
