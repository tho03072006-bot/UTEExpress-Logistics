package vn.edu.hcmute.uteexpress.dto;

import java.io.Serializable;

/** URLs are gateway responses; QR content is encoded locally, never interpreted as HTML. */
public record MomoCheckout(Long orderId, String reference, long amount, String payUrl,
                           String qrContent) implements Serializable { }
