package hotel.reservation.data.model;

/**
 * Contact information for a hotel.
 */
public class ContactInfo {
    private String phone;
    private String email;

    public ContactInfo() {}

    public ContactInfo(String phone, String email) {
        this.phone = phone;
        this.email = email;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return String.format("Phone: %s, Email: %s", phone, email);
    }
}
