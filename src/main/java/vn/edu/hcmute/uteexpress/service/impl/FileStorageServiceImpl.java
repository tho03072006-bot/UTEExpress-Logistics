package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import vn.edu.hcmute.uteexpress.service.FileStorageService;
import vn.edu.hcmute.uteexpress.service.media.*;

/** Tệp đánh giá chỉ lưu ở Cloudinary chung; không ghi vào filesystem của máy chạy app. */
@Service
public class FileStorageServiceImpl implements FileStorageService {
    private final CloudMediaStorageService cloud;
    private final MediaValidationService validation;
    public FileStorageServiceImpl(CloudMediaStorageService cloud,MediaValidationService validation) {
        this.cloud = cloud; this.validation = validation;
    }
    @Override public String storeReviewMedia(MultipartFile file) { return cloud.store(MediaPurpose.REVIEW,file); }
    @Override public ServiceReview.MediaType detectMediaType(MultipartFile file) {
        byte[] bytes = validation.readBounded(file,MediaPurpose.REVIEW);
        String format = validation.validate(MediaPurpose.REVIEW,bytes,file.getOriginalFilename());
        return format.matches("mp4|webm") ? ServiceReview.MediaType.VIDEO : ServiceReview.MediaType.IMAGE;
    }
}
