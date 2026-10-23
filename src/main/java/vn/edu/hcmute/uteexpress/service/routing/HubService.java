package vn.edu.hcmute.uteexpress.service.routing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.admin.AuditService;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;

/**
 * Kho chi nhánh và chuyến trung chuyển liên tỉnh.
 * Kho gửi: xếp đơn đã nhập kho lên chuyến, niêm phong rồi xuất kho.
 * Kho nhận: đối chiếu niêm phong, quét từng kiện; kiện thiếu phải ghi chú và nhận bổ sung sau.
 * Quầy chi nhánh gửi: nhận hàng khách tự mang tới (DROP_OFF) thay cho chặng shipper lấy hàng.
 * Tài xế chỉ là thông tin của chuyến, không có tài khoản; mọi thao tác do Manager của đúng kho thực hiện.
 */
@Service
@Transactional(readOnly = true)
public class HubService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private static final DateTimeFormatter CODE_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private final LogisticsAccess access;
    private final DispatchRoutingPolicy routing;
    private final BranchRepository branches;
    private final OrderRouteRepository routes;
    private final LinehaulTripRepository trips;
    private final LinehaulItemRepository items;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final OrderPaymentService payments;

    public HubService(LogisticsAccess access, DispatchRoutingPolicy routing, BranchRepository branches, OrderRouteRepository routes,
            LinehaulTripRepository trips, LinehaulItemRepository items, OrderRepository orders, OrderStatusHistoryRepository history,
            AuditService audit, ApplicationEventPublisher events, Clock clock, OrderPaymentService payments) {
        this.access = access; this.routing = routing; this.branches = branches; this.routes = routes; this.trips = trips;
        this.items = items; this.orders = orders; this.history = history; this.audit = audit; this.events = events; this.clock = clock;
        this.payments = payments;
    }

    public record TripView(LinehaulTrip trip, List<LinehaulItem> items) { }
    /** waiting gồm đơn chiều đi đã nhập kho gửi và hàng hoàn (B9) đang ở kho nhận chờ chuyến về; returned: hàng hoàn đã về kho này. */
    public record Overview(Branch branch, List<Branch> branches, List<Branch> destinations, List<OrderRoute> waiting,
            List<TripView> loading, List<TripView> outgoing, List<TripView> incoming, List<TripView> arrived,
            List<LinehaulItem> missing, long waitingDelivery, List<OrderRoute> dropOffs, List<OrderRoute> returned) {
        /** Đơn đang chờ xếp chuyến tới một chi nhánh, để mỗi chi nhánh đến có một form tạo chuyến riêng. */
        public List<OrderRoute> waitingFor(Long destBranchId) {
            return waiting.stream().filter(r -> r.nextHub().getId().equals(destBranchId)).toList();
        }
    }

    /** Manager luôn xem kho của mình; Admin chọn kho, mặc định chi nhánh đầu tiên theo mã. */
    public Overview overview(String actor, Long branchId) {
        AppUser user = access.internal(actor);
        Branch branch = hubBranch(user, branchId);
        List<Branch> all = branches.findAll(org.springframework.data.domain.Sort.by("code"));
        List<Branch> destinations = all.stream().filter(b -> !b.getId().equals(branch.getId())).toList();
        var waiting = new ArrayList<OrderRoute>(routes.waitingAtOriginHub(branch.getId()));
        waiting.addAll(routes.waitingReturnAtDestHub(branch.getId()));
        return new Overview(branch, user.getRole() == AppUser.Role.ADMIN ? all : List.of(branch), destinations,
                List.copyOf(waiting),
                views(trips.findAllByFromBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.LOADING)),
                views(trips.findAllByFromBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.DEPARTED)),
                views(trips.findAllByToBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.DEPARTED)),
                views(trips.findTop10ByToBranch_IdAndStatusOrderByArrivedAtDesc(branch.getId(), LinehaulTrip.Status.ARRIVED)),
                items.findAllByTrip_ToBranch_IdAndTrip_StatusAndReceivedFalseOrderByIdDesc(branch.getId(), LinehaulTrip.Status.ARRIVED),
                routes.countByDestBranch_IdAndStageAndReturnLegFalse(branch.getId(), OrderRoute.Stage.DEST_HUB),
                routes.waitingDropOff(branch.getId()), routes.returnedAtOriginHub(branch.getId()));
    }

    /**
     * Khách tự mang hàng tới quầy chi nhánh gửi. Đơn liên tỉnh vào kho gửi chờ xếp chuyến;
     * đơn nội tỉnh chờ phân công Shipper giao luôn. Chỉ Manager của chi nhánh gửi (hoặc Admin) được nhận.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OrderRoute receiveAtCounter(String actor, Long orderId) {
        AppUser user = access.internal(actor);
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Mã vận đơn không hợp lệ.");
        Order order = lockedOrder(orderId);
        OrderRoute route = currentRoute(order);
        Branch branch = route.getOriginBranch();
        requireBranch(user, branch);
        if (!route.isDropOff()) throw new IllegalStateException("Đơn " + order.getTrackingCode() + " do Shipper đến lấy, không nhận tại quầy.");
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP || route.getStage() != OrderRoute.Stage.PICKUP)
            throw new IllegalStateException("Đơn " + order.getTrackingCode() + " đã được nhận hoặc không còn chờ khách mang tới.");
        payments.requireReadyForFulfillment(order);
        LocalDateTime now = LocalDateTime.now(clock);
        route.receiveAtCounter();
        order.setStatus(Order.OrderStatus.PICKED_UP);
        order.setUpdatedAt(now);
        orders.save(order);
        String next = route.isInterprovincial() ? "chờ xếp chuyến tới " + route.getDestBranch().getName() : "chờ phân công Shipper giao";
        history.save(new OrderStatusHistory(order, user, Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP, null,
                "Nhận hàng tại quầy " + branch.getName() + ", " + next + ".", null, now));
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getTrackingCode(), order.getStatus().name(), now));
        notifySender(order, SenderOrderUpdateEvent.Kind.RECEIVED_AT_COUNTER);
        audit.record(actor, "COUNTER_RECEIVE", "order:" + order.getId(), order.getTrackingCode() + "; quầy " + branch.getCode()
                + (route.isInterprovincial() ? "; đi " + route.getDestBranch().getCode() : "; nội tỉnh"));
        return route;
    }

    /** Tạo chuyến từ kho gửi tới một kho khác, gồm các đơn đã nhập kho và cùng chi nhánh đến. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LinehaulTrip createTrip(String actor, Long branchId, Long destBranchId, String plate, String driverName,
            String driverPhone, List<Long> orderIds) {
        AppUser user = access.internal(actor);
        Set<Long> ids = checkedIds(orderIds, false);
        Branch from = hubBranch(user, branchId);
        if (destBranchId == null || destBranchId <= 0) throw new IllegalArgumentException("Hãy chọn chi nhánh đến hợp lệ.");
        Branch to = branches.findById(destBranchId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh đến."));
        if (to.getId().equals(from.getId())) throw new IllegalArgumentException("Chi nhánh đến phải khác kho gửi.");
        String normalizedPlate = plate(plate);
        String name = LogisticsInput.text(driverName, 2, 100, "Tên tài xế");
        if (!name.matches(vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.NAME))
            throw new IllegalArgumentException("Tên tài xế phải có chữ cái, chỉ chứa chữ, khoảng trắng, chấm, gạch nối hoặc nháy.");
        String phone = phone(driverPhone);
        LocalDateTime now = LocalDateTime.now(clock);
        LinehaulTrip trip = trips.save(new LinehaulTrip(newCode(from, to, now), from, to, normalizedPlate, name, phone));
        for (Long id : ids) {
            Order order = lockedOrder(id);
            OrderRoute route = currentRoute(order);
            requireInHub(order, route);
            // Chiều đi: đơn ở kho gửi; chiều ngược (B9): hàng hoàn ở kho nhận, chuyến về kho gửi.
            var waitingStage = route.isReturnLeg() ? OrderRoute.Stage.DEST_HUB : OrderRoute.Stage.ORIGIN_HUB;
            if (route.getStage() != waitingStage || !route.currentBranch().getId().equals(from.getId()))
                throw new IllegalArgumentException("Vận đơn " + order.getTrackingCode() + " chưa nằm trong kho " + from.getName() + ".");
            if (!route.nextHub().getId().equals(to.getId()))
                throw new IllegalArgumentException("Vận đơn " + order.getTrackingCode() + " không đi tới " + to.getName() + ".");
            if (items.existsByOrder_IdAndTrip_Status(order.getId(), LinehaulTrip.Status.LOADING))
                throw new IllegalArgumentException("Vận đơn " + order.getTrackingCode() + " đã nằm trên một chuyến đang xếp hàng.");
            items.save(new LinehaulItem(trip, order));
        }
        trip.recount(ids.size());
        audit.record(actor, "TRIP_CREATE", "trip:" + trip.getId(), trip.getCode() + "; " + from.getCode() + " -> " + to.getCode()
                + "; " + ids.size() + " kiện; xe " + normalizedPlate);
        return trip;
    }

    /** Hủy chuyến khi chưa xuất kho; các đơn quay lại danh sách chờ xếp chuyến. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancelTrip(String actor, Long tripId, long revision) {
        AppUser user = access.internal(actor);
        LinehaulTrip trip = lockTrip(tripId, revision);
        requireBranch(user, trip.getFromBranch());
        if (trip.getStatus() != LinehaulTrip.Status.LOADING) throw new IllegalStateException("Chỉ hủy chuyến khi chưa xuất kho.");
        var tripItems = items.findAllByTrip_Id(trip.getId());
        items.deleteAll(tripItems);
        trips.delete(trip);
        audit.record(actor, "TRIP_CANCEL", "trip:" + trip.getId(), trip.getCode() + "; " + tripItems.size() + " kiện trả lại kho");
    }

    /** Niêm phong và xuất kho: mọi đơn trên chuyến chuyển sang đang trung chuyển. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void depart(String actor, Long tripId, long revision, String sealCode) {
        AppUser user = access.internal(actor);
        LinehaulTrip trip = lockTrip(tripId, revision);
        requireBranch(user, trip.getFromBranch());
        if (trip.getStatus() != LinehaulTrip.Status.LOADING) throw new IllegalStateException("Chuyến đã xuất kho hoặc đã tới nơi.");
        String seal = seal(sealCode);
        var tripItems = sortedItems(trip);
        LocalDateTime now = LocalDateTime.now(clock);
        for (LinehaulItem item : tripItems) {
            Order order = lockedOrder(item.getOrder().getId());
            OrderRoute route = currentRoute(order);
            requireInHub(order, route);
            requireTripRoute(route, trip);
            route.departLinehaul();
            touch(order, user, now, (route.isReturnLeg() ? "Hàng hoàn xuất kho " : "Xuất kho ") + trip.getFromBranch().getName() + " trên chuyến " + trip.getCode()
                    + " (xe " + trip.getVehiclePlate() + ") tới " + trip.getToBranch().getName() + ".",
                    route.isReturnLeg() ? SenderOrderUpdateEvent.Kind.RETURN_IN_LINEHAUL : SenderOrderUpdateEvent.Kind.IN_LINEHAUL);
        }
        trip.depart(seal, tripItems.size(), user, now);
        audit.record(actor, "TRIP_DEPART", "trip:" + trip.getId(), trip.getCode() + "; niêm phong " + seal + "; " + tripItems.size() + " kiện");
    }

    /**
     * Kho nhận chuyến: kiện có trong danh sách nhận chuyển sang chờ phân công giao; kiện thiếu giữ trạng thái trung chuyển.
     * Niêm phong lệch hoặc thiếu kiện đều bắt buộc ghi chú để truy vết.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int arrive(String actor, Long tripId, long revision, String sealCode, List<Long> receivedOrderIds, String note) {
        AppUser user = access.internal(actor);
        LinehaulTrip trip = lockTrip(tripId, revision);
        requireBranch(user, trip.getToBranch());
        if (trip.getStatus() != LinehaulTrip.Status.DEPARTED) throw new IllegalStateException("Chuyến chưa xuất kho hoặc đã được nhận.");
        String seal = seal(sealCode);
        boolean sealMatches = seal.equalsIgnoreCase(trip.getSealCode());
        Set<Long> received = checkedIds(receivedOrderIds, true);
        var tripItems = sortedItems(trip);
        var tripOrderIds = tripItems.stream().map(i -> i.getOrder().getId()).toList();
        if (!tripOrderIds.containsAll(received)) throw new IllegalArgumentException("Danh sách kiện nhận có đơn không thuộc chuyến này.");
        List<String> missing = new ArrayList<>();
        for (LinehaulItem item : tripItems) if (!received.contains(item.getOrder().getId())) missing.add(item.getOrder().getTrackingCode());
        String normalizedNote = LogisticsInput.text(note, 0, 400, "Ghi chú nhận chuyến");
        if ((!sealMatches || !missing.isEmpty()) && normalizedNote.length() < 10)
            throw new IllegalArgumentException("Niêm phong không khớp hoặc thiếu kiện: hãy ghi chú ít nhất 10 ký tự để truy vết.");
        LocalDateTime now = LocalDateTime.now(clock);
        for (LinehaulItem item : tripItems) {
            if (!received.contains(item.getOrder().getId())) continue;
            receive(item, user, now, trip);
        }
        StringBuilder arrivalNote = new StringBuilder();
        if (!sealMatches) arrivalNote.append("Niêm phong không khớp (nhận ").append(seal).append("). ");
        if (!missing.isEmpty()) arrivalNote.append("Thiếu ").append(missing.size()).append(" kiện: ").append(String.join(", ", missing)).append(". ");
        arrivalNote.append(normalizedNote);
        String finalNote = arrivalNote.toString().strip();
        trip.arrive(user, now, finalNote.length() > 500 ? finalNote.substring(0, 500) : (finalNote.isEmpty() ? null : finalNote));
        audit.record(actor, "TRIP_ARRIVE", "trip:" + trip.getId(), trip.getCode() + "; nhận " + received.size() + "/" + tripItems.size()
                + " kiện" + (sealMatches ? "" : "; niêm phong lệch"));
        return missing.size();
    }

    /** Kiện ghi thiếu lúc nhận chuyến, nay đã tìm thấy: nhận bổ sung vào kho nhận. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void receiveLate(String actor, Long itemId, String note) {
        AppUser user = access.internal(actor);
        if (itemId == null || itemId <= 0) throw new IllegalArgumentException("Mã kiện không hợp lệ.");
        LinehaulItem item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kiện."));
        LinehaulTrip trip = trips.findLockedById(item.getTrip().getId()).orElseThrow();
        entityManager.refresh(trip, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireBranch(user, trip.getToBranch());
        if (trip.getStatus() != LinehaulTrip.Status.ARRIVED) throw new IllegalStateException("Chuyến chưa được nhận.");
        if (item.isReceived()) throw new IllegalStateException("Kiện đã được nhận trước đó. Hãy tải lại trang.");
        String normalizedNote = LogisticsInput.text(note, 10, 400, "Ghi chú nhận bổ sung");
        receive(item, user, LocalDateTime.now(clock), trip);
        audit.record(actor, "TRIP_ITEM_LATE", "trip:" + trip.getId(), item.getOrder().getTrackingCode() + "; " + normalizedNote);
    }

    private void receive(LinehaulItem item, AppUser user, LocalDateTime now, LinehaulTrip trip) {
        Order order = lockedOrder(item.getOrder().getId());
        OrderRoute route = currentRoute(order);
        requireInHub(order, route);
        requireTripRoute(route, trip);
        route.arriveFromLinehaul();
        item.markReceived();
        if (route.isReturnLeg())
            touch(order, user, now, "Hàng hoàn nhập kho " + trip.getToBranch().getName() + " từ chuyến " + trip.getCode() + ", chờ trả người gửi.",
                    SenderOrderUpdateEvent.Kind.RETURN_AT_ORIGIN_HUB);
        else touch(order, user, now, "Nhập kho " + trip.getToBranch().getName() + " từ chuyến " + trip.getCode() + ", chờ phân công giao.",
                SenderOrderUpdateEvent.Kind.AT_DEST_HUB);
    }

    private void touch(Order order, AppUser user, LocalDateTime now, String note, SenderOrderUpdateEvent.Kind kind) {
        order.setUpdatedAt(now);
        orders.save(order);
        history.save(new OrderStatusHistory(order, user, order.getStatus(), order.getStatus(), null, note, null, now));
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getTrackingCode(), order.getStatus().name(), now));
        notifySender(order, kind);
    }

    private void notifySender(Order order, SenderOrderUpdateEvent.Kind kind) {
        AppUser sender = order.getSender();
        if (sender == null) return;
        String name = sender.getFullName() == null || sender.getFullName().isBlank() ? order.getSenderName() : sender.getFullName();
        String location = routes.findByOrder_Id(order.getId()).map(route -> OrderRoutingService.describe(order, route)).orElse(null);
        events.publishEvent(new SenderOrderUpdateEvent(sender.getEmail(), name, order.getTrackingCode(), kind, null, null, location));
    }

    private List<TripView> views(List<LinehaulTrip> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<LinehaulItem>> byTrip = new HashMap<>();
        items.findAllByTrip_IdIn(list.stream().map(LinehaulTrip::getId).toList())
                .forEach(item -> byTrip.computeIfAbsent(item.getTrip().getId(), k -> new ArrayList<>()).add(item));
        return list.stream().map(t -> new TripView(t, byTrip.getOrDefault(t.getId(), List.of()))).toList();
    }

    private Branch hubBranch(AppUser user, Long requested) {
        Long scoped = routing.scope(user, requested);
        if (scoped != null && scoped < 0) throw new AccessDeniedException("Tài khoản của bạn chưa được gán chi nhánh. Liên hệ Admin.");
        if (scoped == null) return branches.findAll(org.springframework.data.domain.Sort.by("code")).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa có chi nhánh nào."));
        return branches.findById(scoped).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
    }

    private void requireBranch(AppUser user, Branch branch) {
        Long scoped = routing.scope(user, null);
        if (scoped != null && !scoped.equals(branch.getId())) throw new AccessDeniedException("Bạn chỉ thao tác được kho của chi nhánh mình.");
    }

    private LinehaulTrip lockTrip(Long tripId, long revision) {
        if (tripId == null || tripId <= 0 || revision < 0) throw new IllegalArgumentException("Mã hoặc phiên bản chuyến không hợp lệ.");
        LinehaulTrip trip = trips.findLockedById(tripId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến."));
        entityManager.refresh(trip, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (trip.getRevision() != revision) throw new IllegalStateException("Chuyến vừa được cập nhật. Hãy tải lại trang.");
        return trip;
    }

    private Order lockedOrder(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Mã vận đơn không hợp lệ.");
        var order = orders.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        entityManager.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return order;
    }
    private OrderRoute currentRoute(Order order) {
        var route = routing.route(order);
        entityManager.refresh(route, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return route;
    }
    private void requireInHub(Order order, OrderRoute route) {
        var expected = route.isReturnLeg() ? Order.OrderStatus.FAILED : Order.OrderStatus.PICKED_UP;
        if (order.getStatus() != expected)
            throw new IllegalStateException("Vận đơn " + order.getTrackingCode() + " không còn ở trạng thái phù hợp để trung chuyển"
                    + (route.isReturnLeg() ? " hoàn hàng." : "."));
        payments.requireReadyForFulfillment(order);
    }
    private static void requireTripRoute(OrderRoute route, LinehaulTrip trip) {
        Branch from = route.isReturnLeg() ? route.getDestBranch() : route.getOriginBranch();
        if (!route.isInterprovincial() || !from.getId().equals(trip.getFromBranch().getId())
                || !route.nextHub().getId().equals(trip.getToBranch().getId()))
            throw new IllegalStateException("Tuyến của kiện không khớp chuyến trung chuyển.");
    }
    private List<LinehaulItem> sortedItems(LinehaulTrip trip) {
        return items.findAllByTrip_Id(trip.getId()).stream().sorted(Comparator.comparing(i -> i.getOrder().getId())).toList();
    }
    private static Set<Long> checkedIds(List<Long> raw, boolean allowEmpty) {
        if (raw == null || raw.isEmpty()) {
            if (allowEmpty) return Set.of();
            throw new IllegalArgumentException("Hãy chọn ít nhất một đơn để xếp lên chuyến.");
        }
        if (raw.size() > 500) throw new IllegalArgumentException("Mỗi danh sách tối đa 500 kiện.");
        if (raw.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("Danh sách có mã vận đơn không hợp lệ.");
        // Mọi thao tác nhiều kiện đều khóa theo thứ tự ID, tránh hai lô khóa chéo nhau.
        return new TreeSet<>(raw);
    }

    private String newCode(Branch from, Branch to, LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            // 48 bit ngẫu nhiên thay cho 90 lựa chọn/giây; giới hạn phần mã chi nhánh để luôn vừa VARCHAR(40).
            String fromCode = from.getCode().substring(0, Math.min(6, from.getCode().length()));
            String toCode = to.getCode().substring(0, Math.min(6, to.getCode().length()));
            String code = "TC-" + fromCode + toCode + "-" + now.format(CODE_TIME)
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (!trips.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Không tạo được mã chuyến. Hãy thử lại.");
    }

    static String plate(String value) {
        String plate = LogisticsInput.text(value, 5, 20, "Biển số xe").toUpperCase(Locale.ROOT).replace(" ", "");
        if (!plate.matches("[0-9]{2}[A-Z]{1,2}[0-9]?-?[0-9]{3}\\.?[0-9]{2}|[0-9]{2}[A-Z]{1,2}[0-9]?-?[0-9]{4}"))
            throw new IllegalArgumentException("Biển số xe không hợp lệ, ví dụ 51C-123.45 hoặc 29H-1234.");
        return plate;
    }

    static String phone(String value) {
        String phone = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.phone(LogisticsInput.text(value, 0, 20, "Số điện thoại tài xế"));
        if (!phone.matches(vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.PHONE))
            throw new IllegalArgumentException("Nhập số di động Việt Nam hợp lệ 10 số hoặc dạng +84 cho tài xế.");
        return phone;
    }

    static String seal(String value) {
        String seal = LogisticsInput.text(value, 4, 50, "Mã niêm phong").toUpperCase(Locale.ROOT);
        if (!seal.matches("(?=.*[A-Z0-9])[A-Z0-9-]+")) throw new IllegalArgumentException("Mã niêm phong phải có chữ hoặc số, chỉ gồm chữ, số và dấu gạch ngang.");
        return seal;
    }
}
