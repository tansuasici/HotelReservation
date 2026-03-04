package hotel.reservation.data.model;

/**
 * Weather information for a city, fetched from OpenWeather API.
 * Used by pricing and evaluation logic to adjust decisions based on weather conditions.
 */
public class WeatherInfo {

    private String city;
    private String condition;      // "Clear", "Rain", "Clouds", "Snow", etc.
    private String description;    // "light rain", "overcast clouds", etc.
    private double temperature;    // Celsius
    private double feelsLike;      // Celsius
    private int humidity;          // percentage
    private double windSpeed;      // m/s

    public WeatherInfo() {}

    public WeatherInfo(String city, String condition, String description,
                       double temperature, double feelsLike, int humidity, double windSpeed) {
        this.city = city;
        this.condition = condition;
        this.description = description;
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
    }

    /**
     * Whether weather conditions are favorable for tourism.
     * Clear and Clouds are favorable; Rain, Snow, Thunderstorm, Drizzle are not.
     */
    public boolean isFavorable() {
        if (condition == null) return true;
        return switch (condition) {
            case "Rain", "Snow", "Thunderstorm", "Drizzle" -> false;
            default -> true; // Clear, Clouds, Mist, Haze, etc.
        };
    }

    // Getters and setters

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getFeelsLike() { return feelsLike; }
    public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    @Override
    public String toString() {
        return String.format("WeatherInfo{city='%s', condition='%s', description='%s', temp=%.1f°C, humidity=%d%%}",
            city, condition, description, temperature, humidity);
    }
}
