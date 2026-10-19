package vn.edu.hcmute.uteexpress.dto.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chỉ dữ liệu tối thiểu cho bộ chọn đơn, không có tên/địa chỉ/điện thoại hay ID tài khoản. */
public record ChatOrderPageResponse(UUID conversationId,String role,int page,boolean hasNext,long total,
        Instant checkedAt,List<OrderOptionResponse> orders) {
    public record OrderOptionResponse(String trackingCode,String status,String serviceType,String createdAt) { }
}
