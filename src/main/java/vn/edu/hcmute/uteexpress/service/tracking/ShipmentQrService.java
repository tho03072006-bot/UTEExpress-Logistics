package vn.edu.hcmute.uteexpress.service.tracking;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ShipmentQrService {
    private final ShipmentImageService images;
    public ShipmentQrService(ShipmentImageService images) { this.images = images; }
    public byte[] generate(String trackingCode) {
        checkCode(trackingCode);
        try {
            var matrix = new MultiFormatWriter().encode(trackingCode, BarcodeFormat.QR_CODE, 280, 280,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 2));
            var output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Không tạo được QR vận đơn."); }
    }
    public String decode(MultipartFile file) {
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(images.read(file))));
        var hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        try {
            String code = new MultiFormatReader().decode(bitmap, hints).getText().strip();
            checkCode(code);
            return code;
        } catch (NotFoundException ex) { throw new IllegalArgumentException("Không đọc được QR. Hãy chụp rõ và đầy đủ mã."); }
    }
    public void checkCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9-]{1,20}"))
            throw new IllegalArgumentException("QR phải chứa mã vận đơn UTEExpress hợp lệ.");
    }
}
