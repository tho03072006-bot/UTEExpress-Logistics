package vn.edu.hcmute.uteexpress.service.media;

public interface ReviewMediaService {
    CloudMediaStorageService.MediaFile readPublishedMedia(Long reviewId);
}
