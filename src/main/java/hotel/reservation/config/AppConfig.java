package hotel.reservation.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application configuration loaded from .env file.
 * Provides static access to configuration values for non-Spring components.
 */
public class AppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    private static final Dotenv dotenv;

    // Default values
    private static final String DEFAULT_HOTEL_API_BASE = "http://localhost:8080/api/data/hotels";
    private static final String DEFAULT_WEATHER_API_BASE = "https://api.openweathermap.org/data/2.5/weather";

    static {
        dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
        LOGGER.info("AppConfig loaded from .env file");
    }

    /**
     * Get Hotel Data API base URL.
     */
    public static String getHotelApiBase() {
        return dotenv.get("HOTEL_API_BASE", DEFAULT_HOTEL_API_BASE);
    }

    /**
     * Get Weather API base URL.
     */
    public static String getWeatherApiBase() {
        return dotenv.get("WEATHER_API_BASE", DEFAULT_WEATHER_API_BASE);
    }

    /**
     * Get OpenWeatherMap API key.
     */
    public static String getOpenWeatherApiKey() {
        return dotenv.get("OPENWEATHER_API_KEY", "");
    }

    /**
     * Get any configuration value with a default.
     */
    public static String get(String key, String defaultValue) {
        return dotenv.get(key, defaultValue);
    }

    /**
     * Get any configuration value.
     */
    public static String get(String key) {
        return dotenv.get(key);
    }
}
