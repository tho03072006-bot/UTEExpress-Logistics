package vn.edu.hcmute.uteexpress.controller.user;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;

/**
 * Đẩy số đơn đang nằm trong giỏ vào model để navbar hiện được con số bên cạnh chữ "Giỏ đơn".
 *
 * Giới hạn phạm vi đúng trong package controller.user (phần của TV1) để các trang của
 * Guest / Shipper / Manager không phải chạy thêm câu đếm này. Trang nào không có
 * cartCount thì navbar tự ẩn con số đi, không lỗi.
 */
@ControllerAdvice(basePackages = "vn.edu.hcmute.uteexpress.controller.user")
public class UserCartBadgeAdvice {

    private final OrderDraftService orderDraftService;

    public UserCartBadgeAdvice(OrderDraftService orderDraftService) {
        this.orderDraftService = orderDraftService;
    }

    @ModelAttribute("cartCount")
    public long cartCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return 0;
        }
        return orderDraftService.countDrafts(authentication.getName());
    }
}
