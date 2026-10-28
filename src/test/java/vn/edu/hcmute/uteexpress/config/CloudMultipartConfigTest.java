package vn.edu.hcmute.uteexpress.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

class CloudMultipartConfigTest {
    @Test void formAndMultipartLimitsAllowProofPlusBase64SignatureButStayBounded() {
        var config=new CloudinaryConfig(); var multipart=config.cloudMultipartConfig();
        assertEquals(10*1024*1024,multipart.getMaxFileSize());
        assertEquals(12*1024*1024,multipart.getMaxRequestSize());
        var factory=new TomcatServletWebServerFactory(); config.cloudFormPostLimit().customize(factory);
        var connector=new Connector();
        for (var customizer : factory.getTomcatConnectorCustomizers()) customizer.customize(connector);
        assertEquals(12*1024*1024,connector.getMaxPostSize());
    }
}
