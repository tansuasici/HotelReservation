package hotel.reservation.api.dto;

import hotel.reservation.message.ReservationConfirmation;

/**
 * DTO for reservation confirmation.
 */
public class ConfirmationDTO {
    private String confirmationNumber;
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
    private String status;
    private double originalPrice;
    private double discountPercent;

    public ConfirmationDTO() {}

    public static ConfirmationDTO from(ReservationConfirmation conf) {
        ConfirmationDTO dto = new ConfirmationDTO();
        dto.confirmationNumber = conf.getConfirmationNumber();
        dto.customerId = conf.getCustomerId();
        dto.hotelId = conf.getHotelId();
        dto.hotelName = conf.getHotelName();
        dto.roomType = conf.getRoomType();
        dto.pricePerNight = conf.getPricePerNight();
        dto.totalPrice = conf.getTotalPrice();
        dto.currency = conf.getCurrency();
        dto.checkInDate = conf.getCheckInDate();
        dto.checkOutDate = conf.getCheckOutDate();
        dto.numberOfNights = conf.getNumberOfNights();
        dto.status = conf.getStatus();
        dto.originalPrice = conf.getOriginalPrice();
        dto.discountPercent = conf.getDiscountPercent();
        return dto;
    }

    // Getters and Setters
    public String getConfirmationNumber() { return confirmationNumber; }
    public void setConfirmationNumber(String confirmationNumber) { this.confirmationNumber = confirmationNumber; }

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

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }
}
