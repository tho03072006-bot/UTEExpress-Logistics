package vn.edu.hcmute.uteexpress.service.payment;

/** Đơn đã được trả bằng một mã QR khác; mã QR hiện tại không được thanh toán thêm lần nữa. */
public class PaidByAnotherQrException extends IllegalStateException {
    public PaidByAnotherQrException() {
        super("Đơn này đã được thanh toán bằng một mã QR khác. Mã QR này không thể thanh toán nữa.");
    }
}
