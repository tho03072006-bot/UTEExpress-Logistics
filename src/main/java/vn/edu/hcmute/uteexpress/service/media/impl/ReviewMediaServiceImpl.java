package vn.edu.hcmute.uteexpress.service.media.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository;
import vn.edu.hcmute.uteexpress.service.media.*;

@Service
@Transactional(readOnly=true)
public class ReviewMediaServiceImpl implements ReviewMediaService {
    private final ServiceReviewRepository reviews;
    private final CloudMediaStorageService cloud;
    public ReviewMediaServiceImpl(ServiceReviewRepository reviews,CloudMediaStorageService cloud) {
        this.reviews=reviews; this.cloud=cloud;
    }
    @Override public CloudMediaStorageService.MediaFile readPublishedMedia(Long reviewId) {
        if (reviewId==null || reviewId<=0) throw new IllegalArgumentException("ID đánh giá không hợp lệ.");
        var review=reviews.findById(reviewId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!review.hasMedia()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var file=cloud.read(review.getMediaPath(),MediaPurpose.REVIEW);
        if (file.type().getType().equals("video")!=(review.getMediaType()==ServiceReview.MediaType.VIDEO))
            throw new CloudStorageUnavailableException();
        return file;
    }
}
