package hotel.reservation.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/**
 * Customer specification representing a customer's search criteria.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerSpec {

    private String id;
    private String name;
    private String desiredLocation;
    private int desiredRank;
    private double maxPrice;
    private int numberOfRooms = 1;
    private List<String> amenities = Collections.emptyList();

    public CustomerSpec() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesiredLocation() { return desiredLocation; }
    public void setDesiredLocation(String desiredLocation) { this.desiredLocation = desiredLocation; }

    public int getDesiredRank() { return desiredRank; }
    public void setDesiredRank(int desiredRank) { this.desiredRank = desiredRank; }

    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }

    public int getNumberOfRooms() { return numberOfRooms; }
    public void setNumberOfRooms(int numberOfRooms) { this.numberOfRooms = numberOfRooms; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities != null ? amenities : Collections.emptyList(); }

    /**
     * Check if this customer's criteria match the given filters.
     */
    public boolean matchesCriteria(String city, Integer minRank, Double maxPrice) {
        if (city != null && !city.equalsIgnoreCase(desiredLocation)) {
            return false;
        }
        if (minRank != null && desiredRank < minRank) {
            return false;
        }
        if (maxPrice != null && this.maxPrice > maxPrice) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("CustomerSpec[%s] %s - %d★ %s (max $%.2f, rooms=%d, amenities=%s)",
            id, name, desiredRank, desiredLocation, maxPrice, numberOfRooms, amenities);
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CustomerSpec spec = new CustomerSpec();

        public Builder id(String id) { spec.id = id; return this; }
        public Builder name(String name) { spec.name = name; return this; }
        public Builder desiredLocation(String loc) { spec.desiredLocation = loc; return this; }
        public Builder desiredRank(int rank) { spec.desiredRank = rank; return this; }
        public Builder maxPrice(double price) { spec.maxPrice = price; return this; }
        public Builder numberOfRooms(int rooms) { spec.numberOfRooms = rooms; return this; }
        public Builder amenities(List<String> amenities) { spec.amenities = amenities != null ? amenities : Collections.emptyList(); return this; }

        public CustomerSpec build() { return spec; }
    }
}
