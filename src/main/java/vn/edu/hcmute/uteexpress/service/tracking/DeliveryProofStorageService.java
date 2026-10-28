package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.web.multipart.MultipartFile;

public interface DeliveryProofStorageService {

    String storeProofImage(MultipartFile proofImage);

    String storeSignature(String signatureData);
}