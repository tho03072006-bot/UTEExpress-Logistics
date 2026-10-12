package vn.edu.hcmute.uteexpress.dto.tracking;

import vn.edu.hcmute.uteexpress.entity.Order;

/** Số đơn theo từng trạng thái (một dòng của câu đếm có group by). */
public record StatusCount(Order.OrderStatus status, long count) { }
