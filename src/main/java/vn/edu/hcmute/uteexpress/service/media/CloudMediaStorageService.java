package vn.edu.hcmute.uteexpress.service.media;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface CloudMediaStorageService {
    String store(MediaPurpose purpose, MultipartFile file);
    String storeBytes(MediaPurpose purpose, byte[] bytes, String filename);
    MediaFile read(String reference, MediaPurpose purpose);
    boolean isReference(String value, MediaPurpose purpose);
    record MediaFile(byte[] bytes, MediaType type) { }
}
