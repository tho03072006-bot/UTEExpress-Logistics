package vn.edu.hcmute.uteexpress.repository.admin;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
public interface OperationsCaseRepository extends JpaRepository<OperationsCase, Long> {
    /** Trong các đơn cho trước, đơn đã chi trả xong bồi thường: hồ sơ hàng mất/hư hỏng đã đóng. */
    @Query("""
        select distinct c.order.id from OperationsCase c
        where c.kind = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.COMPENSATION
          and c.status = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.COMPLETED and c.order.id in :ids
        """)
    List<Long> compensatedOrderIds(@Param("ids") Collection<Long> ids);
    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.tracking.OperationsProgressResponse(
          c.kind, c.status, c.amount, c.nextAttemptAt, c.updatedAt)
        from OperationsCase c left join c.complaint complaint
        where c.order.id = :orderId or complaint.order.id = :orderId
        """)
    Page<vn.edu.hcmute.uteexpress.dto.tracking.OperationsProgressResponse> findOrderProgress(
            @Param("orderId") Long orderId, Pageable pageable);

    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.tracking.OperationsProgressResponse(
          c.kind, c.status, c.amount, c.nextAttemptAt, c.updatedAt)
        from OperationsCase c where c.settlement.id = :settlementId
        """)
    Page<vn.edu.hcmute.uteexpress.dto.tracking.OperationsProgressResponse> findSettlementProgress(
            @Param("settlementId") Long settlementId, Pageable pageable);

    @Query("""
        select coalesce(sum(c.amount), 0) from OperationsCase c where c.settlement.shipper = :shipper
        and c.settlement.status = vn.edu.hcmute.uteexpress.entity.CodSettlement.Status.CONFIRMED
        and c.kind = :kind and c.status in :statuses
        """)
    BigDecimal sumAcceptedByShipper(@Param("shipper") AppUser shipper, @Param("kind") OperationsCase.Kind kind,
            @Param("statuses") Collection<OperationsCase.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from OperationsCase c where c.id = :id")
    Optional<OperationsCase> lockCase(@Param("id") Long id);
    /**
     * Hồ sơ theo đơn (giao lại, hoàn, bồi hoàn) thuộc chi nhánh gửi/nhận của đơn; phiếu tiền thuộc chi nhánh của Shipper.
     * branchId null là mọi chi nhánh. Hồ sơ "Chờ duyệt" luôn đứng đầu.
     */
    String CASE_FILTER = """
        from OperationsCase c left join c.order o left join c.settlement st left join st.shipper u
        where (:kind is null or c.kind = :kind) and (:status is null or c.status = :status)
          and (:branchId is null
            or (o.id is not null and (not exists (select r0.id from OrderRoute r0 where r0.order = o)
                or exists (select r1.id from OrderRoute r1 where r1.order = o and (r1.originBranch.id = :branchId or r1.destBranch.id = :branchId))))
            or (u.id is not null and (not exists (select sb0.id from StaffBranch sb0 where sb0.user = u)
                or exists (select sb1.id from StaffBranch sb1 where sb1.user = u and sb1.branch.id = :branchId))))
        """;
    @Query(value = "select new vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseResponse(c.id, c.kind, c.status, c.createdBy, c.createdAt) "
            + CASE_FILTER + " order by case when c.status = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.REQUESTED then 0 else 1 end,"
            + " c.createdAt desc, c.id desc",
            countQuery = "select count(c) " + CASE_FILTER)
    Page<vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseResponse> search(@Param("kind") OperationsCase.Kind kind,
            @Param("status") OperationsCase.Status status, @Param("branchId") Long branchId, Pageable pageable);
    List<OperationsCase> findByOrderAndKindAndStatus(Order order, OperationsCase.Kind kind, OperationsCase.Status status);
    boolean existsByOrderAndStatusIn(Order order, Collection<OperationsCase.Status> statuses);
    boolean existsByOrderAndKindAndStatusIn(Order order, OperationsCase.Kind kind, Collection<OperationsCase.Status> statuses);
    boolean existsByComplaintAndKindAndStatusIn(OrderComplaint complaint, OperationsCase.Kind kind, Collection<OperationsCase.Status> statuses);
    @Query("select coalesce(sum(c.amount), 0) from OperationsCase c where c.settlement = :settlement and c.kind = :kind and c.status in :statuses")
    BigDecimal sumAmounts(@Param("settlement") CodSettlement settlement, @Param("kind") OperationsCase.Kind kind,
            @Param("statuses") Collection<OperationsCase.Status> statuses);
}
