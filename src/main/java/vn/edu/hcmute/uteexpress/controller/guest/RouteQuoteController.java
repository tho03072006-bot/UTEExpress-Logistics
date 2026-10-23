package vn.edu.hcmute.uteexpress.controller.guest;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.FeeEstimateRequest;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.service.routing.RouteQuoteService;

@RestController
public class RouteQuoteController {
    private final RouteQuoteService service;
    public RouteQuoteController(RouteQuoteService service) { this.service = service; }
    /** originBranchName/originHubAddress: quầy nhận hàng khi khách tự mang tới. */
    public record QuoteView(ServiceType serviceType, double distanceKm, boolean interprovincial,
            BigDecimal baseFee, BigDecimal distanceFee, BigDecimal routeFee, BigDecimal dropOffDiscount, BigDecimal total, String leadTimeText,
            String pickupMode, String originBranchName, String originHubAddress) {
        static QuoteView from(RouteQuoteService.Quote q) {
            var branch = q.pickupArea().getBranch();
            return new QuoteView(q.serviceType(), q.distanceKm(), q.interprovincial(), q.baseFee(), q.distanceFee(), q.routeFee(),
                    q.dropOffDiscount(), q.total(), q.leadTimeText(), q.pickupMode().name(), branch.getName(), branch.getHubAddress());
        }
    }
    @GetMapping("/api/routing/quote")
    public org.springframework.http.ResponseEntity<List<QuoteView>> quote(@Valid @ModelAttribute FeeEstimateRequest form) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(service.quotes(form).stream().map(QuoteView::from).toList());
    }
}
