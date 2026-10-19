package vn.edu.hcmute.uteexpress.service.payment;

public interface MomoStartupService {
    record Snapshot(String state, String message) { }
    Snapshot snapshot();
}
