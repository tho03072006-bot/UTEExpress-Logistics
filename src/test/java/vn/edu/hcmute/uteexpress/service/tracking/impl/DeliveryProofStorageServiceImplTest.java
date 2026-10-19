package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.hcmute.uteexpress.service.media.*;
import vn.edu.hcmute.uteexpress.service.media.impl.CloudMediaStorageServiceImpl;
import vn.edu.hcmute.uteexpress.support.InMemoryCloudMediaGateway;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryProofStorageServiceImplTest {
    private DeliveryProofStorageServiceImpl storage;
    private CloudMediaStorageService cloud;
    private InMemoryCloudMediaGateway gateway;
    @BeforeEach void setUp() {
        gateway = new InMemoryCloudMediaGateway();
        cloud = new CloudMediaStorageServiceImpl(gateway,new MediaValidationService());
        storage = new DeliveryProofStorageServiceImpl(cloud);
    }
    @Test void storesProofOnlyAsAuthenticatedCloudReference() throws Exception {
        byte[] bytes = png(2,2);
        String ref = storage.storeProofImage(new MockMultipartFile("proofImage","proof.png","image/png",bytes));
        assertTrue(ref.startsWith("cld1:test-cloud:PROOF:"));
        assertArrayEquals(bytes,cloud.read(ref,MediaPurpose.PROOF).bytes()); assertEquals(1,gateway.count());
    }
    @Test void rejectsUnsupportedExtensionWithoutUploading() {
        assertThrows(IllegalArgumentException.class,() -> storage.storeProofImage(new MockMultipartFile("proofImage","proof.txt","text/plain",new byte[]{1,2,3})));
        assertEquals(0,gateway.count());
    }
    @Test void storesSignatureInSeparatePrivatePurpose() throws Exception {
        byte[] bytes = png(2,2);
        String ref = storage.storeSignature("data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes));
        assertTrue(ref.startsWith("cld1:test-cloud:SIGNATURE:"));
        assertArrayEquals(bytes,cloud.read(ref,MediaPurpose.SIGNATURE).bytes());
    }
    @Test void rejectsMissingInvalidOrOversizedBase64Signature() {
        for (String data : new String[]{null,"","data:image/jpeg;base64,AAAA","data:image/png;base64,not base64!", "data:image/png;base64,"+"A".repeat(3_000_000)})
            assertThrows(IllegalArgumentException.class,() -> storage.storeSignature(data));
        assertEquals(0,gateway.count());
    }
    @Test void rejectsOversizedDimensionsBeforeUploading() throws Exception {
        var file = new MockMultipartFile("proofImage","large.png","image/png",png(4097,1));
        assertThrows(IllegalArgumentException.class,() -> storage.storeProofImage(file)); assertEquals(0,gateway.count());
    }
    @Test void rejectsBlankTransparentAndUniformSignatures() throws Exception {
        for (int type : new int[]{BufferedImage.TYPE_INT_ARGB,BufferedImage.TYPE_INT_RGB}) {
            var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20,10,type),"png",output);
            assertThrows(IllegalArgumentException.class,() -> storage.storeSignature("data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray())));
        }
        assertEquals(0,gateway.count());
    }
    private byte[] png(int width,int height) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB); image.setRGB(0,0,0xFFFFFF);
            ImageIO.write(image,"png",output); return output.toByteArray();
        }
    }
}
