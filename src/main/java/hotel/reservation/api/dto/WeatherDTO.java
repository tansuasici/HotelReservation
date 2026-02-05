package hotel.reservation.api.dto;

import java.util.List;

/**
 * DTO for weather response from OpenWeatherMap API.
 */
public class WeatherDTO {
    private String city;
    private String country;
    private Main main;
    private List<Weather> weather;
    private Wind wind;
    private String recommendation;

    public WeatherDTO() {}

    // Nested classes matching OpenWeatherMap response
    public static class Main {
        private double temp;
        private double feels_like;
        private int humidity;
        private int pressure;

        public double getTemp() { return temp; }
        public void setTemp(double temp) { this.temp = temp; }

        public double getFeels_like() { return feels_like; }
        public void setFeels_like(double feels_like) { this.feels_like = feels_like; }

        public int getHumidity() { return humidity; }
        public void setHumidity(int humidity) { this.humidity = humidity; }

        public int getPressure() { return pressure; }
        public void setPressure(int pressure) { this.pressure = pressure; }
    }

    public static class Weather {
        private String main;
        private String description;
        private String icon;

        public String getMain() { return main; }
        public void setMain(String main) { this.main = main; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
    }

    public static class Wind {
        private double speed;
        private int deg;

        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }

        public int getDeg() { return deg; }
        public void setDeg(int deg) { this.deg = deg; }
    }

    // Getters and Setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Main getMain() { return main; }
    public void setMain(Main main) { this.main = main; }

    public List<Weather> getWeather() { return weather; }
    public void setWeather(List<Weather> weather) { this.weather = weather; }

    public Wind getWind() { return wind; }
    public void setWind(Wind wind) { this.wind = wind; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    /**
     * Generate hotel recommendation based on weather.
     */
    public String generateRecommendation() {
        if (main == null) return "Weather data not available";

        StringBuilder rec = new StringBuilder();
        double temp = main.getTemp();

        if (temp > 25) {
            rec.append("Hot weather! Hotels with pool are recommended. ");
        } else if (temp < 10) {
            rec.append("Cold weather! Hotels with spa/sauna are recommended. ");
        } else {
            rec.append("Pleasant weather for sightseeing! ");
        }

        if (weather != null && !weather.isEmpty()) {
            String desc = weather.get(0).getDescription();
            if (desc != null && desc.contains("rain")) {
                rec.append("Rain expected - consider hotels with indoor activities.");
            }
        }

        return rec.toString().trim();
    }
}
