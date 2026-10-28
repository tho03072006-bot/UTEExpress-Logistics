package vn.edu.hcmute.uteexpress.service.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import vn.edu.hcmute.uteexpress.config.CloudinaryConfig;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.service.media.impl.*;
import vn.edu.hcmute.uteexpress.support.InMemoryCloudMediaGateway;

class CloudMediaStorageServiceTest {
    final MediaValidationService validation = new MediaValidationService();
    @AfterEach void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    static byte[] png() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        return output.toByteArray();
    }
    @Test void twoApplicationInstancesUseTheSameCloudAndPurpose() throws Exception {
        var gateway = new InMemoryCloudMediaGateway();
        var first = new CloudMediaStorageServiceImpl(gateway,validation);
        var second = new CloudMediaStorageServiceImpl(gateway,validation);
        var bytes = png(); String ref = first.storeBytes(MediaPurpose.PROOF,bytes,"proof.png");
        assertArrayEquals(bytes,second.read(ref,MediaPurpose.PROOF).bytes());
        assertThrows(CloudStorageUnavailableException.class,() -> second.read(ref,MediaPurpose.REVIEW));
        assertThrows(CloudStorageUnavailableException.class,() -> second.read(ref.replace("test-cloud","other-cloud"),MediaPurpose.PROOF));
    }
    @ParameterizedTest @ValueSource(strings={"proof.svg","../proof.png","C:\\proof.png","proof.png\n","proof.jpg","proof.exe"})
    void rejectsForgedOrUnsafeNamesBeforeNetwork(String filename) throws Exception {
        var gateway = mock(CloudMediaGateway.class); var service = new CloudMediaStorageServiceImpl(gateway,validation);
        assertThrows(IllegalArgumentException.class,() -> service.storeBytes(MediaPurpose.PROOF,png(),filename));
        verifyNoInteractions(gateway);
    }
    @Test void mimeEmptyTruncatedAndActualSizeMustAgree() throws Exception {
        var gateway = mock(CloudMediaGateway.class); var service = new CloudMediaStorageServiceImpl(gateway,validation);
        assertThrows(IllegalArgumentException.class,() -> service.store(MediaPurpose.PROOF,new MockMultipartFile("file","proof.png","text/html",png())));
        assertThrows(IllegalArgumentException.class,() -> service.storeBytes(MediaPurpose.PROOF,new byte[0],"proof.png"));
        assertThrows(IllegalArgumentException.class,() -> service.storeBytes(MediaPurpose.PROOF,Arrays.copyOf(png(),12),"proof.png"));
        var inconsistent = new MockMultipartFile("file","proof.png","image/png",png()) {
            @Override public long getSize() { return super.getSize()+1; }
        };
        assertThrows(IllegalArgumentException.class,() -> service.store(MediaPurpose.PROOF,inconsistent));
        verifyNoInteractions(gateway);
    }
    @Test void oversizedInputIsRejectedBeforeReading() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getSize()).thenReturn(11L*1024*1024);
        assertThrows(IllegalArgumentException.class,() -> validation.readBounded(file,MediaPurpose.REVIEW));
        verify(file,never()).getInputStream();
    }
    @Test void rollbackDeletesNewAssetButCommitKeepsIt() throws Exception {
        var gateway = mock(CloudMediaGateway.class); when(gateway.cloudName()).thenReturn("test-cloud");
        when(gateway.upload(any(),any())).thenAnswer(call -> metadata(call.getArgument(0),70,2,2,0));
        var service = new CloudMediaStorageServiceImpl(gateway,validation);
        TransactionSynchronizationManager.initSynchronization();
        String ref = service.storeBytes(MediaPurpose.PROOF,png(),"proof.png");
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(gateway).deleteNewUpload(CloudMediaReference.parse(ref));
        TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.initSynchronization();
        String kept = service.storeBytes(MediaPurpose.PROOF,png(),"proof.png");
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(gateway,never()).deleteNewUpload(CloudMediaReference.parse(kept));
    }
    @Test void uploadFailureCleansOnlyNewIdAndDoesNotReturnReference() throws Exception {
        var gateway = mock(CloudMediaGateway.class); when(gateway.cloudName()).thenReturn("test-cloud");
        when(gateway.upload(any(),any())).thenThrow(new CloudStorageUnavailableException());
        var service = new CloudMediaStorageServiceImpl(gateway,validation);
        assertThrows(CloudStorageUnavailableException.class,() -> service.storeBytes(MediaPurpose.PROOF,png(),"proof.png"));
        verify(gateway).deleteNewUpload(argThat(ref -> ref.purpose()==MediaPurpose.PROOF && ref.id().matches("[a-f0-9]{32}")));
    }
    @Test void providerMustConfirmPrivateAssetDimensionsAndVideoDuration() {
        var gateway = mock(CloudMediaGateway.class); when(gateway.cloudName()).thenReturn("test-cloud");
        var service = new CloudMediaStorageServiceImpl(gateway,validation);
        byte[] video = new byte[24]; System.arraycopy("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,video,4,4);
        for (double duration : new double[]{0,121,Double.NaN,Double.POSITIVE_INFINITY}) {
            doAnswer(call -> metadata(call.getArgument(0),24,1280,720,duration)).when(gateway).upload(any(),any());
            assertThrows(IllegalArgumentException.class,() -> service.storeBytes(MediaPurpose.REVIEW,video,"review.mp4"));
        }
        doAnswer(call -> metadata(call.getArgument(0),24,1280,720,120)).when(gateway).upload(any(),any());
        assertTrue(service.storeBytes(MediaPurpose.REVIEW,video,"review.mp4").endsWith(":mp4"));
        doAnswer(call -> new CloudMediaGateway.UploadResult(
                ((CloudMediaReference)call.getArgument(0)).publicId(),"video","upload","mp4",24,1280,720,30)).when(gateway).upload(any(),any());
        assertThrows(IllegalArgumentException.class,() -> service.storeBytes(MediaPurpose.REVIEW,video,"review.mp4"));
    }
    @Test void corruptDownloadAndArbitraryUrlsFailClosed() throws Exception {
        var gateway = mock(CloudMediaGateway.class); when(gateway.cloudName()).thenReturn("test-cloud");
        var service = new CloudMediaStorageServiceImpl(gateway,validation);
        for (String bad : List.of("https://evil.invalid/file.png","/uploads/a.png","file:/config/aiven-local.properties","cld1:test:PROOF:../../:png"))
            assertThrows(CloudStorageUnavailableException.class,() -> service.read(bad,MediaPurpose.PROOF));
        verify(gateway,never()).download(any());
        when(gateway.download(any())).thenReturn(new byte[]{1,2,3});
        assertThrows(CloudStorageUnavailableException.class,() -> service.read("cld1:test-cloud:PROOF:0123456789abcdef0123456789abcdef:png",MediaPurpose.PROOF));
    }
    @Test void missingCredentialsFailWithoutLocalFallbackOrLeakingSecrets() throws Exception {
        var settings = new CloudinaryConfig.Settings(true,"CHANGE_ME","CHANGE_ME","CHANGE_ME");
        assertFalse(settings.configured()); assertFalse(settings.toString().contains("CHANGE_ME"));
        var gateway = new CloudinaryMediaGatewayImpl(settings);
        assertThrows(CloudStorageUnavailableException.class,gateway::cloudName);
        var service = new CloudMediaStorageServiceImpl(gateway,validation);
        assertThrows(CloudStorageUnavailableException.class,() -> service.storeBytes(MediaPurpose.PROOF,png(),"proof.png"));
    }
    @Test void validSettingsBuildSdkWithoutContactingNetwork() {
        var settings=new CloudinaryConfig.Settings(true,"test-cloud","123456789012345","a".repeat(27));
        assertTrue(settings.configured());
        assertEquals("test-cloud",new CloudinaryMediaGatewayImpl(settings).cloudName());
        assertFalse(settings.toString().contains(settings.apiSecret()));
    }
    private CloudMediaGateway.UploadResult metadata(CloudMediaReference ref,long bytes,long width,long height,double duration) {
        return new CloudMediaGateway.UploadResult(ref.publicId(),ref.resourceType(),"authenticated",ref.format(),bytes,width,height,duration);
    }
}
