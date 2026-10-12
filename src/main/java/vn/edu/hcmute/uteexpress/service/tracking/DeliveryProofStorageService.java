package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.web.multipart.MultipartFile;

public interface DeliveryProofStorageService {

    String storeProofImage(MultipartFile proofImage);

    String storeSignature(String signatureData);

    /** Remove only a file previously stored in the delivery-proof folder. */
    void deleteStored(String storedPath);
}
