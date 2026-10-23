package vn.edu.hcmute.uteexpress.service.routing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    public record Overview(Branch branch, List<Branch> branches, List<Branch> destinations, List<OrderRoute> waiting,
            List<TripView> loading, List<TripView> outgoing, List<TripView> incoming, List<TripView> arrived,
            List<LinehaulItem> missing, long waitingDelivery, List<OrderRoute> dropOffs) {
        /** Đơn đang chờ xếp chuyến tới một chi nhánh, để mỗi chi nhánh đến có một form tạo chuyến riêng. */
        public List<OrderRoute> waitingFor(Long destBranchId) {
            return waiting.stream().filter(r -> r.getDestBranch().getId().equals(destBranchId)).toList();
        }
    }

    /** Manager luôn xem kho của mình; Admin chọn kho, mặc định chi nhánh đầu tiên theo mã. */
    public Overview overview(String actor, Long branchId) {
        AppUser user = access.internal(actor);
        Branch branch = hubBranch(user, branchId);
        List<Branch> all = branches.findAll(org.springframework.data.domain.Sort.by("code"));
        List<Branch> destinations = all.stream().filter(b -> !b.getId().equals(branch.getId())).toList();
        return new Overview(branch, user.getRole() == AppUser.Role.ADMIN ? all : List.of(branch), destinations,
                routes.waitingAtOriginHub(branch.getId()),
                views(trips.findAllByFromBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.LOADING)),
                views(trips.findAllByFromBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.DEPARTED)),
                views(trips.findAllByToBranch_IdAndStatusOrderByIdDesc(branch.getId(), LinehaulTrip.Status.DEPARTED)),
                views(trips.findTop10ByToBranch_IdAndStatusOrderByArrivedAtDesc(branch.getId(), LinehaulTrip.Status.ARRIVED)),
                items.findAllByTrip_ToBranch_IdAndTrip_StatusAndReceivedFalseOrderByIdDesc(branch.getId(), LinehaulTrip.Status.ARRIVED),
                routes.countByDestBranch_IdAndStage(branch.getId(), OrderRoute.Stage.DEST_HUB),
                routes.waitingDropOff(branch.getId()));
    }

    /**
     * Khách tự mang hàng tới quầy chi nhánh gửi. Đơn liên tỉnh vào kho gửi chờ xếp chuyến;
     * đơn nội tỉnh chờ phân công Shipper giao luôn. Chỉ Manager của chi nhánh gửi (hoặc Admin) được nhận.
     */
    @Transactional
    public OrderRoute receiveAtCounter(String actor, Long orderId) {
        AppUser user = access.internal(actor);
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Mã vận đơn không hợp lệ.");
        Order order = orders.findLockedById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        OrderRoute route = routing.route(order);
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
    @Transactional
    public LinehaulTrip createTrip(String actor, Long branchId, Long destBranchId, String plate, String driverName,
            String driverPhone, List<Long> orderIds) {
        AppUser user = access.internal(actor);
        Branch from = hubBranch(user, branchId);
        if (destBranchId == null) throw new IllegalArgumentException("Hãy chọn chi nhánh đến.");
        Branch to = branches.findById(destBranchId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh đến."));
        if (to.getId().equals(from.getId())) throw new IllegalArgumentException("Chi nhánh đến phải khác kho gửi.");
        String normalizedPlate = plate(plate);
        String name = LogisticsInput.text(driverName, 2, 100, "Tên tài xế");
        if (!name.matches("[\\p{L} .'-]+")) throw new IllegalArgumentException("Tên tài xế chỉ gồm chữ cái và khoảng trắng.");
        String phone = phone(driverPhone);
        Set<Long> ids = new LinkedHashSet<>(orderIds == null ? List.of() : orderIds);
        if (ids.isEmpty()) throw new IllegalArgumentException("Hãy chọn ít nhất một đơn để xếp lên chuyến.");
        if (ids.size() > 500) throw new IllegalArgumentException("Một chuyến xếp tối đa 500 kiện.");
        LocalDateTime now = LocalDateTime.now(clock);
        LinehaulTrip trip = trips.save(new LinehaulTrip(newCode(from, to, now), from, to, normalizedPlate, name, phone));
        for (Long id : ids) {
            Order order = orders.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
            OrderRoute route = routing.route(order);
            if (route.getStage() != OrderRoute.Stage.ORIGIN_HUB || !route.getOriginBranch().getId().equals(from.getId()))
                throw new IllegalArgumentException("Vận đơn " + order.getTrackingCode() + " chưa nằm trong kho " + from.getName() + ".");
            if (!route.getDestBranch().getId().equals(to.getId()))
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
    @Transactional
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
    @Transactional
    public void depart(String actor, Long tripId, long revision, String sealCode) {
        AppUser user = access.internal(actor);
        LinehaulTrip trip = lockTrip(tripId, revision);
        requireBranch(user, trip.getFromBranch());
        String seal = seal(sealCode);
        var tripItems = items.findAllByTrip_Id(trip.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        for (LinehaulItem item : tripItems) {
            Order order = orders.findLockedById(item.getOrder().getId()).orElseThrow();
            OrderRoute route = routing.route(order);
            route.departLinehaul();
            touch(order, user, now, "Xuất kho " + trip.getFromBranch().getName() + " trên chuyến " + trip.getCode()
                    + " (xe " + trip.getVehiclePlate() + ") tới " + trip.getToBranch().getName() + ".", SenderOrderUpdateEvent.Kind.IN_LINEHAUL);
        }
        trip.depart(seal, tripItems.size(), user, now);
        audit.record(actor, "TRIP_DEPART", "trip:" + trip.getId(), trip.getCode() + "; niêm phong " + seal + "; " + tripItems.size() + " kiện");
    }

    /**
     * Kho nhận chuyến: kiện có trong danh sách nhận chuyển sang chờ phân công giao; kiện thiếu giữ trạng thái trung chuyển.
     * Niêm phong lệch hoặc thiếu kiện đều bắt buộc ghi chú để truy vết.
     */
    @Transactional
    public int arrive(String actor, Long tripId, long revision, String sealCode, List<Long> receivedOrderIds, String note) {
        AppUser user = access.internal(actor);
        LinehaulTrip trip = lockTrip(tripId, revision);
        requireBranch(user, trip.getToBranch());
        if (trip.getStatus() != LinehaulTrip.Status.DEPARTED) throw new IllegalStateException("Chuyến chưa xuất kho hoặc đã được nhận.");
        String seal = seal(sealCode);
        boolean sealMatches = seal.equalsIgnoreCase(trip.getSealCode());
        Set<Long> received = new HashSet<>(receivedOrderIds == null ? List.of() : receivedOrderIds);
        var tripItems = items.findAllByTrip_Id(trip.getId());
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
    @Transactional
    public void receiveLate(String actor, Long itemId, String note) {
        AppUser user = access.internal(actor);
        LinehaulItem item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kiện."));
        LinehaulTrip trip = trips.findLockedById(item.getTrip().getId()).orElseThrow();
        requireBranch(user, trip.getToBranch());
        if (trip.getStatus() != LinehaulTrip.Status.ARRIVED) throw new IllegalStateException("Chuyến chưa được nhận.");
        String normalizedNote = LogisticsInput.text(note, 10, 400, "Ghi chú nhận bổ sung");
        receive(item, user, LocalDateTime.now(clock), trip);
        audit.record(actor, "TRIP_ITEM_LATE", "trip:" + trip.getId(), item.getOrder().getTrackingCode() + "; " + normalizedNote);
    }

    private void receive(LinehaulItem item, AppUser user, LocalDateTime now, LinehaulTrip trip) {
        Order order = orders.findLockedById(item.getOrder().getId()).orElseThrow();
        OrderRoute route = routing.route(order);
        route.arriveDestHub();
        item.markReceived();
        touch(order, user, now, "Nhập kho " + trip.getToBranch().getName() + " từ chuyến " + trip.getCode() + ", chờ phân công giao.",
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
        if (tripId == null || tripId <= 0) throw new IllegalArgumentException("Mã chuyến không hợp lệ.");
        LinehaulTrip trip = trips.findLockedById(tripId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến."));
        if (trip.getRevision() != revision) throw new IllegalStateException("Chuyến vừa được cập nhật. Hãy tải lại trang.");
        return trip;
    }

    private String newCode(Branch from, Branch to, LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "TC-" + from.getCode() + to.getCode() + "-" + now.format(CODE_TIME) + ThreadLocalRandom.current().nextInt(10, 100);
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
        String phone = LogisticsInput.text(value, 0, 20, "Số điện thoại tài xế").replace(" ", "");
        if (!phone.matches("0[0-9]{9}")) throw new IllegalArgumentException("Số điện thoại tài xế gồm 10 chữ số, bắt đầu bằng 0.");
        return phone;
    }

    static String seal(String value) {
        String seal = LogisticsInput.text(value, 4, 50, "Mã niêm phong").toUpperCase(Locale.ROOT);
        if (!seal.matches("[A-Z0-9-]+")) throw new IllegalArgumentException("Mã niêm phong chỉ gồm chữ, số và dấu gạch ngang.");
        return seal;
    }
}
