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
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from OperationsCase c where c.id = :id")
    Optional<OperationsCase> lockCase(@Param("id") Long id);
    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseResponse(c.id, c.kind, c.status, c.createdBy, c.createdAt)
        from OperationsCase c where (:kind is null or c.kind = :kind)
        """)
    Page<vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseResponse> search(@Param("kind") OperationsCase.Kind kind, Pageable pageable);
    List<OperationsCase> findByOrderAndKindAndStatus(Order order, OperationsCase.Kind kind, OperationsCase.Status status);
    boolean existsByOrderAndStatusIn(Order order, Collection<OperationsCase.Status> statuses);
    boolean existsByOrderAndKindAndStatusIn(Order order, OperationsCase.Kind kind, Collection<OperationsCase.Status> statuses);
    boolean existsByComplaintAndKindAndStatusIn(OrderComplaint complaint, OperationsCase.Kind kind, Collection<OperationsCase.Status> statuses);
    @Query("select coalesce(sum(c.amount), 0) from OperationsCase c where c.settlement = :settlement and c.kind = :kind and c.status in :statuses")
    BigDecimal sumAmounts(@Param("settlement") CodSettlement settlement, @Param("kind") OperationsCase.Kind kind,
            @Param("statuses") Collection<OperationsCase.Status> statuses);
}
