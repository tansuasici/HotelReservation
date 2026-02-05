package hotel.reservation.data.model;

/**
 * Geographic coordinates for a location.
 */
public class Coordinates {
    private double lat;
    private double lng;

    public Coordinates() {}

    public Coordinates(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", lat, lng);
    }
}
