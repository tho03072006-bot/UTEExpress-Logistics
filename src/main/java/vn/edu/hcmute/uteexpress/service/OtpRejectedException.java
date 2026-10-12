package vn.edu.hcmute.uteexpress.service;

/** Only OTP rejections commit failed-attempt/expiry cleanup; other failures still roll back. */
public final class OtpRejectedException extends IllegalStateException {
    public OtpRejectedException(String message) { super(message); }
}
