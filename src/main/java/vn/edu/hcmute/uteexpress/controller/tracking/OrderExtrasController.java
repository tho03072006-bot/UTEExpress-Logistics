package vn.edu.hcmute.uteexpress.controller.tracking;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.service.tracking.*;

@Controller
public class OrderExtrasController {
    private final OrderJourneyService journey;
    private final ShipmentQrService qr;
    private final OrderComplaintService complaints;
    private final DeliveryProofFileService proofFiles;
    public OrderExtrasController(OrderJourneyService journey, ShipmentQrService qr, OrderComplaintService complaints, DeliveryProofFileService proofFiles) {
        this.journey = journey; this.qr = qr; this.complaints = complaints; this.proofFiles = proofFiles;
    }
    @GetMapping({"/shipper/don/{orderId}/bang-chung/{kind}", "/nguoi-dung/don-hang/{orderId}/bang-chung/{kind}",
            "/manager/don-hang/{orderId}/bang-chung/{kind}"})
    @ResponseBody
    public ResponseEntity<byte[]> proof(@PathVariable Long orderId, @PathVariable String kind, Authentication auth) {
        var file = proofFiles.read(orderId, auth.getName(), kind);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(file.type()).body(file.bytes());
    }
    @GetMapping({"/shipper/don/{orderId}/hanh-trinh", "/nguoi-dung/don-hang/{orderId}/hanh-trinh"})
    public String history(@PathVariable Long orderId, @RequestParam(defaultValue = "0") int page,
            Authentication auth, Model model) {
        model.addAttribute("order", journey.accessibleOrder(orderId, auth.getName()));
        model.addAttribute("history", journey.history(orderId, auth.getName(), page));
        model.addAttribute("prefix", prefix(auth));
        return "logistics/journey";
    }
    @GetMapping(value = {"/shipper/don/{orderId}/qr.png", "/nguoi-dung/don-hang/{orderId}/qr.png"}, produces = "image/png")
    @ResponseBody
    public ResponseEntity<byte[]> qr(@PathVariable Long orderId, Authentication auth) {
        var order = journey.accessibleOrder(orderId, auth.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(qr.generate(order.getTrackingCode()));
    }
    @GetMapping({"/nguoi-dung/don-hang/{orderId}/khieu-nai", "/shipper/don/{orderId}/khieu-nai"})
    public String complaint(@PathVariable Long orderId, Authentication auth, Model model) {
        var order = journey.accessibleOrder(orderId, auth.getName());
        populateComplaint(order, complaints.forOrder(order, auth.getName()).orElse(null), auth, model);
        return "logistics/complaint";
    }
    @PostMapping("/nguoi-dung/don-hang/{orderId}/khieu-nai")
    public String create(@PathVariable Long orderId, @RequestParam String subject, @RequestParam String description,
            @RequestParam(required = false) MultipartFile image, Authentication auth, RedirectAttributes flash) {
        try {
            complaints.create(orderId, auth.getName(), subject, description, image);
            flash.addFlashAttribute("message", "Đã gửi khiếu nại. Manager sẽ kiểm tra và phản hồi.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("subject", subject);
            flash.addFlashAttribute("description", description);
        }
        return "redirect:/nguoi-dung/don-hang/" + orderId + "/khieu-nai";
    }
    @GetMapping("/manager/khieu-nai")
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) OrderComplaint.Status status, Authentication auth, Model model) {
        model.addAttribute("complaints", complaints.list(auth.getName(), status, page));
        model.addAttribute("statuses", OrderComplaint.Status.values());
        model.addAttribute("selectedStatus", status);
        return "manager/complaints";
    }
    @GetMapping({"/manager/khieu-nai/{id}", "/shipper/khieu-nai/{id}", "/nguoi-dung/khieu-nai/{id}"})
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        var complaint = complaints.view(id, auth.getName());
        populateComplaint(complaint.getOrder(), complaint, auth, model);
        return "logistics/complaint";
    }
    @PostMapping("/manager/khieu-nai/{id}/nhan-xu-ly")
    public String claim(@PathVariable Long id, @RequestParam long revision, Authentication auth, RedirectAttributes flash) {
        complaints.claim(id, auth.getName(), revision); flash.addFlashAttribute("message", "Đã nhận phụ trách hồ sơ.");
        return "redirect:/manager/khieu-nai/" + id;
    }
    @GetMapping("/manager/khieu-nai/{id}/lich-su")
    public String timeline(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        var complaint = complaints.view(id, auth.getName());
        populateComplaint(complaint.getOrder(), complaint, auth, model);
        model.addAttribute("activities", complaints.timeline(id, auth.getName(), page));
        return "logistics/complaint";
    }
    @PostMapping("/manager/khieu-nai/{id}/phan-hoi")
    public String respond(@PathVariable Long id, @RequestParam long revision, @RequestParam OrderComplaint.Status status,
            @RequestParam String reply, Authentication auth, RedirectAttributes flash) {
        try {
            complaints.respond(id, auth.getName(), revision, status, reply);
            flash.addFlashAttribute("message", "Đã lưu phản hồi khiếu nại.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/manager/khieu-nai/" + id;
    }
    @PostMapping("/shipper/khieu-nai/{id}/bo-sung")
    public String supplement(@PathVariable Long id, @RequestParam long revision, @RequestParam String note,
            Authentication auth, RedirectAttributes flash) {
        try {
            complaints.supplement(id, auth.getName(), revision, note);
            flash.addFlashAttribute("message", "Đã gửi thông tin bổ sung cho Manager.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/shipper/khieu-nai/" + id;
    }
    @GetMapping(value = {"/manager/khieu-nai/{id}/anh", "/shipper/khieu-nai/{id}/anh", "/nguoi-dung/khieu-nai/{id}/anh"}, produces = "image/png")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long id, Authentication auth) {
        var complaint = complaints.view(id, auth.getName());
        if (!complaint.isHasImage()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG).body(complaint.getImageBytes());
    }
    private void populateComplaint(Order order, OrderComplaint complaint, Authentication auth, Model model) {
        model.addAttribute("order", order);
        model.addAttribute("complaint", complaint);
        if (complaint != null) model.addAttribute("activities", complaints.timeline(complaint.getId(), auth.getName(), 0));
        model.addAttribute("prefix", prefix(auth));
        String viewer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHIPPER")) ? "shipper"
                : auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) ? "customer" : "staff";
        model.addAttribute("viewer", viewer);
        model.addAttribute("imagePrefix", viewer.equals("staff") ? "/manager/khieu-nai/"
                : viewer.equals("shipper") ? "/shipper/khieu-nai/" : "/nguoi-dung/khieu-nai/");
        model.addAttribute("statuses", OrderComplaint.Status.values());
    }
    private String prefix(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHIPPER"))
                ? "/shipper/don/" : "/nguoi-dung/don-hang/";
    }
}
