package vn.edu.hcmute.uteexpress.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Render provider QR locally, without forwarding identifiers to an external image service. */
@Service
public class PaymentQrCodeService {
    public String toSvg(String content) {
        if (content == null || content.isBlank() || content.length() > 4096) {
            throw new IllegalStateException("Nội dung QR MoMo không hợp lệ.");
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1, 1,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4));
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " " + matrix.getHeight()
                    + "\" role=\"img\" aria-label=\"Mã QR thanh toán MoMo TEST\" style=\"width:100%;max-width:288px;height:auto\""
                    + " shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>"
                    + "<path d=\"" + path + "\" fill=\"black\"/></svg>";
        } catch (WriterException ex) {
            throw new IllegalStateException("Không tạo được mã QR MoMo. Vui lòng mở trang thanh toán MoMo.");
        }
    }
}
