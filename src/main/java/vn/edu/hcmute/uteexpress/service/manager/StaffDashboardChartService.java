package vn.edu.hcmute.uteexpress.service.manager;

import vn.edu.hcmute.uteexpress.dto.manager.DashboardChartsResponse;

public interface StaffDashboardChartService {
    DashboardChartsResponse getOperationsCharts(String actor);
    DashboardChartsResponse getAdminCharts(String actor);
}
