package vn.edu.hcmute.uteexpress.tool.database;

import com.cloudinary.Cloudinary;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Map;
import javax.imageio.ImageIO;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.service.media.*;
import vn.edu.hcmute.uteexpress.service.media.impl.*;

/** --check chỉ ping; --prepare tạo các folder chung; --test-upload thử PNG và xóa đúng tệp mới. */
public final class CheckCloudinaryConnection {
    private CheckCloudinaryConnection() { }
    public static void main(String[] args) {
        if (args.length!=1 || !java.util.Set.of("--check","--prepare","--test-upload").contains(args[0])) {
            System.err.println("Dùng --check, --prepare hoặc --test-upload."); System.exit(1);
        }
        String stage="config";
        try {
            var settings=CloudinaryToolSettings.load();
            var sdk=new Cloudinary(Map.of("cloud_name",settings.cloudName(),"api_key",settings.apiKey(),
                    "api_secret",settings.apiSecret(),"secure",true,"timeout",15000,"signature_algorithm","SHA256"));
            stage="ping";
            if (!"ok".equals(sdk.api().ping(Map.of()).get("status"))) throw new IllegalStateException();
            System.out.println("Cloudinary: xác thực API thành công (không in khóa). ");
            if (args[0].equals("--prepare")) {
                stage="prepare-folders";
                for (var purpose : MediaPurpose.values()) {
                    String folder="uteexpress/"+purpose.folder();
                    try { sdk.api().createFolder(folder,Map.of()); }
                    catch (com.cloudinary.api.exceptions.AlreadyExists ex) { System.out.println("Giữ nguyên folder đã có: "+folder); }
                    System.out.println("Cloud folder ready: "+folder);
                }
            }
            if (args[0].equals("--test-upload")) {
                var gateway=new CloudinaryMediaGatewayImpl(settings);
                var storage=new CloudMediaStorageServiceImpl(gateway,new MediaValidationService());
                var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",output);
                byte[] source=output.toByteArray(); String ref=null;
                try {
                    stage="upload"; ref=storage.storeBytes(MediaPurpose.PROOF,source,"connection-test.png");
                    stage="download";
                    if (!MessageDigest.isEqual(source,storage.read(ref,MediaPurpose.PROOF).bytes())) throw new IllegalStateException();
                    System.out.println("Cloudinary: upload authenticated + đọc lại khớp dữ liệu thành công.");
                } finally {
                    if (ref!=null) {
                        String previous=stage; stage="cleanup"; gateway.deleteNewUpload(CloudMediaReference.parse(ref)); stage=previous;
                        System.out.println("Cloudinary: đã xóa ảnh mẫu vừa tạo; không đụng tệp khác.");
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Cloudinary check failed at "+stage+" ("+ex.getClass().getSimpleName()+"); provider details and credentials hidden.");
            if (stage.equals("config") && ex instanceof IllegalArgumentException && ex.getMessage()!=null
                    && ex.getMessage().startsWith("Cloudinary fields invalid:")) System.err.println(ex.getMessage());
            if (stage.equals("config")) for (var frame : java.util.Arrays.stream(ex.getStackTrace()).limit(4).toList())
                System.err.println("Config location: "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());
            System.exit(1);
        }
    }
}
