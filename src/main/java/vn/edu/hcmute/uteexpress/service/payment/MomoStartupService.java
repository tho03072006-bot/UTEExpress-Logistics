package vn.edu.hcmute.uteexpress.service.payment;

public interface MomoStartupService {
    record Snapshot(String state, String message) { }
    Snapshot snapshot();

    /**
     * Gọi khi khách mở trang thanh toán MoMo: Render Free ngủ sau ~15 phút không dùng nên đánh thức lại ở nền
     * để lúc quét QR không gặp màn hình chờ của Render. Không chặn request, không lặp giữ Render luôn thức.
     */
    default void wakeForPayment() { }
}
