package vn.edu.hcmute.uteexpress.service.media;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaValidationService {
    public static final int MAX_IMAGE_DIMENSION=4096;
    public static final long MAX_IMAGE_PIXELS=12_000_000;
    public static final int MAX_VIDEO_SECONDS=120;
    private static final int MAX_GIF_FRAMES=100;
    private static final long MAX_GIF_PIXELS=60_000_000;
    private static final Set<String> IMAGE_FORMATS = Set.of("png","jpg","gif","webp");
    public byte[] readBounded(MultipartFile file, MediaPurpose purpose) {
        if (purpose == null) throw new IllegalArgumentException("Mục đích tải tệp không hợp lệ.");
        if (file == null || file.isEmpty() || file.getSize() < 1 || file.getSize() > purpose.maximumBytes())
            throw new IllegalArgumentException("Chọn tệp không rỗng, tối đa " + purpose.maximumBytes()/1024/1024 + " MB.");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(purpose.maximumBytes()+1);
            if (bytes.length != file.getSize()) throw new IllegalArgumentException("Kích thước tệp không khớp dữ liệu.");
            return bytes;
        } catch (IOException ex) { throw new IllegalArgumentException("Không đọc được tệp tải lên."); }
    }
    public String validate(MediaPurpose purpose, byte[] bytes, String filename) {
        if (purpose == null || bytes == null || bytes.length == 0 || bytes.length > purpose.maximumBytes())
            throw new IllegalArgumentException("Dung lượng tệp không hợp lệ.");
        if (filename == null || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)
                || filename.contains("/") || filename.contains("\\")) throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        int dot = filename.lastIndexOf('.');
        String format = dot < 0 ? "" : filename.substring(dot+1).toLowerCase(Locale.ROOT);
        if (format.equals("jpeg")) format = "jpg";
        if (!Set.of("png","jpg","gif","webp","mp4","webm").contains(format)
                || (purpose != MediaPurpose.REVIEW && !Set.of("jpg","png").contains(format))
                || (purpose == MediaPurpose.SIGNATURE && !format.equals("png")))
            throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ cho chức năng này.");
        if (!magicMatches(bytes,format)) throw new IllegalArgumentException("Nội dung tệp không khớp định dạng; không chỉ đổi đuôi tệp.");
        if (format.equals("webp") && bytes.length>=21 && ascii(bytes,12,4).equals("VP8X") && (bytes[20]&2)!=0)
            throw new IllegalArgumentException("Chỉ nhận WebP tĩnh; ảnh động hãy dùng GIF giới hạn hoặc video.");
        if (IMAGE_FORMATS.contains(format) && !format.equals("webp")) validateImage(bytes,format,purpose);
        // WebP và video còn được Cloudinary giải mã/kiểm tra metadata trước khi chấp nhận lưu DB.
        return format;
    }
    private void validateImage(byte[] bytes,String expected,MediaPurpose purpose) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Không giải mã được ảnh.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String detected = reader.getFormatName().toLowerCase(Locale.ROOT).replace("jpeg","jpg");
                if (!expected.equals(detected)) throw new IllegalArgumentException("Định dạng ảnh thực tế không khớp.");
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION || width * height > MAX_IMAGE_PIXELS)
                    throw new IllegalArgumentException("Ảnh tối đa 4096 px mỗi chiều và 12 triệu điểm ảnh.");
                if (expected.equals("gif")) {
                    int frames=reader.getNumImages(true);
                    if (frames<1 || frames>MAX_GIF_FRAMES || width*height*frames>MAX_GIF_PIXELS)
                        throw new IllegalArgumentException("GIF tối đa 100 khung và 60 triệu điểm ảnh tổng cộng.");
                }
                var image=reader.read(0);
                if (image == null) throw new IllegalArgumentException("Ảnh bị hỏng.");
                if (purpose==MediaPurpose.SIGNATURE) {
                    boolean visible=false, varied=false; int first=image.getRGB(0,0);
                    for (int y=0;y<image.getHeight() && !(visible && varied);y++) for (int x=0;x<image.getWidth();x++) {
                        int pixel=image.getRGB(x,y);
                        if ((pixel>>>24)>16) visible=true;
                        if (pixel!=first) varied=true;
                    }
                    if (!visible || !varied) throw new IllegalArgumentException("Chữ ký đang trống hoặc chỉ có một màu. Vui lòng ký xác nhận.");
                }
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("Ảnh bị hỏng hoặc không đọc được."); }
    }
    private boolean magicMatches(byte[] data,String format) {
        if (data.length < 12) return false;
        return switch (format) {
            case "png" -> data[0]==(byte)137 && data[1]==80 && data[2]==78 && data[3]==71 && data[4]==13 && data[5]==10 && data[6]==26 && data[7]==10;
            case "jpg" -> data[0]==(byte)255 && data[1]==(byte)216 && data[2]==(byte)255;
            case "gif" -> new String(data,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a");
            case "webp" -> ascii(data,0,4).equals("RIFF") && ascii(data,8,4).equals("WEBP");
            case "mp4" -> data.length>=24 && ascii(data,4,4).equals("ftyp");
            case "webm" -> data[0]==26 && data[1]==69 && data[2]==(byte)223 && data[3]==(byte)163
                    && new String(data,0,Math.min(data.length,4096),java.nio.charset.StandardCharsets.ISO_8859_1).contains("webm");
            default -> false;
        };
    }
    private String ascii(byte[] bytes,int offset,int count) { return new String(bytes,offset,count,java.nio.charset.StandardCharsets.US_ASCII); }
}
