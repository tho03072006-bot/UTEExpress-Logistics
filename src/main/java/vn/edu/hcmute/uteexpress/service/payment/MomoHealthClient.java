package vn.edu.hcmute.uteexpress.service.payment;

import java.net.URI;

/** Chỉ kiểm tra sức khỏe Render, không gửi vé, thông tin khách hoặc yêu cầu thanh toán. */
public interface MomoHealthClient {
    boolean isReachable(URI origin) throws InterruptedException;
}
