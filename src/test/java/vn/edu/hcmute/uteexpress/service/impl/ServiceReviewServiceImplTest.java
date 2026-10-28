package vn.edu.hcmute.uteexpress.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.uteexpress.dto.ServiceReviewRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.FileStorageService;

@ExtendWith(MockitoExtension.class)
class ServiceReviewServiceImplTest {
    @Mock ServiceReviewRepository reviews;
    @Mock OrderRepository orders;
    @Mock AppUserRepository users;
    @Mock FileStorageService files;
    @InjectMocks ServiceReviewServiceImpl service;
    AppUser customer;
    @BeforeEach void setup() {
        customer=new AppUser("reviewer","test-hash","reviewer@test.invalid"); customer.setId(1L);
        customer.setEnabled(true); customer.setRole(AppUser.Role.USER);
        when(users.findByUsername("reviewer")).thenReturn(Optional.of(customer));
    }
    ServiceReviewRequest form() {
        var form=new ServiceReviewRequest(); form.setRating(5); form.setContent("Dịch vụ giao hàng đúng hẹn, đóng gói và hỗ trợ khách hàng rất tốt."); return form;
    }
    @Test void rejectsInvalidRatingAndTrimmedContentBeforeStorageOrOrderLock() {
        for (Integer rating : new Integer[]{null,0,6}) {
            var form=form(); form.setRating(rating);
            assertThrows(IllegalArgumentException.class,() -> service.createReview(1L,form,"reviewer"));
        }
        for (String content : new String[]{null," ".repeat(60),"","a".repeat(1001),"a".repeat(60)+"\u0000"}) {
            var form=form(); form.setContent(content);
            assertThrows(IllegalArgumentException.class,() -> service.createReview(1L,form,"reviewer"));
        }
        verifyNoInteractions(files,orders,reviews);
    }
    @Test void lockedDisabledOrWrongRoleCannotUpload() {
        customer.setLockedByAdmin(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,() -> service.createReview(1L,form(),"reviewer"));
        customer.setLockedByAdmin(false); customer.setEnabled(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,() -> service.createReview(1L,form(),"reviewer"));
        customer.setEnabled(true); customer.setRole(AppUser.Role.MANAGER);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,() -> service.createReview(1L,form(),"reviewer"));
        verifyNoInteractions(files,orders,reviews);
    }
    @Test void ownershipStatusAndDuplicateAreCheckedBeforeCloud() {
        var order=new Order(); var other=new AppUser(); other.setUsername("other"); order.setSender(other);
        order.setStatus(Order.OrderStatus.DELIVERED);
        when(orders.findLockedById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class,() -> service.createReview(1L,form(),"reviewer"));
        order.setSender(customer); order.setStatus(Order.OrderStatus.IN_TRANSIT);
        assertThrows(IllegalStateException.class,() -> service.createReview(1L,form(),"reviewer"));
        order.setStatus(Order.OrderStatus.DELIVERED); when(reviews.existsByOrder(order)).thenReturn(true);
        assertThrows(IllegalStateException.class,() -> service.createReview(1L,form(),"reviewer"));
        verifyNoInteractions(files);
    }
    @Test void optionalAttachmentDoesNotRequireCloud() {
        var order=new Order(); order.setSender(customer); order.setStatus(Order.OrderStatus.DELIVERED);
        when(orders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));
        var result=service.createReview(1L,form(),"reviewer");
        assertEquals(5,result.getRating()); assertFalse(result.hasMedia()); verifyNoInteractions(files);
    }
}
