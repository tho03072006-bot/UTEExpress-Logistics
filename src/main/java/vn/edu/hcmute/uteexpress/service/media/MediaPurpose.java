package vn.edu.hcmute.uteexpress.service.media;

public enum MediaPurpose {
    PROOF("proofs", 5 * 1024 * 1024), SIGNATURE("signatures", 2 * 1024 * 1024),
    REVIEW("reviews", 10 * 1024 * 1024), COMPLAINT("complaints", 5 * 1024 * 1024),
    RECEIPT("receipts", 5 * 1024 * 1024), AVATAR("avatars", 2 * 1024 * 1024);
    private final String folder;
    private final int maximumBytes;
    MediaPurpose(String folder, int maximumBytes) { this.folder = folder; this.maximumBytes = maximumBytes; }
    public String folder() { return folder; }
    public int maximumBytes() { return maximumBytes; }
}
