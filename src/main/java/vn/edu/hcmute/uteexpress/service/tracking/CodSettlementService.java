package vn.edu.hcmute.uteexpress.service.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.CodTotals;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;

/**
 * Đối soát cước COD: Shipper lập phiếu nộp tiền cho các đơn đã thu, Manager xác nhận hoặc từ chối.
 * Phiếu bị từ chối trả các đơn về danh sách chờ nộp.
 */
@Service
@Transactional(readOnly = true)
public class CodSettlementService {
    public static final int MAX_ORDERS = 200;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999");
    private final LogisticsAccess access;
    private final CodSettlementRepository settlements;
    private final CodSettlementItemRepository items;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;

    public CodSettlementService(LogisticsAccess access, CodSettlementRepository settlements,
            CodSettlementItemRepository items, Clock clock, vn.edu.hcmute.uteexpress.service.routing.BranchScope scope) {
        this.access = access; this.settlements = settlements; this.items = items; this.clock = clock; this.scope = scope;
    }

    /** Tiền đang giữ (chưa nộp) và tiền đang chờ Manager xác nhận của Shipper. */
    public record Held(long count, BigDecimal amount, BigDecimal pendingAmount) {
        public boolean hasPending() { return pendingAmount.signum() > 0; }
    }

    public Held held(String username) {
        var shipper = access.shipper(username);
        CodTotals totals = items.unsettledTotals(shipper);
        BigDecimal pending = pendingOf(shipper).map(CodSettlement::getDeclaredAmount).orElse(BigDecimal.ZERO);
        return new Held(totals.count(), totals.amount(), pending);
    }

    public List<OrderPayment> unsettled(String username) {
        return items.unsettled(access.shipper(username), PageRequest.of(0, MAX_ORDERS));
    }

    public Optional<CodSettlement> pending(String username) {
        return pendingOf(access.shipper(username));
    }

    /** Trang đối soát cần phiếu chờ xác nhận ở hai chỗ (số tiền và cảnh báo): chỉ đọc một lần mỗi giao dịch. */
    private Optional<CodSettlement> pendingOf(AppUser shipper) {
        return TransactionCache.get("cod-pending:" + shipper.getId(), () ->
                settlements.findFirstByShipperAndStatusOrderByIdDesc(shipper, CodSettlement.Status.SUBMITTED));
    }

    public Page<CodSettlement> history(String username, int page) {
        return settlements.findByShipper(access.shipper(username),
                PageRequest.of(LogisticsInput.page(page), 10, Sort.by("submittedAt", "id").descending()));
    }

    /** Shipper chỉ xem phiếu của mình; phiếu người khác báo như không tồn tại. */
    public CodSettlement viewOwn(Long id, String username) {
        var shipper = access.shipper(username);
        return settlements.findById(id).filter(s -> s.getShipper().getId().equals(shipper.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp tiền."));
    }

    public CodSettlement viewAsManager(Long id, String username) {
        access.internal(username);
        var settlement = settlements.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp tiền."));
        requireBranch(username, settlement);
        return settlement;
    }
    /** Phiếu thuộc chi nhánh của Shipper nộp; Manager chi nhánh khác không xem hay duyệt được. */
    private void requireBranch(String username, CodSettlement settlement) {
        scope.requireShipper(username, settlement.getShipper());
    }

    public List<CodSettlementItem> items(CodSettlement settlement) {
        return items.findWithOrders(settlement);
    }

    public Page<CodSettlement> search(String username, CodSettlement.Status status, int page) { return search(username, status, null, page); }
    public Page<CodSettlement> search(String username, CodSettlement.Status status, Long branchId, int page) {
        access.internal(username);
        return settlements.search(status, scope.of(username, branchId), PageRequest.of(LogisticsInput.page(page), 10,
                Sort.by("submittedAt", "id").descending()));
    }

    public record ManagerSummary(long pendingCount, BigDecimal pendingAmount, long confirmedCount) { }

    public ManagerSummary managerSummary(String username) { return managerSummary(username, null); }
    public ManagerSummary managerSummary(String username, Long branchId) {
        access.internal(username);
        Long branch = scope.of(username, branchId);
        BigDecimal pending = settlements.sumDeclared(CodSettlement.Status.SUBMITTED, branch);
        return new ManagerSummary(settlements.countByStatus(CodSettlement.Status.SUBMITTED, branch),
                pending == null ? BigDecimal.ZERO : pending, settlements.countByStatus(CodSettlement.Status.CONFIRMED, branch));
    }

    /** Đọc số tiền người dùng nhập: chỉ chữ số, cho phép dấu chấm/phẩy/khoảng trắng phân tách hàng nghìn. */
    public static BigDecimal parseAmount(String raw) {
        String value = raw == null ? "" : raw.strip().replace('\u00a0', ' ').replaceAll("(?iu)\\s*(đ|vnd)$", "").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Hãy nhập số tiền nộp.");
        if (value.length() > 30 || !(value.matches("\\d{1,12}") || value.matches("\\d{1,3}(?:\\.\\d{3})+")
                || value.matches("\\d{1,3}(?:,\\d{3})+") || value.matches("\\d{1,3}(?: \\d{3})+")))
            throw new IllegalArgumentException("Số tiền là VND nguyên; dấu phân cách phải đúng nhóm 3 chữ số, không nhận số thập phân.");
        value = value.replaceAll("[., ]", "");
        if (!value.matches("\\d{1,12}")) throw new IllegalArgumentException("Số tiền nộp chỉ gồm chữ số, tối đa 12 chữ số.");
        return new BigDecimal(value);
    }

    @Transactional
    public CodSettlement submit(String username, List<Long> orderIds, BigDecimal declared, String note) {
        var shipper = access.shipper(username);
        if (orderIds == null || orderIds.stream().noneMatch(Objects::nonNull))
            throw new IllegalArgumentException("Hãy chọn ít nhất một đơn COD để nộp tiền.");
        var ids = new LinkedHashSet<>(orderIds.stream().filter(Objects::nonNull).toList());
        if (ids.size() > MAX_ORDERS) throw new IllegalArgumentException("Mỗi phiếu tối đa " + MAX_ORDERS + " đơn.");
        if (ids.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("Mã đơn không hợp lệ.");
        String normalizedNote = LogisticsInput.text(note, 0, 500, "Ghi chú");
        settlements.lockShipper(shipper.getId());
        if (settlements.existsByShipperAndStatus(shipper, CodSettlement.Status.SUBMITTED))
            throw new IllegalArgumentException("Bạn đang có phiếu chờ Manager xác nhận. Hãy đợi xác nhận trước khi lập phiếu mới.");
        var payments = items.unsettledIn(shipper, ids);
        if (payments.size() != ids.size())
            throw new IllegalArgumentException("Có đơn không còn hợp lệ để nộp (đã nộp, chưa giao thành công hoặc không phải COD đã thu). Hãy tải lại trang.");
        BigDecimal total = payments.stream().map(OrderPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || total.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Tổng cước phải dương và tối đa 12 chữ số. Hãy chia thành các phiếu nhỏ hơn.");
        if (declared == null) throw new IllegalArgumentException("Hãy nhập số tiền nộp.");
        if (declared.signum() <= 0 || declared.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Số tiền nộp phải là số nguyên lớn hơn 0.");
        if (declared.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Số tiền nộp vượt giới hạn cho phép.");
        if (declared.compareTo(total) != 0 && normalizedNote.length() < 10)
            throw new IllegalArgumentException("Số tiền nộp khác tổng cước COD; hãy ghi rõ lý do (ít nhất 10 ký tự).");
        var settlement = settlements.save(new CodSettlement(shipper, payments.size(), total, declared,
                normalizedNote.isEmpty() ? null : normalizedNote, LocalDateTime.now(clock)));
        try {
            items.saveAllAndFlush(payments.stream()
                    .map(payment -> new CodSettlementItem(settlement, payment.getOrder(), payment.getAmount())).toList());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Một số đơn vừa được đưa vào phiếu khác. Hãy tải lại trang.");
        }
        return settlement;
    }

    @Transactional
    public CodSettlement review(String username, Long id, long revision, boolean approve, String note) {
        var manager = access.internal(username);
        var settlement = settlements.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp tiền."));
        requireBranch(username, settlement);
        if (settlement.getRevision() != revision) throw new IllegalArgumentException("Phiếu đã thay đổi. Hãy tải lại trang.");
        if (settlement.getStatus() != CodSettlement.Status.SUBMITTED)
            throw new IllegalArgumentException("Phiếu đã được xử lý trước đó.");
        String text = approve ? LogisticsInput.text(note, 0, 500, "Ghi chú") : LogisticsInput.text(note, 5, 500, "Lý do từ chối");
        if (approve && settlement.hasDifference() && text.length() < 5)
            throw new IllegalArgumentException("Phiếu có chênh lệch tiền; hãy ghi chú xác nhận (ít nhất 5 ký tự).");
        if (!approve) items.deleteBySettlement(settlement);
        settlement.review(manager, approve, text.isEmpty() ? null : text, LocalDateTime.now(clock));
        return settlements.saveAndFlush(settlement);
    }
}
