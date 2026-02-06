package hotel.reservation.message;

import java.io.Serializable;

/**
 * Accept message payload.
 * Sent by CustomerAgent to accept a hotel's proposal.
 */
public class ReservationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String customerId;
    private String customerName;
    private String proposalId;
    private String hotelId;
    private String checkInDate;
    private String checkOutDate;
    private int numberOfNights;
    private int numberOfGuests;
    private String specialRequests;
    private double negotiatedPrice;  // Negotiated price (0 if not negotiated)
    private long timestamp;

    public ReservationRequest() {
        this.timestamp = System.currentTimeMillis();
        this.requestId = "req-" + System.currentTimeMillis();
        this.numberOfNights = 1;
        this.numberOfGuests = 1;
    }

    public ReservationRequest(String customerId, String proposalId, String hotelId) {
        this();
        this.customerId = customerId;
        this.proposalId = proposalId;
        this.hotelId = hotelId;
    }

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getCheckInDate() { return checkInDate; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }

    public String getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }

    public int getNumberOfNights() { return numberOfNights; }
    public void setNumberOfNights(int numberOfNights) { this.numberOfNights = numberOfNights; }

    public int getNumberOfGuests() { return numberOfGuests; }
    public void setNumberOfGuests(int numberOfGuests) { this.numberOfGuests = numberOfGuests; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public double getNegotiatedPrice() { return negotiatedPrice; }
    public void setNegotiatedPrice(double negotiatedPrice) { this.negotiatedPrice = negotiatedPrice; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("ReservationRequest[%s] customer=%s, proposal=%s",
            requestId, customerId, proposalId);
    }
}
