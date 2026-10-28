package vn.edu.hcmute.uteexpress.service.media;

/** Adapter mạng riêng để test không bao giờ tải tệp thật lên cloud. */
public interface CloudMediaGateway {
    String cloudName();
    UploadResult upload(CloudMediaReference reference, byte[] bytes);
    byte[] download(CloudMediaReference reference);
    void deleteNewUpload(CloudMediaReference reference);
    record UploadResult(String publicId, String resourceType, String deliveryType, String format,
                        long bytes, long width, long height, double duration) { }
}
