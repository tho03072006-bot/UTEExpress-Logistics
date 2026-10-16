package vn.edu.hcmute.uteexpress.controller.manager;

import java.nio.charset.StandardCharsets;
import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Controller @RequestMapping("/manager")
public class ManagerReportController {
    private final ManagerReportService service;
    private final Clock clock;
    public ManagerReportController(ManagerReportService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/bao-cao")
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth, Model model) {
        var end = to == null ? LocalDate.now(clock) : to;
        var start = from == null ? end.minusDays(29) : from;
        model.addAttribute("report", service.report(auth.getName(), start, end));
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("statusLabels", ManagerOrderResponse.getStatusLabels());
        return "manager/report";
    }
    @GetMapping("/bao-cao/csv")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        var report = service.report(auth.getName(), from, to);
        // Chỉ xuất tổng hợp số/enum; không xuất thông tin khách hàng hoặc dữ liệu có thể thành công thức Excel.
        var csv = new StringBuilder("\uFEFFChi_tieu,Gia_tri\r\n");
        csv.append("Tu_ngay,").append(from).append("\r\nDen_ngay,").append(to).append("\r\n");
        report.counts().forEach((status, count) -> csv.append(status.name()).append(",").append(count).append("\r\n"));
        csv.append("Tong_don,").append(report.totalOrders()).append("\r\n")
            .append("Ty_le_giao_thanh_cong_phan_tram,").append(report.completionRate()).append("\r\n")
            .append("Cuoc_da_thanh_toan_VND,").append(report.paidShippingFees().toPlainString()).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao-cao-van-hanh.csv")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}

