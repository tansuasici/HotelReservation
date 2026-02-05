package hotel.reservation.message;

import java.io.Serializable;

/**
 * Confirm message payload.
 * Sent by HotelAgent to confirm a reservation.
 */
public class ReservationConfirmation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String confirmationNumber;
    private String requestId;
    private String customerId;
    private String hotelId;
    private String hotelName;
    private String roomType;
    private double pricePerNight;
    private double totalPrice;
    private String currency;
    private String checkInDate;
    private String checkOutDate;
    private int numberOfNights;
    private String status;  // CONFIRMED, PENDING, CANCELLED
    private long timestamp;

    public ReservationConfirmation() {
        this.timestamp = System.currentTimeMillis();
        this.confirmationNumber = "CONF-" + System.currentTimeMillis();
        this.status = "CONFIRMED";
        this.currency = "USD";
    }

    public ReservationConfirmation(String requestId, String customerId, String hotelId, String hotelName,
                                   double pricePerNight, int numberOfNights) {
        this();
        this.requestId = requestId;
        this.customerId = customerId;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.pricePerNight = pricePerNight;
        this.numberOfNights = numberOfNights;
        this.totalPrice = pricePerNight * numberOfNights;
    }

    // Getters and Setters
    public String getConfirmationNumber() { return confirmationNumber; }
    public void setConfirmationNumber(String confirmationNumber) { this.confirmationNumber = confirmationNumber; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public double getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(double pricePerNight) { this.pricePerNight = pricePerNight; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCheckInDate() { return checkInDate; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }

    public String getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }

    public int getNumberOfNights() { return numberOfNights; }
    public void setNumberOfNights(int numberOfNights) { this.numberOfNights = numberOfNights; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * Calculate total price based on nights and price per night.
     */
    public void calculateTotalPrice() {
        this.totalPrice = this.pricePerNight * this.numberOfNights;
    }

    @Override
    public String toString() {
        return String.format("ReservationConfirmation[%s] %s at %s - $%.2f total",
            confirmationNumber, customerId, hotelName, totalPrice);
    }
}
