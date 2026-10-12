package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test void rejectsFakeImageContentAndMismatchBetweenExtensionAndRealFormat() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("proofImage", "fake.png", "image/png",
                "<html>not an image</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> storageService.storeProofImage(fake));
        MockMultipartFile wrongExtension = new MockMultipartFile("proofImage", "wrong.jpg", "image/jpeg", createPng());
        assertThrows(IllegalStateException.class, () -> storageService.storeProofImage(wrongExtension));
    }

    @Test void rejectsImageWhoseDimensionsExceedTheLimitBeforeSaving() throws Exception {
        BufferedImage image = new BufferedImage(10001, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            MockMultipartFile huge = new MockMultipartFile("proofImage", "huge.png", "image/png", output.toByteArray());
            assertThrows(IllegalStateException.class, () -> storageService.storeProofImage(huge));
        }
        try (var files = Files.walk(uploadRoot)) {
            assertFalse(files.anyMatch(Files::isRegularFile));
        }
    }

    @Test void signatureMustContainAnActualPngAndStayWithinEncodedSizeLimit() {
        String fake = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThrows(IllegalStateException.class, () -> storageService.storeSignature(fake));
        String oversized = "data:image/png;base64," + "a".repeat(3 * 1024 * 1024);
        assertThrows(IllegalStateException.class, () -> storageService.storeSignature(oversized));
    }

    @Test void deleteStoredIsConfinedToFilesInTheProofFolder() throws Exception {
        String stored = storageService.storeProofImage(new MockMultipartFile(
                "proofImage", "proof.png", "image/png", createPng()));
        Path sibling = uploadRoot.resolve("must-stay.png");
        Files.write(sibling, createPng());
        storageService.deleteStored("/uploads/giao-hang/../must-stay.png");
        storageService.deleteStored("/uploads/danh-gia/must-stay.png");
        assertTrue(Files.exists(sibling));
        storageService.deleteStored(stored);
        assertFalse(Files.exists(savedFile(stored)));
        assertTrue(Files.exists(sibling));
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
