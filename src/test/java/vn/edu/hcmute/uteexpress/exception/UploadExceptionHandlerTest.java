package vn.edu.hcmute.uteexpress.exception;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.*;

class UploadExceptionHandlerTest {
    @Test void malformedAndOversizedMultipartHaveCorrectStatusesWithoutTechnicalDetails() {
        var handler=new GlobalExceptionHandler();
        var invalid=handler.uploadInvalid(new MultipartException("sensitive storage path"));
        assertEquals(400,invalid.getStatusCode().value()); assertFalse(invalid.getBody().contains("sensitive"));
        var large=handler.uploadInvalid(new MaxUploadSizeExceededException(10*1024*1024));
        assertEquals(413,large.getStatusCode().value()); assertEquals("no-store",large.getHeaders().getFirst("Cache-Control"));
    }
}
