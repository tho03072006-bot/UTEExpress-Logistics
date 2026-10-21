package vn.edu.hcmute.uteexpress.service.profile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.dto.profile.ProfileDetailForm;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.AppUser.Role;
import vn.edu.hcmute.uteexpress.entity.UserProfileDetail;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.SavedAddressRepository;
import vn.edu.hcmute.uteexpress.repository.UserProfileDetailRepository;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaGateway;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService;
import vn.edu.hcmute.uteexpress.service.media.MediaPurpose;
import vn.edu.hcmute.uteexpress.util.DeliverySuccessRate;

/**
 * Trang "Tài khoản của tôi" cho người gửi và Shipper: ảnh đại diện (Cloudinary), thông tin cá nhân,
 * tùy chọn email và số liệu tài khoản. Manager/Admin dùng tài khoản nội bộ riêng nên không qua đây.
 */
@Service
public class AccountProfileService {
    private static final Logger log = LoggerFactory.getLogger(AccountProfileService.class);
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    /** Số đơn theo vai trò: người gửi tính đơn đã gửi, Shipper tính đơn được phân công. */
    public record Stats(long total, long active, long delivered, long returned, long cancelled, java.math.BigDecimal successRate,
            long savedAddresses) { }

    /** Khách chấm sao cho Shipper (tổng hợp từ đánh giá dịch vụ của các đơn Shipper đã giao). */
    public record Rating(long count, java.math.BigDecimal average, java.util.List<RatingItem> latest) {
        public int fullStars() { return average.setScale(0, java.math.RoundingMode.HALF_UP).intValue(); }
    }

    public record RatingItem(int stars, String content, String trackingCode, LocalDateTime createdAt) { }

    public record View(AppUser account, UserProfileDetail detail, Stats stats, Rating rating) {
        public boolean hasAvatar() { return detail != null && detail.hasAvatar(); }
        /** Mốc lưu hồ sơ gần nhất: đổi mỗi lần lưu nên trình duyệt tải ảnh mới; luôn > 0 vì 0 là "chưa biết" ở menu. */
        public long avatarVersion() { return detail == null ? 0 : version(detail); }
        public boolean notifyOrderEmail() { return detail == null || detail.isNotifyOrderEmail(); }
        public boolean shipper() { return account.getRole() == Role.SHIPPER; }

        /** Các mục để hồ sơ "đầy đủ", hiện thành danh sách việc cần làm ở trang tài khoản. */
        public java.util.List<Check> checklist() {
            return java.util.List.of(
                    new Check("Ảnh đại diện", "bi-camera", hasAvatar()),
                    new Check("Họ và tên", "bi-person", filled(account.getFullName())),
                    new Check("Số điện thoại", "bi-telephone", filled(account.getPhone())),
                    new Check("Ngày sinh", "bi-cake2", detail != null && detail.getBirthDate() != null),
                    new Check("Giới tính", "bi-gender-ambiguous", detail != null && detail.getGender() != null),
                    new Check("Địa chỉ liên hệ", "bi-geo-alt", detail != null && (filled(detail.getProvince()) || filled(detail.getAddressLine()))),
                    new Check("Giới thiệu ngắn", "bi-chat-quote", detail != null && filled(detail.getBio())));
        }

        public int completeness() {
            var items = checklist();
            return (int) Math.round(items.stream().filter(Check::done).count() * 100.0 / items.size());
        }

        private static boolean filled(String value) { return value != null && !value.isBlank(); }
    }

    public record Check(String label, String icon, boolean done) { }

    public record AvatarRef(long version, String asset) { }

    private final AppUserRepository users;
    private final UserProfileDetailRepository details;
    private final OrderRepository orders;
    private final SavedAddressRepository addresses;
    private final CloudMediaStorageService storage;
    private final CloudMediaGateway gateway;
    private final Clock clock;

    private final vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository reviews;

    public AccountProfileService(AppUserRepository users, UserProfileDetailRepository details, OrderRepository orders,
            SavedAddressRepository addresses, CloudMediaStorageService storage, CloudMediaGateway gateway, Clock clock,
            vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository reviews) {
        this.users = users; this.details = details; this.orders = orders; this.addresses = addresses;
        this.storage = storage; this.gateway = gateway; this.clock = clock; this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public View view(String username) {
        AppUser user = account(username);
        return new View(user, details.findByUser(user).orElse(null), stats(user), user.getRole() == Role.SHIPPER ? rating(user) : null);
    }

    private Rating rating(AppUser shipper) {
        Object[] row = reviews.ratingSummaryForShipper(shipper).stream().findFirst().orElse(new Object[]{0L, 0.0});
        long count = row[0] == null ? 0 : ((Number) row[0]).longValue();
        java.math.BigDecimal average = count == 0 ? java.math.BigDecimal.ZERO
                : java.math.BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, java.math.RoundingMode.HALF_UP);
        var latest = count == 0 ? java.util.List.<RatingItem>of() : reviews.latestForShipper(shipper,
                org.springframework.data.domain.PageRequest.of(0, 3)).stream()
                .map(r -> new RatingItem(r.getRating(), r.getContent(), r.getOrder().getTrackingCode(), r.getCreatedAt())).toList();
        return new Rating(count, average, latest);
    }

    @Transactional
    public void updateDetails(String username, ProfileDetailForm form) {
        LocalDate birthDate = form.getBirthDate();
        if (birthDate != null && (birthDate.isBefore(EARLIEST_BIRTH_DATE) || birthDate.isAfter(LocalDate.now(clock))))
            throw new IllegalArgumentException("Ngày sinh phải từ năm 1900 đến hôm nay.");
        UserProfileDetail detail = detailOf(account(username));
        detail.changeDetails(birthDate, form.getGender(), clean(form.getBio(), 160), clean(form.getProvince(), 60),
                clean(form.getAddressLine(), 200), LocalDateTime.now(clock));
        details.save(detail);
    }

    /** Lưu ảnh mới lên Cloudinary; ảnh cũ chỉ bị xóa sau khi lưu database thành công. */
    @Transactional
    public void updateAvatar(String username, MultipartFile file) {
        UserProfileDetail detail = detailOf(account(username));
        String previous = detail.getAvatarAsset();
        detail.changeAvatar(storage.store(MediaPurpose.AVATAR, file), LocalDateTime.now(clock));
        details.save(detail);
        deleteAfterCommit(previous);
    }

    @Transactional
    public void removeAvatar(String username) {
        AppUser user = account(username);
        details.findByUser(user).filter(UserProfileDetail::hasAvatar).ifPresent(detail -> {
            String previous = detail.getAvatarAsset();
            detail.changeAvatar(null, LocalDateTime.now(clock));
            details.save(detail);
            deleteAfterCommit(previous);
        });
    }

    @Transactional
    public void updateNotifications(String username, boolean notifyOrderEmail) {
        UserProfileDetail detail = detailOf(account(username));
        detail.changeNotifications(notifyOrderEmail, LocalDateTime.now(clock));
        details.save(detail);
    }

    @Transactional(readOnly = true)
    public Optional<AvatarRef> avatarRef(String username) {
        return details.findByUser(account(username)).filter(UserProfileDetail::hasAvatar)
                .map(detail -> new AvatarRef(version(detail), detail.getAvatarAsset()));
    }

    public CloudMediaStorageService.MediaFile readAvatar(AvatarRef ref) {
        return storage.read(ref.asset(), MediaPurpose.AVATAR);
    }

    /** Email cập nhật vận đơn: chỉ không gửi khi người dùng đã tắt rõ ràng. */
    @Transactional(readOnly = true)
    public boolean wantsOrderEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return !details.orderEmailPreferences(email.strip()).contains(Boolean.FALSE);
    }

    private AppUser account(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (user.getRole() != Role.USER && user.getRole() != Role.SHIPPER)
            throw new IllegalArgumentException("Trang này chỉ dành cho người gửi và Shipper.");
        return user;
    }

    private UserProfileDetail detailOf(AppUser user) {
        return details.findByUser(user).orElseGet(() -> new UserProfileDetail(user, LocalDateTime.now(clock)));
    }

    private Stats stats(AppUser user) {
        boolean shipper = user.getRole() == Role.SHIPPER;
        Map<Order.OrderStatus, Long> byStatus = new EnumMap<>(Order.OrderStatus.class);
        for (var row : shipper ? orders.countShipperStatuses(user) : orders.countSenderStatuses(user))
            byStatus.put(row.status(), row.count());
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long delivered = byStatus.getOrDefault(Order.OrderStatus.DELIVERED, 0L);
        long returned = byStatus.getOrDefault(Order.OrderStatus.RETURNED, 0L);
        long cancelled = byStatus.getOrDefault(Order.OrderStatus.CANCELLED, 0L);
        return new Stats(total, total - delivered - returned - cancelled, delivered, returned, cancelled,
                DeliverySuccessRate.calculate(delivered, returned), shipper ? 0 : addresses.countByUser(user));
    }

    /** Phiên bản ảnh cho URL và ETag: mốc lưu gần nhất (mili giây), không lặp lại kể cả khi database được tạo lại. */
    private static long version(UserProfileDetail detail) {
        LocalDateTime at = detail.getUpdatedAt();
        return at == null ? detail.getRevision() + 1 : at.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String text = value.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s{2,}", " ");
        if (text.isEmpty()) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private void deleteAfterCommit(String asset) {
        if (asset == null || !storage.isReference(asset, MediaPurpose.AVATAR)) return;
        Runnable delete = () -> {
            try { gateway.deleteNewUpload(CloudMediaReference.parse(asset)); }
            catch (RuntimeException ex) { log.warn("Không xóa được ảnh đại diện cũ trên cloud; có thể dọn sau."); }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { delete.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { delete.run(); }
        });
    }
}
