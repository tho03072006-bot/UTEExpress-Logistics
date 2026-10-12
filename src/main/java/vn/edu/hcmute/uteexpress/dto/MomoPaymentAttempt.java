package vn.edu.hcmute.uteexpress.dto;

/** Immutable amount and correlation snapshot persisted before calling the gateway. */
public record MomoPaymentAttempt(Long orderId, String reference, long amount, String orderInfo, boolean paid) { }
