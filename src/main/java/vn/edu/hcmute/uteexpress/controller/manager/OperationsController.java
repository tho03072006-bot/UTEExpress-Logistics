package vn.edu.hcmute.uteexpress.controller.manager;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Controller @RequestMapping("/manager")
public class OperationsController {
    private final OperationsOverviewService overview;
    private final OperationsCaseService cases;
    public OperationsController(OperationsOverviewService overview, OperationsCaseService cases) { this.overview = overview; this.cases = cases; }
    @GetMapping("/canh-bao") @Transactional(readOnly = true)
    public String alerts(@RequestParam(defaultValue = "UNASSIGNED") OperationsOverviewService.Alert alert,
            @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        var orders = overview.alerts(auth.getName(), alert, branchId, page);
        model.addAttribute("orders", orders);
        model.addAttribute("dropOffIds", overview.dropOffOrderIds(orders.getContent()));
        model.addAttribute("deliveryHubIds", overview.deliveryHubOrderIds(auth.getName(), orders.getContent()));
        model.addAttribute("branches", overview.branches(auth.getName())); model.addAttribute("branchId", branchId);
        model.addAttribute("lateComplaints", overview.lateComplaints(auth.getName(), 0));
        model.addAttribute("lateCash", overview.lateCash(auth.getName(), 0));
        model.addAttribute("alerts", OperationsOverviewService.Alert.values()); model.addAttribute("selected", alert);
        return "manager/operations-alerts";
    }
    @GetMapping("/hieu-suat-shipper") @Transactional(readOnly = true)
    public String performance(@RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "0") int page,
            Authentication auth, Model model) {
        model.addAttribute("branches", overview.branches(auth.getName())); model.addAttribute("branchId", branchId);
        model.addAttribute("performance", overview.performance(auth.getName(), branchId, page)); return "manager/shipper-performance";
    }
    @GetMapping("/ho-so-van-hanh") @Transactional(readOnly = true)
    public String cases(@RequestParam(required = false) OperationsCase.Kind kind, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        // Mở trang lần đầu (chưa chọn trạng thái): Admin thấy ngay hồ sơ chờ mình duyệt; chọn "Tất cả" thì gửi status rỗng.
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        OperationsCase.Status selected = status == null ? (admin ? OperationsCase.Status.REQUESTED : null)
                : status.isBlank() ? null : OperationsCase.Status.valueOf(status);
        model.addAttribute("cases", cases.list(auth.getName(), kind, selected, branchId, page));
        model.addAttribute("kindGroups", java.util.Arrays.stream(OperationsCase.Kind.values())
                .collect(java.util.stream.Collectors.groupingBy(OperationsCase.Kind::getGroup, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList())));
        model.addAttribute("statuses", OperationsCase.Status.values());
        model.addAttribute("selectedKind", kind); model.addAttribute("selectedStatus", selected);
        model.addAttribute("branches", cases.branches(auth.getName())); model.addAttribute("branchId", branchId);
        return "manager/operations-cases";
    }
    @PostMapping("/ho-so-van-hanh")
    public String request(@Valid @ModelAttribute OperationsCaseRequest form, BindingResult errors,
            @RequestParam(required = false) MultipartFile evidence, Authentication auth, RedirectAttributes flash) {
        if (errors.hasErrors()) { flash.addFlashAttribute("error", "Kiểm tra loại hồ sơ, ID, phiên bản, số tiền, lịch hẹn và lý do."); }
        else {
            try { Long id = cases.request(auth.getName(), form, evidence); return "redirect:/manager/ho-so-van-hanh/" + id; }
            catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        }
        if (form.kind() != null && form.targetId() != null && form.targetId() > 0) flash.addFlashAttribute("submitted", form);
        return "redirect:/manager/ho-so-van-hanh";
    }
    @GetMapping("/ho-so-van-hanh/tao") @Transactional(readOnly = true)
    public String prepare(@RequestParam OperationsCase.Kind kind, @RequestParam Long targetId, Authentication auth, Model model) {
        model.addAttribute("submitted", cases.prepare(auth.getName(), kind, targetId));
        return cases(null, null, null, 0, auth, model);
    }
    @GetMapping("/ho-so-van-hanh/{id}") @Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        var record = cases.view(auth.getName(), id); model.addAttribute("case", record);
        var reviewActions = cases.reviewActions(auth.getName(), id);
        model.addAttribute("canReview", reviewActions.canReview());
        model.addAttribute("canApprove", reviewActions.canApprove());
        var returnActions = cases.returnActions(auth.getName(), id);
        model.addAttribute("canStartReturn", returnActions.canStart());
        model.addAttribute("canComplete", record.getKind() == OperationsCase.Kind.RETURN ? returnActions.canComplete()
                : cases.canComplete(auth.getName(), id));
        if (record.getSettlement() != null) model.addAttribute("outstanding", cases.outstanding(record.getSettlement()));
        return "manager/operations-case";
    }
    @PostMapping("/ho-so-van-hanh/{id}/duyet")
    public String review(@PathVariable Long id, @Valid @ModelAttribute OperationsReviewRequest form, BindingResult errors,
            Authentication auth, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Chọn duyệt/từ chối, phiên bản hợp lệ và lý do 5–500 ký tự.");
        else { try { cases.review(auth.getName(), id, form); flash.addFlashAttribute("message", "Đã xử lý hồ sơ."); }
            catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); } }
        return "redirect:/manager/ho-so-van-hanh/" + id;
    }
    @PostMapping("/ho-so-van-hanh/{id}/bat-dau-hoan")
    public String start(@PathVariable Long id, @RequestParam long revision, @RequestParam String reason,
            Authentication auth, RedirectAttributes flash) {
        try { cases.startReturn(auth.getName(), id, revision, reason); flash.addFlashAttribute("message", "Đã ghi nhận đang hoàn hàng."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/manager/ho-so-van-hanh/" + id;
    }
    @PostMapping("/ho-so-van-hanh/{id}/hoan-tat")
    public String complete(@PathVariable Long id, @RequestParam long revision, @RequestParam String reference,
            @RequestParam MultipartFile evidence, Authentication auth, RedirectAttributes flash) {
        try { cases.complete(auth.getName(), id, revision, reference, evidence); flash.addFlashAttribute("message", "Đã lưu biên nhận ngoài hệ thống và hoàn tất hồ sơ."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/manager/ho-so-van-hanh/" + id;
    }
    @GetMapping("/ho-so-van-hanh/{id}/chung-tu") @ResponseBody
    public ResponseEntity<byte[]> evidence(@PathVariable Long id, Authentication auth) {
        var record = cases.view(auth.getName(), id);
        if (!record.isHasEvidence()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(cases.readEvidence(auth.getName(), id));
    }
}
