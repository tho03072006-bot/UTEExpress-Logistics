package vn.edu.hcmute.uteexpress.dto.tracking;

public enum ShipperStatisticsPeriod {
    DAY("Ngày"),
    WEEK("Tuần"),
    MONTH("Tháng");

    private final String displayName;

    ShipperStatisticsPeriod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
