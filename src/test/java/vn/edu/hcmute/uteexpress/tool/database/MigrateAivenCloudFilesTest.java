package vn.edu.hcmute.uteexpress.tool.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hcmute.uteexpress.service.media.*;
import vn.edu.hcmute.uteexpress.service.media.impl.CloudMediaStorageServiceImpl;
import vn.edu.hcmute.uteexpress.support.InMemoryCloudMediaGateway;

class MigrateAivenCloudFilesTest {
    @TempDir Path root;
    Connection connection;
    byte[] png;
    @BeforeEach void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:h2:mem:cloud-migrate-"+UUID.randomUUID()+";MODE=MySQL","sa","");
        try (var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE delivery_proof(id BIGINT PRIMARY KEY,proof_image_path VARCHAR(255),signature_path VARCHAR(255))");
            statement.execute("CREATE TABLE service_review(id BIGINT PRIMARY KEY,media_path VARCHAR(255))");
            statement.execute("CREATE TABLE order_complaint(id BIGINT PRIMARY KEY,image_bytes BLOB,image_asset VARCHAR(255),revision BIGINT DEFAULT 0)");
            statement.execute("CREATE TABLE operations_case(id BIGINT PRIMARY KEY,evidence BLOB,evidence_asset VARCHAR(255),revision BIGINT DEFAULT 0)");
        }
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output); png=output.toByteArray();
        try (var insert=connection.prepareStatement("INSERT INTO order_complaint(id,image_bytes) VALUES(1,?)")) { insert.setBytes(1,png); insert.executeUpdate(); }
    }
    @AfterEach void close() throws Exception { connection.close(); }
    @Test void checkNeverUploadsOrModifiesBlob() throws Exception {
        var storage=mock(CloudMediaStorageService.class); var gateway=mock(CloudMediaGateway.class);
        assertEquals(0,MigrateAivenCloudFiles.migrate(connection,root,false,storage,gateway));
        verifyNoInteractions(storage,gateway);
        try (var rs=connection.createStatement().executeQuery("SELECT image_asset,image_bytes,revision FROM order_complaint")) {
            assertTrue(rs.next()); assertNull(rs.getString(1)); assertArrayEquals(png,rs.getBytes(2)); assertEquals(0,rs.getLong(3));
        }
    }
    @Test void applyVerifiesReadbackKeepsOriginalAndIsIdempotent() throws Exception {
        var gateway=new InMemoryCloudMediaGateway(); var storage=new CloudMediaStorageServiceImpl(gateway,new MediaValidationService());
        assertEquals(0,MigrateAivenCloudFiles.migrate(connection,root,true,storage,gateway));
        String ref;
        try (var rs=connection.createStatement().executeQuery("SELECT image_asset,image_bytes,revision FROM order_complaint")) {
            assertTrue(rs.next()); ref=rs.getString(1); assertTrue(ref.startsWith("cld1:test-cloud:COMPLAINT:"));
            assertArrayEquals(png,rs.getBytes(2)); assertEquals(1,rs.getLong(3));
        }
        assertArrayEquals(png,storage.read(ref,MediaPurpose.COMPLAINT).bytes());
        assertEquals(0,MigrateAivenCloudFiles.migrate(connection,root,true,storage,gateway));
        try (var rs=connection.createStatement().executeQuery("SELECT image_asset,revision FROM order_complaint")) {
            rs.next(); assertEquals(ref,rs.getString(1)); assertEquals(1,rs.getLong(2));
        }
    }
    @Test void failedReadbackRollsBackReferenceAndDeletesOnlyNewUpload() throws Exception {
        var storage=mock(CloudMediaStorageService.class); var gateway=mock(CloudMediaGateway.class);
        String ref="cld1:test-cloud:COMPLAINT:0123456789abcdef0123456789abcdef:png";
        when(storage.storeBytes(any(),any(),any())).thenReturn(ref);
        when(storage.read(ref,MediaPurpose.COMPLAINT)).thenReturn(new CloudMediaStorageService.MediaFile(new byte[]{1},org.springframework.http.MediaType.IMAGE_PNG));
        assertEquals(1,MigrateAivenCloudFiles.migrate(connection,root,true,storage,gateway));
        verify(gateway).deleteNewUpload(vn.edu.hcmute.uteexpress.service.media.CloudMediaReference.parse(ref));
        try (var rs=connection.createStatement().executeQuery("SELECT image_asset,image_bytes FROM order_complaint")) {
            rs.next(); assertNull(rs.getString(1)); assertArrayEquals(png,rs.getBytes(2));
        }
    }
    @Test void rejectsTraversalRemoteUrlsAndMissingFileWithoutChangingDb() throws Exception {
        for (String unsafe : new String[]{"/uploads/giao-hang/../../config/aiven-local.properties","https://evil.invalid/a.png","C:/private.png"})
            assertThrows(IllegalArgumentException.class,() -> MigrateAivenCloudFiles.resolveLegacy(root,unsafe,MediaPurpose.PROOF));
        String old="/uploads/giao-hang/0123456789abcdef0123456789abcdef.png";
        try (var insert=connection.prepareStatement("INSERT INTO delivery_proof(id,proof_image_path) VALUES(1,?)")) { insert.setString(1,old); insert.executeUpdate(); }
        var storage=mock(CloudMediaStorageService.class); var gateway=mock(CloudMediaGateway.class);
        assertEquals(1,MigrateAivenCloudFiles.migrate(connection,root,false,storage,gateway));
        verifyNoInteractions(storage,gateway);
        Files.createDirectories(root.resolve("giao-hang")); Files.write(root.resolve("giao-hang/0123456789abcdef0123456789abcdef.png"),png);
        assertEquals(root.resolve("giao-hang/0123456789abcdef0123456789abcdef.png").toRealPath(),MigrateAivenCloudFiles.resolveLegacy(root,old,MediaPurpose.PROOF));
    }
}
