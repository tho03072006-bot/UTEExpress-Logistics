package vn.edu.hcmute.uteexpress.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import static org.junit.jupiter.api.Assertions.*;

class MediaValidatorTest {
    @Test void acceptsActualPngAndRejectsHtmlOrMismatchedExtension() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        assertEquals(ServiceReview.MediaType.IMAGE, MediaValidator.validate(new MockMultipartFile("media", "real.png", "image/png", png), 10000));
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "fake.png", "image/png", "<html>fake</html>".getBytes()), 10000));
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "fake.jpg", "image/jpeg", png), 10000));
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "real.png", "image/png", png), 1));
    }
    @Test void rejectsTruncatedVideoAndNonVideoData() {
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "fake.mp4", "video/mp4", "<html>fake</html>".getBytes()), 10000));
        byte[] truncated = ByteBuffer.allocate(24).putInt(100).put("ftyp".getBytes(StandardCharsets.US_ASCII)).array();
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "bad.mp4", "video/mp4", truncated), 10000));
    }
    @Test void rejectsOversizedDecodedImageBeforePixelAllocation() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        ByteBuffer.wrap(png, 16, 8).putInt(100000).putInt(100000);
        assertThrows(IllegalStateException.class, () -> MediaValidator.validate(new MockMultipartFile("media", "bomb.png", "image/png", png), 10000));
    }
}
