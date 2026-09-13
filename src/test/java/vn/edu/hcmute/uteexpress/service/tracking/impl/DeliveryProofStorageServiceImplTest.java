package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class DeliveryProofStorageServiceImplTest {

    @TempDir
    private Path uploadRoot;

    private DeliveryProofStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new DeliveryProofStorageServiceImpl(
                uploadRoot.toString());
    }

    @Test
    void storeProofImage_savesValidPng() throws Exception {
        byte[] png = createPng();
        MockMultipartFile image = new MockMultipartFile(
                "proofImage",
                "proof.png",
                "image/png",
                png);

        String publicPath = storageService.storeProofImage(image);

        assertTrue(publicPath.startsWith("/uploads/giao-hang/"));
        assertTrue(Files.exists(savedFile(publicPath)));
    }

    @Test
    void storeProofImage_rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "proofImage",
                "proof.txt",
                "text/plain",
                new byte[] { 1, 2, 3 });

        assertThrows(
                IllegalStateException.class,
                () -> storageService.storeProofImage(file));
    }

    @Test
    void storeSignature_savesValidCanvasData() throws Exception {
        String signatureData = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(createPng());

        String publicPath = storageService
                .storeSignature(signatureData);

        assertTrue(publicPath.startsWith("/uploads/giao-hang/"));
        assertTrue(Files.exists(savedFile(publicPath)));
    }

    @Test
    void storeSignature_rejectsMissingSignature() {
        assertThrows(
                IllegalStateException.class,
                () -> storageService.storeSignature(""));
    }

    private Path savedFile(String publicPath) {
        String fileName = Path.of(publicPath)
                .getFileName()
                .toString();

        return uploadRoot.resolve("giao-hang").resolve(fileName);
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB);

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
