package vn.edu.hcmute.uteexpress.service.tracking;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ShipmentImageService {
    public BufferedImage read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new IllegalArgumentException("Chọn ảnh JPG/PNG có dung lượng tối đa 5 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Tệp không phải ảnh JPG/PNG hợp lệ.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
                    throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG/PNG.");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 12_000_000)
                    throw new IllegalArgumentException("Ảnh tối đa 4096 px mỗi chiều và 12 triệu điểm ảnh.");
                return reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("Không đọc được ảnh. Hãy chọn ảnh khác."); }
    }
    public byte[] png(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(read(file), "png", output);
            if (output.size() > 5 * 1024 * 1024) throw new IllegalArgumentException("Ảnh sau xử lý vượt 5 MB. Hãy chọn ảnh nhỏ hơn.");
            return output.toByteArray();
        } catch (IOException ex) { throw new IllegalArgumentException("Không xử lý được ảnh."); }
    }
}
