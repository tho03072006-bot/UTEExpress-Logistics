package vn.edu.hcmute.uteexpress.dto.tracking;

/** Số lần (vd. lấy hàng thất bại) của từng vận đơn, đọc một lần cho cả trang. */
public record OrderCount(Long orderId, long count) { }
