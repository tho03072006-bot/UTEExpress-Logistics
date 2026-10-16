package vn.edu.hcmute.uteexpress.repository.admin;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public interface AdminUserRepository extends Repository<AppUser, Long> {
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    AppUser save(AppUser user);
    Optional<AppUser> findById(Long id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    void delete(AppUser user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockUser(Long id);

    @Query("select u from AppUser u where (:role is null or u.role = :role) order by u.id desc")
    Page<AppUser> findAccounts(AppUser.Role role, Pageable pageable);

    @Query("select count(o) from Order o where o.sender.id = :id or o.shipper.id = :id")
    long countOrders(Long id);
    @Query("select count(a) from SavedAddress a where a.user.id = :id")
    long countAddresses(Long id);
    @Query("select count(d) from DraftOrder d where d.user.id = :id")
    long countDrafts(Long id);
    @Query("select count(r) from ServiceReview r where r.user.id = :id")
    long countReviews(Long id);
    @Query("select count(p) from DeliveryProof p where p.shipper.id = :id")
    long countProofs(Long id);
    @Query("select count(e) from EmailChangeRequest e where e.user.id = :id")
    long countEmailChanges(Long id);
    @Query("select count(s) from StaffAccount s where s.issuedBy = :username")
    long countIssuedAccounts(String username);
    @Query("select count(h) from OrderStatusHistory h where h.actor.id = :id")
    long countJourneyActions(Long id);
    @Query("select count(c) from OrderComplaint c where c.handledBy.id = :id")
    long countHandledComplaints(Long id);
    @Query("select count(s) from CodSettlement s where s.shipper.id = :id or s.reviewedBy.id = :id")
    long countCashRecords(Long id);
    @Query("select count(o) from Order o where o.shipper.id = :id and o.status in :statuses")
    long countUnfinishedAssignments(Long id, java.util.Collection<vn.edu.hcmute.uteexpress.entity.Order.OrderStatus> statuses);
}
