package vn.edu.hcmute.uteexpress.support;

import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaGateway;

/** Chỉ thuộc test classpath, không thể trở thành fallback của bản chạy thật. */
public class InMemoryCloudMediaGateway implements CloudMediaGateway {
    private final ConcurrentHashMap<String,byte[]> files = new ConcurrentHashMap<>();
    @Override public String cloudName() { return "test-cloud"; }
    @Override public UploadResult upload(CloudMediaReference ref,byte[] bytes) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image==null) throw new IllegalArgumentException("Test gateway chỉ giải mã ảnh có fixture thực.");
            files.put(ref.publicId(),bytes.clone());
            return new UploadResult(ref.publicId(),ref.resourceType(),"authenticated",ref.format(),bytes.length,image.getWidth(),image.getHeight(),0);
        } catch (java.io.IOException ex) { throw new IllegalArgumentException("Ảnh test không hợp lệ."); }
    }
    @Override public byte[] download(CloudMediaReference ref) {
        var bytes = files.get(ref.publicId());
        if (bytes==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return bytes.clone();
    }
    @Override public void deleteNewUpload(CloudMediaReference ref) { files.remove(ref.publicId()); }
    public int count() { return files.size(); }
}
