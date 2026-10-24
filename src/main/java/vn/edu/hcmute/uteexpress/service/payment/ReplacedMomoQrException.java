package vn.edu.hcmute.uteexpress.service.payment;

/** Mỗi lần tạo QR mới thu hồi phiên bản cũ ngay trong database dùng chung. */
public class ReplacedMomoQrException extends IllegalStateException {
    public ReplacedMomoQrException() {
        super("Mã QR này đã được thay thế hoặc thông tin đơn đã thay đổi. Hãy quét mã mới nhất; mã cũ không thể thanh toán.");
    }
}
