package hotel.reservation.message;

import java.io.Serializable;

/**
 * CFP (Call For Proposals) message payload.
 * Sent by CustomerAgent to HotelAgents to request room proposals.
 */
public class RoomQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String location;      // Desired city
    private int minRank;          // Minimum star rating
    private double maxPrice;      // Maximum price per night
    private String checkInDate;   // Optional: check-in date
    private String checkOutDate;  // Optional: check-out date
    private int numberOfNights;   // Number of nights
    private long timestamp;       // Query timestamp

    public RoomQuery() {
        this.timestamp = System.currentTimeMillis();
        this.numberOfNights = 1;
    }

    public RoomQuery(String customerId, String location, int minRank, double maxPrice) {
        this();
        this.customerId = customerId;
        this.location = location;
        this.minRank = minRank;
        this.maxPrice = maxPrice;
    }

    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getMinRank() { return minRank; }
    public void setMinRank(int minRank) { this.minRank = minRank; }

    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }

    public String getCheckInDate() { return checkInDate; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }

    public String getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }

    public int getNumberOfNights() { return numberOfNights; }
    public void setNumberOfNights(int numberOfNights) { this.numberOfNights = numberOfNights; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("RoomQuery[customer=%s, location=%s, minRank=%d, maxPrice=%.2f]",
            customerId, location, minRank, maxPrice);
    }
}
