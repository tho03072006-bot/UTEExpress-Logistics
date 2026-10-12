package vn.edu.hcmute.uteexpress.dto;

public record MomoPaymentResult(Long orderId, State state, String message) {
    public enum State { PAID, PENDING, FAILED }
}
