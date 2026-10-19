package vn.edu.hcmute.uteexpress.service.media.impl;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.service.media.*;

@Service
public class CloudMediaStorageServiceImpl implements CloudMediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(CloudMediaStorageServiceImpl.class);
    private final CloudMediaGateway gateway;
    private final MediaValidationService validation;
    public CloudMediaStorageServiceImpl(CloudMediaGateway gateway,MediaValidationService validation) {
        this.gateway = gateway; this.validation = validation;
    }
    @Override public String store(MediaPurpose purpose,MultipartFile file) {
        byte[] data = validation.readBounded(file,purpose);
        String format = validation.validate(purpose,data,file.getOriginalFilename());
        String declared = file.getContentType();
        String expected = mime(format).toString();
        if (declared != null && !declared.isBlank() && !declared.equalsIgnoreCase(expected)
                && !declared.equalsIgnoreCase("application/octet-stream"))
            throw new IllegalArgumentException("MIME của tệp không khớp nội dung.");
        return upload(purpose,data,format);
    }
    @Override public String storeBytes(MediaPurpose purpose,byte[] bytes,String filename) {
        return upload(purpose,bytes,validation.validate(purpose,bytes,filename));
    }
    private String upload(MediaPurpose purpose,byte[] bytes,String format) {
        var ref = new CloudMediaReference(gateway.cloudName(),purpose,UUID.randomUUID().toString().replace("-",""),format);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) discard(ref); }
            });
        }
        CloudMediaGateway.UploadResult result;
        try { result = gateway.upload(ref,bytes); }
        catch (RuntimeException ex) { discard(ref); throw ex; }
        if (result == null || !ref.publicId().equals(result.publicId()) || !ref.resourceType().equals(result.resourceType())
                || !"authenticated".equals(result.deliveryType()) || !format.equals(result.format())
                || result.bytes() < 1 || result.bytes() > purpose.maximumBytes()
                || result.width()<1 || result.height()<1 || result.width()>MediaValidationService.MAX_IMAGE_DIMENSION
                || result.height()>MediaValidationService.MAX_IMAGE_DIMENSION
                || result.width()*result.height()>MediaValidationService.MAX_IMAGE_PIXELS || !Double.isFinite(result.duration())
                || (ref.resourceType().equals("video") && (result.duration()<=0 || result.duration()>MediaValidationService.MAX_VIDEO_SECONDS))) {
            discard(ref);
            throw new IllegalArgumentException("Cloud không xác nhận được tệp hợp lệ; ảnh tối đa 12 MP, video tối đa 120 giây.");
        }
        return ref.encode();
    }
    @Override public MediaFile read(String value,MediaPurpose expected) {
        CloudMediaReference ref;
        try { ref = CloudMediaReference.parse(value); }
        catch (IllegalArgumentException ex) { throw new CloudStorageUnavailableException(); }
        if (ref.purpose()!=expected || !ref.cloudName().equals(gateway.cloudName())) throw new CloudStorageUnavailableException();
        byte[] data = gateway.download(ref);
        try { validation.validate(expected,data,"asset."+ref.format()); }
        catch (IllegalArgumentException ex) { throw new CloudStorageUnavailableException(); }
        return new MediaFile(data,mime(ref.format()));
    }
    @Override public boolean isReference(String value,MediaPurpose expected) {
        try { return CloudMediaReference.parse(value).purpose()==expected; }
        catch (IllegalArgumentException ex) { return false; }
    }
    private void discard(CloudMediaReference ref) {
        try { gateway.deleteNewUpload(ref); }
        catch (RuntimeException ex) { log.warn("Không dọn được tệp mới chưa commit; cần đối chiếu kho cloud. Không ghi URL ký hoặc khóa."); }
    }
    public static MediaType mime(String format) {
        return MediaType.parseMediaType(switch (format) {
            case "jpg" -> "image/jpeg"; case "png" -> "image/png"; case "gif" -> "image/gif";
            case "webp" -> "image/webp"; case "mp4" -> "video/mp4"; case "webm" -> "video/webm";
            default -> throw new IllegalArgumentException("Định dạng không hợp lệ.");
        });
    }
}
