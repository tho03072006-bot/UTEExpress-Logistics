package vn.edu.hcmute.uteexpress.service.media.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository;
import vn.edu.hcmute.uteexpress.service.media.*;

class ReviewMediaServiceImplTest {
    final ServiceReviewRepository reviews=mock(ServiceReviewRepository.class);
    final CloudMediaStorageService cloud=mock(CloudMediaStorageService.class);
    final ReviewMediaServiceImpl service=new ReviewMediaServiceImpl(reviews,cloud);
    @Test void invalidOrMissingReviewNeverDownloadsAnything() {
        assertThrows(IllegalArgumentException.class,() -> service.readPublishedMedia(-1L));
        assertThrows(IllegalArgumentException.class,() -> service.readPublishedMedia(null));
        when(reviews.findById(1L)).thenReturn(Optional.empty());
        var missing=assertThrows(ResponseStatusException.class,() -> service.readPublishedMedia(1L));
        assertEquals(404,missing.getStatusCode().value()); verifyNoInteractions(cloud);
    }
    @Test void reviewWithoutAttachmentIs404() {
        when(reviews.findById(1L)).thenReturn(Optional.of(new ServiceReview()));
        assertThrows(ResponseStatusException.class,() -> service.readPublishedMedia(1L)); verifyNoInteractions(cloud);
    }
    @Test void storedMediaTypeMustMatchActualCloudType() {
        var review=new ServiceReview(); review.setMediaType(ServiceReview.MediaType.VIDEO);
        review.setMediaPath("cld1:test-cloud:REVIEW:0123456789abcdef0123456789abcdef:mp4");
        when(reviews.findById(1L)).thenReturn(Optional.of(review));
        when(cloud.read(review.getMediaPath(),MediaPurpose.REVIEW)).thenReturn(new CloudMediaStorageService.MediaFile(new byte[1],MediaType.IMAGE_PNG));
        assertThrows(CloudStorageUnavailableException.class,() -> service.readPublishedMedia(1L));
    }
}
