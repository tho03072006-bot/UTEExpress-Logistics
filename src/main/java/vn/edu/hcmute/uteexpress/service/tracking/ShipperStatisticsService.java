package vn.edu.hcmute.uteexpress.service.tracking;

import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsResponse;

public interface ShipperStatisticsService {

    ShipperStatisticsResponse getStatistics(
            String shipperUsername,
            ShipperStatisticsPeriod period);

    ShipperStatisticsResponse getStatistics(String username, ShipperStatisticsPeriod period,
            java.time.LocalDate from, java.time.LocalDate to);
    byte[] exportCsv(String username, ShipperStatisticsPeriod period, java.time.LocalDate from, java.time.LocalDate to);
}
