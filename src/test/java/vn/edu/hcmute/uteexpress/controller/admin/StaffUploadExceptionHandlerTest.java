package vn.edu.hcmute.uteexpress.controller.admin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.multipart.*;
import org.springframework.web.server.ResponseStatusException;

class StaffUploadExceptionHandlerTest {
    final StaffExceptionHandler handler=new StaffExceptionHandler();
    @Test void oversizedUploadReturns413InsteadOf500() {
        var response=new MockHttpServletResponse(); var model=new ExtendedModelMap();
        assertEquals("admin/error",handler.handleUpload(new MaxUploadSizeExceededException(5*1024*1024),response,model));
        assertEquals(413,response.getStatus());
    }
    @Test void missingOrMalformedPartReturns400WithoutLeakingException() {
        var response=new MockHttpServletResponse(); var model=new ExtendedModelMap();
        handler.handleUpload(new MultipartException("sensitive provider text"),response,model);
        assertEquals(400,response.getStatus()); assertFalse(model.toString().contains("sensitive"));
    }
    @Test void missingRemoteFileRemains404AndDoesNotExposeProviderReason() {
        var response=new MockHttpServletResponse(); var model=new ExtendedModelMap();
        handler.handleStatus(new ResponseStatusException(HttpStatus.NOT_FOUND,"sensitive signed URL"),response,model);
        assertEquals(404,response.getStatus()); assertFalse(model.toString().contains("sensitive"));
    }
}
