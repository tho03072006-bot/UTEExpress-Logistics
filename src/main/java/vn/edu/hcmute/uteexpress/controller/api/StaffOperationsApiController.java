package vn.edu.hcmute.uteexpress.controller.api;

import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.admin.StaffAccessResponse;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.service.admin.StaffApiAccessService;
import vn.edu.hcmute.uteexpress.service.manager.StaffWorkbenchService;

@RestController @RequestMapping("/api/internal")
public class StaffOperationsApiController {
    private final StaffWorkbenchService workbench;
    private final StaffApiAccessService access;
    public StaffOperationsApiController(StaffWorkbenchService workbench, StaffApiAccessService access) {
        this.workbench = workbench; this.access = access;
    }
    @GetMapping("/operations/queues")
    public List<WorkQueueResponse> queues(Principal actor) { return workbench.getWorkQueues(actor.getName()); }
    @GetMapping("/operations/tracking")
    public List<BatchTrackingResponse> tracking(Principal actor, @RequestParam String codes) {
        var form = new BatchTrackingRequest(); form.setCodes(codes);
        return workbench.findShipments(actor.getName(), form);
    }
    @GetMapping("/admin/access")
    public Page<StaffAccessResponse> access(Principal actor, @RequestParam(defaultValue = "0") int page) {
        return access.findAccess(actor.getName(), page);
    }
}
