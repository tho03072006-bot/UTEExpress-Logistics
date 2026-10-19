package vn.edu.hcmute.uteexpress.service.tracking;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ShipmentImageService {
    private final vn.edu.hcmute.uteexpress.service.media.MediaValidationService validation;
    public ShipmentImageService(vn.edu.hcmute.uteexpress.service.media.MediaValidationService validation) { this.validation=validation; }
    public BufferedImage read(MultipartFile file) {
        if (file==null || file.isEmpty() || file.getSize()>vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF.maximumBytes())
            throw new IllegalArgumentException("Chọn ảnh JPG/PNG có dung lượng tối đa 5 MB.");
        byte[] bytes=validation.readBounded(file,vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF);
        String format=validation.validate(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF,bytes,file.getOriginalFilename());
        String expected=format.equals("png") ? "image/png" : "image/jpeg";
        if (file.getContentType()!=null && !file.getContentType().isBlank()
                && !file.getContentType().equalsIgnoreCase(expected) && !file.getContentType().equalsIgnoreCase("application/octet-stream"))
            throw new IllegalArgumentException("MIME của ảnh không khớp nội dung.");
        try { return ImageIO.read(new ByteArrayInputStream(bytes)); }
        catch (IOException ex) { throw new IllegalArgumentException("Không đọc được ảnh. Hãy chọn ảnh khác."); }
    }
    public byte[] png(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(read(file), "png", output);
            if (output.size() > vn.edu.hcmute.uteexpress.service.media.MediaPurpose.COMPLAINT.maximumBytes())
                throw new IllegalArgumentException("Ảnh sau xử lý vượt 5 MB. Hãy chọn ảnh nhỏ hơn.");
            return output.toByteArray();
        } catch (IOException ex) { throw new IllegalArgumentException("Không xử lý được ảnh."); }
    }
}
