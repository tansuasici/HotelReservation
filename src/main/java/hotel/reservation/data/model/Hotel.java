package hotel.reservation.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Hotel entity representing a hotel in the system.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Hotel {
    private String id;
    private String name;
    private Location location;
    private int rank;  // 1-5 stars
    private double pricePerNight;
    private String currency;
    private List<String> amenities;
    private List<String> roomTypes;
    private double rating;
    private int reviewCount;
    private List<String> images;
    private boolean available;

    public Hotel() {}

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(double pricePerNight) { this.pricePerNight = pricePerNight; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public List<String> getRoomTypes() { return roomTypes; }
    public void setRoomTypes(List<String> roomTypes) { this.roomTypes = roomTypes; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    /**
     * Get the city from the location.
     */
    public String getCity() {
        return location != null ? location.getCity() : null;
    }

    /**
     * Check if hotel matches the given criteria.
     */
    public boolean matchesCriteria(String city, Integer minRank, Double maxPrice) {
        if (city != null && !city.equalsIgnoreCase(getCity())) {
            return false;
        }
        if (minRank != null && rank < minRank) {
            return false;
        }
        if (maxPrice != null && pricePerNight > maxPrice) {
            return false;
        }
        return available;
    }

    @Override
    public String toString() {
        return String.format("Hotel[%s] %s - %d star - $%.2f/night - %s",
            id, name, rank, pricePerNight, getCity());
    }

    // Builder class
    public static class Builder {
        private final Hotel hotel = new Hotel();

        public Builder id(String id) { hotel.id = id; return this; }
        public Builder name(String name) { hotel.name = name; return this; }
        public Builder location(Location location) { hotel.location = location; return this; }
        public Builder rank(int rank) { hotel.rank = rank; return this; }
        public Builder pricePerNight(double price) { hotel.pricePerNight = price; return this; }
        public Builder currency(String currency) { hotel.currency = currency; return this; }
        public Builder amenities(List<String> amenities) { hotel.amenities = amenities; return this; }
        public Builder roomTypes(List<String> roomTypes) { hotel.roomTypes = roomTypes; return this; }
        public Builder rating(double rating) { hotel.rating = rating; return this; }
        public Builder reviewCount(int count) { hotel.reviewCount = count; return this; }
        public Builder images(List<String> images) { hotel.images = images; return this; }
        public Builder available(boolean available) { hotel.available = available; return this; }

        public Hotel build() {
            return hotel;
        }
    }
}
