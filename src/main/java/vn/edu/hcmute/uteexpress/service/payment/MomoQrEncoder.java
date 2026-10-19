package vn.edu.hcmute.uteexpress.service.payment;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.client.j2se.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR có vùng trắng đủ 4 module và được đọc kiểm tra trước khi hiển thị cho khách. */
public final class MomoQrEncoder {
    private MomoQrEncoder() { }
    public static String encode(String url) {
        if (url == null || url.length() > 1024) throw new IllegalArgumentException("Liên kết QR không hợp lệ.");
        var hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); hints.put(EncodeHintType.MARGIN, 4);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        // Một số mẫu bit ngẫu nhiên khiến bộ nhận diện chọn nhầm finder pattern.
        // Thử mask khác; không phát hành ảnh nếu không đọc lại đúng URL ở cả hai chế độ.
        for (int mask = -1; mask < 8; mask++) {
            if (mask >= 0) hints.put(EncodeHintType.QR_MASK_PATTERN, mask);
            try {
                var matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 480, 480, hints);
                var output = new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(matrix, "PNG", output);
                byte[] bytes = output.toByteArray();
                if (readable(bytes, url, false) && readable(bytes, url, true))
                    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            } catch (WriterException | IOException exception) {
                throw new IllegalStateException("Không tạo được ảnh QR. Vui lòng thử lại.");
            }
        }
        throw new IllegalStateException("Chưa tạo được QR có thể đọc rõ. Vui lòng tải lại trang để tạo mã mới.");
    }
    private static boolean readable(byte[] bytes, String url, boolean tryHarder) throws IOException {
        var source = new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(bytes)));
        var bitmap = new BinaryBitmap(new HybridBinarizer(source));
        var hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        if (tryHarder) hints.put(DecodeHintType.TRY_HARDER, true);
        try {
            return url.equals(new MultiFormatReader().decode(bitmap, hints).getText());
        } catch (NotFoundException exception) { return false; }
    }
}
