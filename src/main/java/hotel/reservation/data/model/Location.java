package hotel.reservation.data.model;

/**
 * Location information for a hotel.
 */
public class Location {
    private String city;
    private String country;
    private String district;
    private Coordinates coordinates;

    public Location() {}

    public Location(String city, String country, String district) {
        this.city = city;
        this.country = country;
        this.district = district;
    }

    public static Location of(String city, String country, String district) {
        return new Location(city, country, district);
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public Coordinates getCoordinates() { return coordinates; }
    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }

    @Override
    public String toString() {
        return String.format("%s, %s, %s", district, city, country);
    }
}
