package vn.edu.hcmute.uteexpress.controller.manager;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.manager.BatchTrackingRequest;
import vn.edu.hcmute.uteexpress.service.manager.StaffWorkbenchService;

@Controller
@RequestMapping("/manager/tra-cuu-hang-loat")
public class BatchTrackingController {
    private final StaffWorkbenchService service;
    public BatchTrackingController(StaffWorkbenchService service) { this.service = service; }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("form", new BatchTrackingRequest()); return "manager/batch-tracking";
    }
    @PostMapping
    public String lookup(Principal actor, @Valid @ModelAttribute("form") BatchTrackingRequest form,
            BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { model.addAttribute("results", service.findShipments(actor.getName(), form)); }
            catch (IllegalArgumentException ex) { errors.rejectValue("codes", "invalid", ex.getMessage()); }
        }
        return "manager/batch-tracking";
    }
    @PostMapping("/xuat-csv")
    public ResponseEntity<String> export(Principal actor, @Valid @ModelAttribute BatchTrackingRequest form) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=uteexpress-van-don.csv")
            .cacheControl(CacheControl.noStore()).body(service.exportShipments(actor.getName(), form));
    }
}
