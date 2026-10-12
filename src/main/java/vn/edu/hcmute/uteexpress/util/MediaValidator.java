package vn.edu.hcmute.uteexpress.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;

/** Kiểm tra nội dung và kích thước trước khi lưu; không tin đuôi/MIME từ client. */
public final class MediaValidator {
    private MediaValidator() { }
    public static ServiceReview.MediaType validate(MultipartFile file, long maximum) {
        if (file == null || file.isEmpty() || file.getSize() > maximum) { throw invalid(); }
        String name = file.getOriginalFilename();
        String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(Math.toIntExact(maximum + 1)); }
        catch (IOException ex) { throw invalid(); }
        if (bytes.length == 0 || bytes.length > maximum) { throw invalid(); }
        if (java.util.Set.of("jpg", "jpeg", "png", "gif").contains(extension)) {
            validateImageBytes(bytes, extension);
            return ServiceReview.MediaType.IMAGE;
        }
        if (extension.equals("mp4") && validMp4(bytes)) { return ServiceReview.MediaType.VIDEO; }
        if (extension.equals("webm") && validWebm(bytes)) { return ServiceReview.MediaType.VIDEO; }
        throw invalid();
    }

    public static void validateImageBytes(byte[] bytes, String extension) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) { throw invalid(); }
            var reader = readers.next();
            try {
                reader.setInput(stream, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String expected = extension.equals("jpg") ? "jpeg" : extension;
                if (!format.equals(expected)) { throw invalid(); }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 10000 || height > 10000
                        || (long) width * height > 20_000_000 || reader.read(0) == null) { throw invalid(); }
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    private static boolean validMp4(byte[] data) {
        if (data.length < 24) return false;
        int offset = 0; boolean ftyp = false, moov = false, mdat = false;
        while (offset + 8 <= data.length) {
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).getInt());
            String type = new String(data, offset + 4, 4, StandardCharsets.US_ASCII);
            int header = 8;
            if (length == 1) {
                if (offset + 16 > data.length) return false;
                length = ByteBuffer.wrap(data, offset + 8, 8).getLong(); header = 16;
            } else if (length == 0) length = data.length - offset;
            if (length < header || length > data.length - offset) return false;
            if (offset == 0 && !type.equals("ftyp")) return false;
            if (type.equals("ftyp")) {
                if (length < 16) return false;
                String brand = new String(data, offset + 8, 4, StandardCharsets.US_ASCII);
                if (!java.util.Set.of("isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "dash").contains(brand)) return false;
                ftyp = true;
            }
            moov |= type.equals("moov") && length > header;
            mdat |= type.equals("mdat") && length > header;
            offset += (int) length;
        }
        return offset == data.length && ftyp && moov && mdat;
    }

    private static boolean validWebm(byte[] data) {
        if (data.length < 32 || data[0] != 0x1a || data[1] != 0x45 || data[2] != (byte) 0xdf || data[3] != (byte) 0xa3) return false;
        // EBML DocType phải là webm; container phải có Segment và Tracks.
        String binary = new String(data, StandardCharsets.ISO_8859_1);
        return binary.contains("\u0042\u0082\u0084webm") && binary.contains("\u0018\u0053\u0080\u0067")
                && binary.contains("\u0016\u0054\u00ae\u006b");
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Tệp không hợp lệ. Chọn ảnh JPG/PNG/GIF hoặc video MP4/WebM đúng định dạng và giới hạn dung lượng.");
    }
}
