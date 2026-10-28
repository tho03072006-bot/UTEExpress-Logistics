package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.service.media.*;
import vn.edu.hcmute.uteexpress.service.media.impl.*;

/** CLI chuyển tệp cũ theo từng bản ghi. Không xóa bản gốc, không khởi động Spring/server. */
public final class MigrateAivenCloudFiles {
    private record Source(String table,String source,String target,MediaPurpose purpose,boolean blob,boolean versioned) { }
    private static final List<Source> SOURCES=List.of(
            new Source("delivery_proof","proof_image_path","proof_image_path",MediaPurpose.PROOF,false,false),
            new Source("delivery_proof","signature_path","signature_path",MediaPurpose.SIGNATURE,false,false),
            new Source("service_review","media_path","media_path",MediaPurpose.REVIEW,false,false),
            new Source("order_complaint","image_bytes","image_asset",MediaPurpose.COMPLAINT,true,true),
            new Source("operations_case","evidence","evidence_asset",MediaPurpose.RECEIPT,true,true));
    private MigrateAivenCloudFiles() { }
    public static void main(String[] args) {
        if (args.length<1 || args.length>3 || !Set.of("--check","--apply").contains(args[0])
                || (args[0].equals("--check") && args.length>2)
                || (args[0].equals("--apply") && args.length<2)) {
            System.err.println("Dùng --check [upload-root] hoặc --apply <expected-cloud-name> [upload-root]. Không xóa file/blob gốc.");
            System.exit(1);
        }
        boolean apply=args[0].equals("--apply");
        int exitCode=0;
        try (var connection=AivenConnectionSettings.open()) {
            Path root=Path.of(args.length>(apply?2:1) ? args[apply?2:1] : "uploads").toAbsolutePath().normalize();
            CloudMediaGateway gateway=null; CloudMediaStorageService storage=null;
            if (apply) {
                var settings=CloudinaryToolSettings.load();
                if (!settings.cloudName().equals(args[1])) throw new IllegalArgumentException("Cloud name không khớp đích đã xác nhận.");
                gateway=new CloudinaryMediaGatewayImpl(settings);
                storage=new CloudMediaStorageServiceImpl(gateway,new MediaValidationService());
            }
            int failures=migrate(connection,root,apply,storage,gateway);
            if (failures>0) exitCode=2;
        } catch (Exception ex) {
            System.err.println("Migration chưa hoàn tất. Giữ tệp/blob gốc; không in dữ liệu, đường dẫn nhạy cảm hoặc phản hồi cloud.");
            exitCode=1;
        }
        if (exitCode!=0) System.exit(exitCode);
    }
    static int migrate(Connection connection,Path root,boolean apply,CloudMediaStorageService storage,CloudMediaGateway gateway) throws Exception {
        if (!connection.getAutoCommit()) throw new IllegalArgumentException("Cần kết nối CLI riêng, không có giao dịch đang mở.");
        int failures=0;
        boolean mysql="MySQL".equals(connection.getMetaData().getDatabaseProductName());
        if (mysql) try (var statement=connection.createStatement(); var rs=statement.executeQuery("SELECT GET_LOCK('uteexpress-cloud-files-migrate', 0)")) {
            if (!rs.next() || rs.getInt(1)!=1) throw new IllegalStateException("Đang chuyển tệp ở tiến trình khác.");
        }
        try {
            for (var source : SOURCES) {
                long last=0; int ready=0, done=0, skipped=0, failed=0;
                while (true) {
                    var ids=new ArrayList<Long>();
                    try (var query=connection.prepareStatement("SELECT id FROM "+source.table+" WHERE id>? AND "+source.source+" IS NOT NULL ORDER BY id LIMIT 100")) {
                        query.setLong(1,last); try (var rows=query.executeQuery()) { while (rows.next()) ids.add(rows.getLong(1)); }
                    }
                    if (ids.isEmpty()) break;
                    for (long id : ids) {
                        last=id; String uploaded=null; boolean commitAttempted=false;
                        try {
                            connection.setAutoCommit(false);
                            try (var query=connection.prepareStatement("SELECT "+source.source+(source.blob ? ","+source.target : "")
                                    +" FROM "+source.table+" WHERE id=?"+(apply ? " FOR UPDATE" : ""))) {
                                query.setLong(1,id);
                                try (var row=query.executeQuery()) {
                                    if (!row.next()) { skipped++; connection.rollback(); continue; }
                                    String current=source.blob ? row.getString(2) : row.getString(1);
                                    if ((source.blob && current!=null) || (!source.blob && current!=null && current.startsWith("cld1:"))) {
                                        var reference=CloudMediaReference.parse(current);
                                        if (reference.purpose()!=source.purpose || (apply && !reference.cloudName().equals(gateway.cloudName())))
                                            throw new IllegalArgumentException("Tham chiếu có mục đích/cloud khác.");
                                        skipped++; connection.rollback(); continue;
                                    }
                                    byte[] bytes; String name;
                                    if (source.blob) {
                                        try (var input=row.getBinaryStream(1)) {
                                            if (input==null) { skipped++; connection.rollback(); continue; }
                                            bytes=input.readNBytes(source.purpose.maximumBytes()+1);
                                        }
                                        name="legacy.png";
                                    } else {
                                        Path file=resolveLegacy(root,row.getString(1),source.purpose);
                                        name=file.getFileName().toString();
                                        try (var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(source.purpose.maximumBytes()+1); }
                                    }
                                    new MediaValidationService().validate(source.purpose,bytes,name);
                                    ready++;
                                    if (apply) {
                                        uploaded=storage.storeBytes(source.purpose,bytes,name);
                                        if (!MessageDigest.isEqual(bytes,storage.read(uploaded,source.purpose).bytes()))
                                            throw new IllegalStateException("Đọc lại cloud không khớp bản gốc.");
                                        try (var update=connection.prepareStatement("UPDATE "+source.table+" SET "+source.target+"=?"
                                                +(source.versioned ? ",revision=revision+1" : "")+" WHERE id=?")) {
                                            update.setString(1,uploaded); update.setLong(2,id);
                                            if (update.executeUpdate()!=1) throw new IllegalStateException("Bản ghi đã thay đổi.");
                                        }
                                        // Giữ blob/file gốc. Chỉ mã mới đã được đọc lại mới được commit.
                                        commitAttempted=true; connection.commit(); uploaded=null; done++;
                                    } else connection.rollback();
                                }
                            }
                        } catch (Exception ex) {
                            connection.rollback(); failed++;
                            // Mất mạng trong COMMIT có thể đã commit ở DB: tuyệt đối không xóa tệp khi kết quả chưa rõ.
                            if (uploaded!=null && !commitAttempted) try { gateway.deleteNewUpload(CloudMediaReference.parse(uploaded)); }
                            catch (RuntimeException cleanup) { System.err.println("Có tệp mới chưa dọn được; cần đối chiếu cloud, không tự xóa hàng loạt."); }
                            System.err.println("Chưa chuyển "+source.table+" id="+id+" ("+ex.getClass().getSimpleName()+", giữ bản gốc).");
                        } finally { connection.setAutoCommit(true); }
                    }
                }
                failures+=failed;
                System.out.println(source.table+"."+source.source+": ready="+ready+", migrated="+done+", skipped="+skipped+", failed="+failed);
            }
        } finally {
            if (mysql) try (var statement=connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-cloud-files-migrate')"); }
        }
        return failures;
    }
    static Path resolveLegacy(Path root,String stored,MediaPurpose purpose) throws IOException {
        if (stored==null) throw new IllegalArgumentException("Không có đường dẫn cũ.");
        String prefix; Path base;
        if ((purpose==MediaPurpose.PROOF || purpose==MediaPurpose.SIGNATURE) && stored.startsWith("/private-proof/")) {
            prefix="/private-proof/"; base=root.resolveSibling(root.getFileName()+"-private").resolve("giao-hang");
        } else {
            String folder=purpose==MediaPurpose.REVIEW ? "danh-gia" : "giao-hang";
            prefix="/uploads/"+folder+"/"; base=root.resolve(folder);
        }
        if (!stored.startsWith(prefix)) throw new IllegalArgumentException("Không phải đường dẫn tệp cũ được phép.");
        String name=stored.substring(prefix.length());
        if (!name.matches("(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})\\.(?:png|jpe?g|gif|webp|mp4|webm)"))
            throw new IllegalArgumentException("Tên tệp cũ không hợp lệ.");
        Path realBase=base.toRealPath(); Path candidate=realBase.resolve(name).toRealPath();
        if (!candidate.getParent().equals(realBase) || !Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Tệp nằm ngoài thư mục được phép.");
        return candidate;
    }
}
