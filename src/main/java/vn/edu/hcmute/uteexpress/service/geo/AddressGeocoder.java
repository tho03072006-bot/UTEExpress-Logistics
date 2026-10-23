package vn.edu.hcmute.uteexpress.service.geo;

/** Đổi địa chỉ chữ thành tọa độ để tính khoảng cách đường chim bay khi báo giá/tạo đơn. */
public interface AddressGeocoder {
    /**
     * @throws AddressNotFoundException địa chỉ quá chung chung hoặc không tìm thấy (người dùng cần ghi rõ hơn)
     * @throws GeocodingUnavailableException dịch vụ bản đồ lỗi/hết thời gian chờ (thử lại sau)
     */
    GeoPoint locate(String address);

    /** Ghi rõ hơn thì tìm được: lỗi do người nhập, hiện lại trên form để sửa. */
    class AddressNotFoundException extends IllegalStateException {
        public AddressNotFoundException(String message) { super(message); }
    }

    /** Dịch vụ bản đồ tạm lỗi: không phải lỗi của người nhập. */
    class GeocodingUnavailableException extends IllegalStateException {
        public GeocodingUnavailableException() {
            super("Chưa tính được khoảng cách vì dịch vụ bản đồ đang bận. Vui lòng thử lại sau ít phút.");
        }
    }
}
