package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity nguoi dung chung cho ca he thong (Guest khong can tai khoan nen khong
 * co entity rieng). Field "role" quyet dinh la USER / SHIPPER / MANAGER / ADMIN.
 * KHONG dung Lombok (theo quy uoc mon hoc) - vien tay constructor/getter/setter.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // luon luu ban da ma hoa BCrypt, khong bao gio luu plain text

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = false; // true sau khi xac thuc OTP dang ky thanh cong

    @Column(name = "otp_code", length = 10)
    private String otpCode; // ma OTP tam thoi cho dang ky / quen mat khau

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        USER, SHIPPER, MANAGER, ADMIN
    }

    public AppUser() {
    }

    public AppUser(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public LocalDateTime getOtpExpiry() {
        return otpExpiry;
    }

    public void setOtpExpiry(LocalDateTime otpExpiry) {
        this.otpExpiry = otpExpiry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
