package vn.edu.hcmute.uteexpress.service.media;

/** Mã tệp có cấu trúc, không phải URL hay đường dẫn ổ đĩa do client lựa chọn. */
public record CloudMediaReference(String cloudName, MediaPurpose purpose, String id, String format) {
    public CloudMediaReference {
        if (cloudName == null || !cloudName.matches("[a-zA-Z0-9_-]{1,100}") || purpose == null
                || id == null || !id.matches("[a-f0-9]{32}") || format == null
                || !format.matches("png|jpg|gif|webp|mp4|webm")) throw new IllegalArgumentException("Mã tệp cloud không hợp lệ.");
        if (purpose == MediaPurpose.SIGNATURE && !format.equals("png")) throw new IllegalArgumentException("Chữ ký phải là PNG.");
        if (purpose != MediaPurpose.REVIEW && !format.matches("png|jpg")) throw new IllegalArgumentException("Chứng từ chỉ nhận PNG/JPG.");
    }
    public String resourceType() { return format.matches("mp4|webm") ? "video" : "image"; }
    public String publicId() { return "uteexpress/" + purpose.folder() + "/" + id; }
    public String encode() { return "cld1:" + cloudName + ":" + purpose + ":" + id + ":" + format; }
    public static CloudMediaReference parse(String value) {
        if (value == null || value.length() > 255 || !value.startsWith("cld1:")) throw new IllegalArgumentException("Tệp cũ chưa được chuyển lên cloud.");
        String[] parts = value.split(":", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Mã tệp cloud không hợp lệ.");
        try { return new CloudMediaReference(parts[1], MediaPurpose.valueOf(parts[2]), parts[3], parts[4]); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Mã tệp cloud không hợp lệ."); }
    }
}
