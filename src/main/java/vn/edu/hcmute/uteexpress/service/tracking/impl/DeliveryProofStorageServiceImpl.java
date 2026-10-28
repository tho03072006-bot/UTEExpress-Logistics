package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofStorageService;
import vn.edu.hcmute.uteexpress.service.media.*;

@Service
public class DeliveryProofStorageServiceImpl implements DeliveryProofStorageService {
    private final CloudMediaStorageService cloud;
    public DeliveryProofStorageServiceImpl(CloudMediaStorageService cloud) { this.cloud = cloud; }
    @Override public String storeProofImage(MultipartFile image) { return cloud.store(MediaPurpose.PROOF,image); }
    @Override public String storeSignature(String signatureData) {
        String prefix = "data:image/png;base64,";
        if (signatureData == null || !signatureData.startsWith(prefix)
                || signatureData.length() > prefix.length() + (MediaPurpose.SIGNATURE.maximumBytes()+2)/3*4)
            throw new IllegalArgumentException("Chữ ký cần PNG base64 hợp lệ, tối đa 2 MB.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(signatureData.substring(prefix.length())); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Dữ liệu chữ ký không hợp lệ."); }
        return cloud.storeBytes(MediaPurpose.SIGNATURE,bytes,"signature.png");
    }
}
