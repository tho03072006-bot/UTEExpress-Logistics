package vn.edu.hcmute.uteexpress.service.manager;

import java.util.List;
import vn.edu.hcmute.uteexpress.dto.manager.*;

public interface StaffWorkbenchService {
    List<WorkQueueResponse> getWorkQueues(String actor);
    List<BatchTrackingResponse> findShipments(String actor, BatchTrackingRequest request);
    String exportShipments(String actor, BatchTrackingRequest request);
}
