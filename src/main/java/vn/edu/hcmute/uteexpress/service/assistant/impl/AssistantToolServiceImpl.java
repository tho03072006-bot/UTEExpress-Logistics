package vn.edu.hcmute.uteexpress.service.assistant.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.dto.assistant.ChatConversationResponse.LinkResponse;
import vn.edu.hcmute.uteexpress.dto.assistant.ChatOrderPageResponse.OrderOptionResponse;
import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusText;
import vn.edu.hcmute.uteexpress.service.*;
import vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import vn.edu.hcmute.uteexpress.service.assistant.*;

@Service
@Transactional(readOnly=true)
public class AssistantToolServiceImpl implements AssistantToolService {
    private final AssistantAccessService access;
    private final OrderService orders;
    private final TrackingService tracking;
    private final OrderJourneyService journey;
    private final OrderPaymentService payments;
    private final ShipperWorkspaceService workspace;
    private final OperationsPolicyService policies;
    private final vn.edu.hcmute.uteexpress.service.routing.RouteQuoteService routeQuotes;
    private final vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing;
    private final Map<AppUser.Role,String> knowledge;
    public AssistantToolServiceImpl(AssistantAccessService access,OrderService orders,TrackingService tracking,
            OrderJourneyService journey,OrderPaymentService payments,ShipperWorkspaceService workspace,
            OperationsPolicyService policies,vn.edu.hcmute.uteexpress.service.routing.RouteQuoteService routeQuotes,
            vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog,EntityManager entityManager,ObjectMapper mapper,Clock clock,
            vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing) throws java.io.IOException {
        this.routing=routing;
        this.access=access; this.orders=orders; this.tracking=tracking; this.journey=journey; this.payments=payments;
        this.workspace=workspace; this.policies=policies; this.routeQuotes=routeQuotes; this.catalog=catalog;
        this.entityManager=entityManager; this.mapper=mapper; this.clock=clock;
        knowledge=Map.of(AppUser.Role.USER,readGuide("user"),AppUser.Role.SHIPPER,readGuide("shipper"));
    }
    private String readGuide(String role) throws java.io.IOException {
        try (var input=new ClassPathResource("assistant/"+role+"-guide.txt").getInputStream()) {
            return new String(input.readNBytes(10000),StandardCharsets.UTF_8);
        }
    }
    /** Hướng dẫn tĩnh theo vai trò kèm mức giảm tự mang ra chi nhánh đang áp dụng (đọc lại mỗi lượt hỏi, Admin đổi là có hiệu lực ngay). */
    @Override public String getKnowledge(AssistantAccessService.Actor actor) {
        var discount=policies.current().getDropOffDiscount();
        return knowledge.get(actor.role()) + "MỨC GIẢM HIỆN HÀNH khi tự mang ra chi nhánh: "
                + String.format(Locale.ROOT, "%,d", discount.longValue()).replace(',', '.') + " đ cho mỗi đơn, áp dụng chung mọi chi nhánh.\r\n";
    }
    @Override public ArrayNode definitions(AssistantAccessService.Actor actor) {
        var result=mapper.createArrayNode();
        addTool(result,"lookup_order","Read CURRENT status, branch route (origin/destination branch, current hub location, route lead time), payment, history and read-only eligibility of an authorized order. Never use old chat as status evidence.",
                "{\"tracking_code\":{\"type\":\"string\",\"description\":\"Authorized tracking code from current selection/list. Omit to open account order picker; never ask the user to type a code.\"}}",List.of());
        addTool(result,"request_order_selection","Open account-scoped order picker when a specific order is needed and none has been selected. Do not ask the user to manually type a code.","{}",List.of());
        addTool(result,"list_my_orders","List up to 10 own/assigned orders, not other accounts. Ask which code if ambiguous.",
                "{\"status\":{\"type\":\"string\",\"enum\":[\"ALL\",\"PENDING_PICKUP\",\"PICKED_UP\",\"IN_TRANSIT\",\"DELIVERED\",\"FAILED\",\"CANCELLED\",\"RETURNED\"]},\"page\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":20}}",List.of());
        addTool(result,"estimate_fee","Read REFERENCE fee quotes from the same branch-route pricing used by the estimate page and order creation: "
                + "base fee by weight + distance surcharge inside one province, or route surcharge between branches, minus the drop-off discount. "
                + "Every quote row has courier_total_vnd (shipper picks up) and drop_off_total_vnd (sender brings the parcel to the sending branch counter); "
                + "always state the concrete amounts from these fields. Ask the user for sending and receiving province when unknown; never guess them. "
                + "Not an ETA of a shipment nor the charge after promo codes.",
                "{\"weight_kg\":{\"type\":\"number\",\"exclusiveMinimum\":0,\"maximum\":100},"
                + "\"from_branch\":{\"type\":\"string\",\"enum\":[\"SGN\",\"HAN\",\"DAD\"],\"description\":\"Sending province: SGN = TP. Hồ Chí Minh, HAN = Hà Nội, DAD = Đà Nẵng\"},"
                + "\"to_branch\":{\"type\":\"string\",\"enum\":[\"SGN\",\"HAN\",\"DAD\"],\"description\":\"Receiving province, same codes\"},"
                + "\"from_area\":{\"type\":\"string\",\"maxLength\":40,\"description\":\"Optional district/area name in the sending province, e.g. Thủ Đức, Quận 1\"},"
                + "\"to_area\":{\"type\":\"string\",\"maxLength\":40,\"description\":\"Optional district/area name in the receiving province, e.g. Cầu Giấy\"},"
                + "\"service_type\":{\"type\":\"string\",\"enum\":[\"STANDARD\",\"EXPRESS\",\"SUPER_EXPRESS\"]},"
                + "\"pickup_mode\":{\"type\":\"string\",\"enum\":[\"COURIER\",\"DROP_OFF\"],\"description\":\"Only chooses which amount total_vnd repeats; both amounts are always returned\"}}",
                List.of("weight_kg","from_branch","to_branch"));
        addTool(result,"get_guidance","Read official existing functions and CURRENT operations policy. Separate general packaging advice from official rules.","{}",List.of());
        if (actor.role()==AppUser.Role.SHIPPER) addTool(result,"get_shipper_work","Read current work counts and up to five priority orders assigned to the current shipper.","{}",List.of());
        return result;
    }
    private void addTool(ArrayNode result,String name,String description,String properties,List<String> required) {
        var function=result.addObject().put("type","function").putObject("function"); function.put("name",name).put("description",description);
        var parameters=function.putObject("parameters").put("type","object").put("additionalProperties",false);
        try { parameters.set("properties",mapper.readTree(properties)); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Schema công cụ chatbot không hợp lệ."); }
        parameters.set("required",mapper.valueToTree(required));
    }
    @Override public ToolOutput execute(String name,JsonNode arguments,Authentication authentication) {
        var actor=access.requireActor(authentication);
        if (arguments==null || !arguments.isObject() || arguments.size()>7) return invalid("Tham số tra cứu không hợp lệ.");
        try {
            return switch (name) {
                case "lookup_order" -> { fields(arguments,Set.of("tracking_code")); yield arguments.has("tracking_code") ? lookup(actor,text(arguments,"tracking_code")) : selectionRequired(); }
                case "request_order_selection" -> { fields(arguments,Set.of()); yield selectionRequired(); }
                case "list_my_orders" -> { fields(arguments,Set.of("status","page")); yield list(actor,arguments); }
                case "estimate_fee" -> { fields(arguments,Set.of("weight_kg","from_branch","to_branch","from_area","to_area","service_type","pickup_mode")); yield quote(arguments); }
                case "get_guidance" -> { fields(arguments,Set.of()); yield guidance(actor); }
                case "get_shipper_work" -> {
                    fields(arguments,Set.of());
                    if (actor.role()!=AppUser.Role.SHIPPER) yield invalid("Công cụ không thuộc vai trò hiện tại.");
                    var data=base(); data.set("counts",mapper.valueToTree(workspace.counts(actor.username())));
                    var rows=workspace.work(actor.username(),vn.edu.hcmute.uteexpress.dto.tracking.ShipperWorkView.TODAY,"",null,null,null,0);
                    data.set("priority_orders",summary(rows.getContent().stream().limit(5).toList(),actor));
                    yield new ToolOutput(data,List.of(new LinkResponse("Công việc của tôi","/shipper/trang-chu")),null);
                }
                default -> invalid("Công cụ không được cho phép. Chatbot chỉ đọc dữ liệu và hướng dẫn.");
            };
        } catch (IllegalArgumentException ex) { return invalid("Thiếu hoặc sai mã đơn, khối lượng, khoảng cách, trạng thái hay số trang. Hãy hỏi lại ngắn gọn."); }
    }
    @Override public OrderPage listOrderOptions(Authentication authentication,int page,String query) {
        var actor=access.requireActor(authentication);
        String keyword=query==null ? "" : query.strip().toUpperCase(Locale.ROOT);
        if(page<0 || page>1000 || !keyword.matches("[A-Z0-9-]{0,20}")) throw new AssistantFailure("INVALID_ORDER_FILTER",400,0);
        Page<Order> result=actor.role()==AppUser.Role.USER
                ? orders.searchOrdersOfUser(actor.username(),null,keyword,PageRequest.of(page,10,Sort.by("createdAt","id").descending()))
                : tracking.searchAssignedOrders(actor.username(),keyword,null,null,null,page);
        var options=new ArrayList<OrderOptionResponse>();
        for(var order:result.getContent()) {
            entityManager.refresh(order);
            var owner=actor.role()==AppUser.Role.USER ? order.getSender() : order.getShipper();
            if(owner!=null && actor.id().equals(owner.getId())) options.add(new OrderOptionResponse(order.getTrackingCode(),
                    OrderStatusText.of(order.getStatus()),order.getServiceType()==null ? "Chưa xác định" : order.getServiceType().getDisplayName(),
                    order.getCreatedAt()==null ? "" : order.getCreatedAt().toString()));
        }
        return new OrderPage(page,result.hasNext(),result.getTotalElements(),clock.instant(),List.copyOf(options));
    }
    private ToolOutput selectionRequired() {
        return new ToolOutput(base().put("ok",false).put("error","ORDER_SELECTION_REQUIRED")
                .put("message","Hãy dùng bộ chọn đơn trên tài khoản hiện tại, không yêu cầu gõ mã thủ công."),List.of(),null);
    }
    private ToolOutput lookup(AssistantAccessService.Actor actor,String raw) {
        String code=raw.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9-]{2,39}")) return invalid("Cần mã vận đơn hợp lệ, không phải URL hoặc tên người nhận.");
        var found=actor.role()==AppUser.Role.USER ? orders.findOwnedOrderByCode(code,actor.username()) : tracking.findAssignedOrderByCode(code,actor.username());
        if (found.isEmpty()) {
            var missing=base().put("ok",false).put("error","NOT_FOUND_OR_NOT_ALLOWED")
                    .put("message","Không tìm thấy đơn trong phạm vi tài khoản của bạn. Không xác nhận đơn có thuộc tài khoản khác hay không.");
            return new ToolOutput(missing,List.of(),null);
        }
        var order=found.get(); entityManager.refresh(order); journey.accessibleOrder(order.getId(),actor.username());
        var data=base(); data.put("tracking_code",order.getTrackingCode()).put("status",OrderStatusText.of(order.getStatus()));
        data.put("updated_at",String.valueOf(order.getUpdatedAt())).put("service",order.getServiceType()==null ? "Chưa xác định" : order.getServiceType().getDisplayName());
        data.putNull("estimated_delivery_at"); data.put("eta_note","Chưa có thời gian giao dự kiến cụ thể cho đơn này; mục tiêu SLA không phải lịch giao cam kết.");
        // Tuyến chi nhánh và vị trí hiện tại lấy từ cùng hàm với trang tra cứu, không suy đoán vị trí ở đây.
        routing.summary(order).ifPresent(route -> {
            var node=data.putObject("route"); node.put("origin_branch",route.originName()).put("dest_branch",route.destName());
            node.put("interprovincial",route.interprovincial()).put("drop_off",route.dropOff());
            if (route.location()!=null) node.put("current_location",route.location()); else node.putNull("current_location");
            if (route.leadTime()!=null) node.put("lead_time",route.leadTime()); else node.putNull("lead_time");
            if (route.counterAddress()!=null) node.put("counter_address",route.counterAddress());
            node.put("note","lead_time là thời gian dự kiến theo tuyến và dịch vụ, không phải giờ giao cam kết.");
        });
        data.put("can_cancel",actor.role()==AppUser.Role.USER && orders.canCancelOrder(order,actor.username()));
        if (actor.role()==AppUser.Role.SHIPPER) {
            data.put("can_retry_now",tracking.canRetry(order)); data.put("has_current_proof",tracking.hasCurrentProof(order));
            data.put("proof_required_before_delivery",true);
        }
        var payment=payments.findByOrder(order);
        if (payment.isPresent()) {
            var pay=payment.get(); entityManager.refresh(pay);
            var money=data.putObject("payment"); money.put("method",pay.getMethod().getDisplayName()).put("status",pay.getStatus().name());
            money.put("shipping_charge_vnd",pay.getAmount()); money.put("note","COD ở đây là cước vận chuyển; ví là mô phỏng. Không phải tiền hàng thu hộ.");
        } else data.put("payment_note","Chưa có thông tin thanh toán.");
        var history=journey.history(order.getId(),actor.username(),0); var entries=data.putArray("history_latest_first");
        for (var entry : history.stream().limit(5).toList()) {
            var item=entries.addObject().put("at",String.valueOf(entry.getOccurredAt())).put("status",OrderStatusText.of(entry.getStatus()));
            if (entry.getNextAttemptAt()!=null) item.put("next_attempt_at",entry.getNextAttemptAt().toString());
            if (entry.getFailureReason()!=null) item.put("reason",entry.getFailureReason().labelFor(entry.getStatus()==Order.OrderStatus.PENDING_PICKUP));
            // Không gửi ghi chú tự do, tên, địa chỉ, điện thoại hay danh tính người thao tác sang AI.
        }
        String path=actor.role()==AppUser.Role.USER ? "/nguoi-dung/don-hang/" : "/shipper/don/";
        return new ToolOutput(data,List.of(new LinkResponse("Xem đơn "+order.getTrackingCode(),path+order.getId())),order.getTrackingCode());
    }
    private ToolOutput list(AssistantAccessService.Actor actor,JsonNode args) {
        int page=0;
        if (args.has("page")) { if (!args.path("page").isIntegralNumber() || !args.path("page").canConvertToInt()) throw new IllegalArgumentException(); page=args.path("page").intValue(); }
        if (page<0 || page>20) throw new IllegalArgumentException();
        String status=args.has("status") ? text(args,"status") : "ALL";
        Order.OrderStatus filter=status.equals("ALL") ? null : Order.OrderStatus.valueOf(status);
        Page<Order> result=actor.role()==AppUser.Role.USER ? orders.searchOrdersOfUser(actor.username(),filter,"",PageRequest.of(page,10,Sort.by("createdAt","id").descending()))
                : tracking.searchAssignedOrders(actor.username(),"",filter,null,null,page);
        var data=base().put("page",result.getNumber()).put("total",result.getTotalElements()).put("has_next",result.hasNext());
        data.set("orders",summary(result.getContent(),actor));
        return new ToolOutput(data,List.of(new LinkResponse("Danh sách đơn của tôi",actor.role()==AppUser.Role.USER ? "/nguoi-dung/don-hang" : "/shipper/trang-chu")),null);
    }
    private ArrayNode summary(List<Order> values,AssistantAccessService.Actor actor) {
        var result=mapper.createArrayNode();
        for (var order : values.stream().limit(10).toList()) {
            entityManager.refresh(order);
            var owner=actor.role()==AppUser.Role.USER ? order.getSender() : order.getShipper();
            if (owner!=null && actor.id().equals(owner.getId()))
                result.addObject().put("tracking_code",order.getTrackingCode()).put("status",OrderStatusText.of(order.getStatus()));
        }
        return result;
    }
    /**
     * Cước tham khảo bằng đúng hàm của trang Ước tính cước và lúc tạo đơn (RouteQuoteService): cùng tuyến chi nhánh,
     * cùng phụ phí và mức giảm tự mang ra chi nhánh, nên con số trợ lý nói khớp với cước khi người dùng tạo đơn.
     */
    private ToolOutput quote(JsonNode args) {
        if (!args.path("weight_kg").isNumber()) throw new IllegalArgumentException();
        var from=branch(text(args,"from_branch")); var to=branch(text(args,"to_branch"));
        var type=args.has("service_type") ? Order.ServiceType.valueOf(text(args,"service_type")) : null;
        // Luôn trả cả cước Shipper đến lấy và cước tự mang ra chi nhánh trong một lần gọi: AI không phải gọi lần hai hay đoán mức giảm.
        var mode=args.has("pickup_mode") ? vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.valueOf(text(args,"pickup_mode"))
                : vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.COURIER;
        var notes=mapper.createArrayNode();
        var pickup=area(from,args.has("from_area") ? text(args,"from_area") : null,notes);
        var delivery=area(to,args.has("to_area") ? text(args,"to_area") : null,notes);
        var form=new vn.edu.hcmute.uteexpress.dto.FeeEstimateRequest();
        form.setPickupProvinceCode(from.getProvinceCode()); form.setPickupAreaCode(pickup.code());
        form.setDeliveryProvinceCode(to.getProvinceCode()); form.setDeliveryAreaCode(delivery.code());
        form.setWeightKg(args.path("weight_kg").doubleValue()); form.setServiceType(type);
        List<vn.edu.hcmute.uteexpress.service.routing.RouteQuoteService.Quote> quotes, dropOffs;
        try {
            form.setPickupMode(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.COURIER); quotes=routeQuotes.quotes(form);
            form.setPickupMode(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF); dropOffs=routeQuotes.quotes(form);
        } catch (IllegalArgumentException | IllegalStateException ex) { return invalid(ex.getMessage()); }
        boolean dropOff=mode==vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF;
        var data=base().put("reference_only",true).put("interprovincial",!from.getId().equals(to.getId()));
        data.put("from",from.getName()+" · "+pickup.name()).put("to",to.getName()+" · "+delivery.name()).put("pickup_mode",mode.name());
        data.put("counter_address",from.getName()+": "+from.getHubAddress());
        var rows=data.putArray("quotes");
        for (int i=0;i<quotes.size();i++) {
            var q=quotes.get(i); var d=dropOffs.get(i);
            var row=rows.addObject().put("service",q.serviceType().getDisplayName()).put("total_vnd",dropOff ? d.total() : q.total());
            row.put("courier_total_vnd",q.total()).put("drop_off_total_vnd",d.total()).put("drop_off_saving_vnd",d.dropOffDiscount());
            row.put("base_fee_vnd",q.baseFee()).put("distance_fee_vnd",q.distanceFee()).put("route_fee_vnd",q.routeFee());
            row.put("approx_distance_km",Math.round(q.distanceKm()*10)/10.0);
            row.put("lead_time",q.leadTimeText());
        }
        notes.add("Cùng cách tính với trang Ước tính cước và lúc tạo đơn; mã giảm giá trừ sau tổng này. Khoảng cách tính từ tâm khu vực, không phải km đường bộ.");
        notes.add("Hỏa tốc chỉ nhận trong cùng chi nhánh và không quá 30 km; lead_time là thời gian dự kiến của tuyến, không phải giờ giao cam kết.");
        data.set("notes",notes);
        return new ToolOutput(data,List.of(new LinkResponse("Ước tính cước","/uoc-tinh-cuoc")),null);
    }
    private vn.edu.hcmute.uteexpress.entity.routing.Branch branch(String code) {
        return catalog.branches().stream().filter(b -> b.getCode().equals(code)).findFirst().orElseThrow(IllegalArgumentException::new);
    }
    /** Khu vực theo tên người dùng gõ (bỏ dấu, bỏ "Quận/Huyện/TP"); không nhận ra thì dùng khu vực kho chi nhánh và ghi chú lại. */
    private vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService.AreaOption area(vn.edu.hcmute.uteexpress.entity.routing.Branch branch,
            String typed,ArrayNode notes) {
        var areas=catalog.areas().stream().filter(a -> a.branchCode().equals(branch.getCode())).toList();
        var hub=areas.stream().filter(a -> a.code().equals(branch.getHubAreaCode())).findFirst().orElseThrow(IllegalArgumentException::new);
        if (typed==null || typed.isBlank()) {
            notes.add("Chưa có khu vực ở "+branch.getName()+" nên tính theo khu vực kho "+hub.name()+".");
            return hub;
        }
        if (typed.length()>40) throw new IllegalArgumentException();
        String wanted=areaKey(typed);
        var match=areas.stream().filter(a -> areaKey(a.name()).equals(wanted)).findFirst();
        if (match.isPresent()) return match.get();
        notes.add("Không nhận ra khu vực \""+typed.strip()+"\" ở "+branch.getName()+" nên tính theo khu vực kho "+hub.name()+".");
        return hub;
    }
    static String areaKey(String value) {
        String plain=java.text.Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT).replace('đ','d'),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").strip();
        // "Quận 1", "Q1", "Khu vực 1" cùng một khu vực; bỏ tiền tố hành chính trước tên.
        return plain.replaceFirst("^(khu vuc|quan|huyen|thi xa|thanh pho|tp)\\s+","").replaceFirst("^q\\s*(?=\\d)","");
    }
    private ToolOutput guidance(AssistantAccessService.Actor actor) {
        var policy=policies.current(); entityManager.refresh(policy);
        var data=base().put("guide",getKnowledge(actor)); var current=data.putObject("current_policy");
        current.put("max_delivery_attempts",policy.getMaxDeliveryAttempts()).put("pickup_target_hours",policy.getPickupHours());
        current.put("standard_target_hours",policy.getStandardHours()).put("express_target_hours",policy.getExpressHours()).put("super_express_target_hours",policy.getSuperExpressHours());
        current.put("drop_off_discount_vnd",policy.getDropOffDiscount()).put("max_active_orders_per_shipper",policy.getMaxActiveOrders());
        current.put("note","Mục tiêu vận hành, không phải lịch hẹn/ETA cam kết của đơn. Ba hạn giao theo dịch vụ chỉ áp cho đơn cũ chưa có tuyến; "
                + "đơn có tuyến chi nhánh lấy hạn theo thời gian dự kiến của tuyến (lead_time của lookup_order/estimate_fee). "
                + "Điều kiện giao lại phải tra từng đơn bằng lookup_order.");
        return new ToolOutput(data,List.of(),null);
    }
    private ObjectNode base() { return mapper.createObjectNode().put("ok",true).put("checked_at",clock.instant().toString()); }
    private ToolOutput invalid(String message) { return new ToolOutput(base().put("ok",false).put("error","INVALID_OR_UNSUPPORTED_REQUEST").put("message",message),List.of(),null); }
    private String text(JsonNode args,String field) { if (!args.path(field).isTextual()) throw new IllegalArgumentException(); return args.path(field).textValue(); }
    private void fields(JsonNode args,Set<String> allowed) { args.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException(); }); }
}
