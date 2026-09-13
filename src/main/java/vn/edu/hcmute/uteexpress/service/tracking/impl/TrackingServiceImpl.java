package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;

@Service
@Transactional(readOnly = true)
public class TrackingServiceImpl implements TrackingService {

    private final AppUserRepository appUserRepository;
    private final OrderRepository orderRepository;

    public TrackingServiceImpl(AppUserRepository appUserRepository,
                               OrderRepository orderRepository) {
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public List<Order> findAssignedOrders(String shipperUsername) {
        AppUser shipper = appUserRepository.findByUsername(shipperUsername)
                .orElseThrow(() ->
                        new IllegalStateException("Không tìm thấy tài khoản shipper"));

        if (shipper.getRole() != AppUser.Role.SHIPPER) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER");
        }

        return orderRepository.findByShipper(shipper)
                .stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();
    }
}