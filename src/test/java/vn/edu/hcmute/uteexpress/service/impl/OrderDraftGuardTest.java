package vn.edu.hcmute.uteexpress.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DraftOrder;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DraftOrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderDraftGuardTest {
    private final DraftOrderRepository drafts = mock(DraftOrderRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final OrderService orders = mock(OrderService.class);
    private final PromoCodeService promos = mock(PromoCodeService.class);
    private final OrderDraftServiceImpl service = new OrderDraftServiceImpl(drafts, users, orders, promos);

    @Test void invalidDraftIsRejectedEvenWithoutMvcValidation() {
        OrderCreateRequest request = request();
        request.setWeightKg(Double.POSITIVE_INFINITY);
        assertThatThrownBy(() -> service.addDraft(request, "sender"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("0,1 đến 100");
        verifyNoInteractions(drafts, users, orders, promos);
    }

    @Test void quotaIsCheckedAfterLockingTheCartOwner() {
        AppUser owner = owner();
        when(users.findByUsernameForUpdate("sender")).thenReturn(Optional.of(owner));
        when(drafts.countByUser(owner)).thenReturn(30L);
        assertThatThrownBy(() -> service.addDraft(request(), "sender")).hasMessageContaining("tối đa 30");
        var sequence = inOrder(users, drafts);
        sequence.verify(users).findByUsernameForUpdate("sender");
        sequence.verify(drafts).countByUser(owner);
        verify(drafts, never()).save(any());
    }

    @Test void repeatedCartConfirmationFindsNoRemainingDrafts() {
        AppUser owner = owner();
        List<DraftOrder> stored = new ArrayList<>(List.of(draft("")));
        when(users.findByUsernameForUpdate("sender")).thenReturn(Optional.of(owner));
        when(drafts.findByUserOrderByCreatedAtDesc(owner)).thenAnswer(call -> new ArrayList<>(stored));
        when(orders.createOrder(any(), eq("sender"))).thenReturn(new Order());
        doAnswer(call -> {
            Iterable<DraftOrder> removed = call.getArgument(0);
            removed.forEach(stored::remove);
            return null;
        }).when(drafts).deleteAll(any());
        assertThat(service.confirmAllDrafts("sender")).hasSize(1);
        assertThatThrownBy(() -> service.confirmAllDrafts("sender")).hasMessageContaining("trống");
        verify(users, times(2)).findByUsernameForUpdate("sender");
        verify(orders, times(1)).createOrder(any(), eq("sender"));
    }

    @Test void batchLocksPromosInDeterministicOrderBeforeCreatingAnyOrder() {
        AppUser owner = owner();
        when(users.findByUsernameForUpdate("sender")).thenReturn(Optional.of(owner));
        when(drafts.findByUserOrderByCreatedAtDesc(owner)).thenReturn(List.of(draft("ZLAST"), draft("AFIRST")));
        when(orders.estimateFee(any(), anyDouble())).thenReturn(java.math.BigDecimal.valueOf(18000));
        when(orders.createOrder(any(), eq("sender"))).thenReturn(new Order());
        service.confirmAllDrafts("sender");
        var sequence = inOrder(promos, orders);
        sequence.verify(orders).estimateFee(Order.ServiceType.STANDARD, 1.0);
        sequence.verify(promos).requireUsableCode(eq("AFIRST"), any());
        sequence.verify(orders).estimateFee(Order.ServiceType.STANDARD, 1.0);
        sequence.verify(promos).requireUsableCode(eq("ZLAST"), any());
        sequence.verify(orders, times(2)).createOrder(any(), eq("sender"));
    }

    @Test void unknownOrForeignDraftCannotCreateAnOrder() {
        AppUser owner = owner();
        when(users.findByUsernameForUpdate("sender")).thenReturn(Optional.of(owner));
        when(drafts.findByIdAndUser(55L, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmDraft(55L, "sender")).hasMessageContaining("Không tìm thấy");
        verifyNoInteractions(orders);
    }

    private static AppUser owner() {
        AppUser owner = new AppUser();
        owner.setId(1L);
        owner.setUsername("sender");
        return owner;
    }

    private static OrderCreateRequest request() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setSenderName("Người gửi");
        request.setSenderAddress("1 Võ Văn Ngân");
        request.setReceiverName("Người nhận");
        request.setReceiverAddress("2 Nguyễn Trãi");
        request.setReceiverPhone("0901234567");
        request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD);
        return request;
    }

    private static DraftOrder draft(String code) {
        DraftOrder draft = new DraftOrder();
        OrderCreateRequest request = request();
        draft.setSenderName(request.getSenderName());
        draft.setSenderAddress(request.getSenderAddress());
        draft.setReceiverName(request.getReceiverName());
        draft.setReceiverAddress(request.getReceiverAddress());
        draft.setReceiverPhone(request.getReceiverPhone());
        draft.setWeightKg(request.getWeightKg());
        draft.setServiceType(request.getServiceType());
        draft.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        draft.setPromoCode(code);
        return draft;
    }
}
