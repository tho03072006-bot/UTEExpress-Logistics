(function () {
    "use strict";

    const statusLabels = {
        PENDING_PICKUP: "Chờ lấy hàng", PICKED_UP: "Đã lấy hàng", IN_TRANSIT: "Đang giao",
        DELIVERED: "Giao thành công", FAILED: "Giao thất bại", CANCELLED: "Đã hủy", RETURNED: "Đã hoàn trả"
    };
    const trackingPattern = /^[A-Za-z0-9-]{1,40}$/;
    const subscriptions = new Map();
    const lastEvents = new Map();
    let client, reconnectTimer, refreshTimer, pollTimer, notice;
    let stopped = false, dirty = false, submitting = false, refreshing = false, refreshPending = false;
    let reconnectDelay = 3000;
    let connectedOnce = false;
    const hasWebSocket = typeof SockJS !== "undefined" && typeof Stomp !== "undefined";

    function trackedElements() { return Array.from(document.querySelectorAll("[data-tracking-code]")); }
    if (!trackedElements().length && !document.querySelector("[data-realtime-region]")) return;

    function showNotice(text) {
        if (!notice) {
            notice = document.createElement("div");
            notice.className = "container alert alert-info mt-3";
            notice.setAttribute("role", "status");
            const message = document.createElement("span");
            message.dataset.realtimeMessage = "true";
            notice.appendChild(message);
            const button = document.createElement("button");
            button.type = "button"; button.className = "btn btn-sm btn-outline-primary ms-2";
            button.textContent = "Tải lại dữ liệu";
            button.addEventListener("click", function () {
                if (!submitting && (!dirty || window.confirm("Tải lại sẽ bỏ dữ liệu chưa gửi. Bạn có muốn tiếp tục?"))) window.location.reload();
            });
            notice.appendChild(button);
            (document.querySelector("main") || document.body).prepend(notice);
        }
        notice.querySelector("[data-realtime-message]").textContent = text;
    }

    // Không thay form đang nhập, chữ ký/ảnh đang tải lên hoặc yêu cầu POST đang chạy.
    function mayRefresh() {
        const active = document.activeElement;
        return !dirty && !submitting && !document.querySelector(".modal.show")
                && !(active && /^(INPUT|SELECT|TEXTAREA)$/.test(active.tagName));
    }

    async function refreshRegions(quiet = false) {
        if (stopped || !document.querySelector("[data-realtime-region]")) return;
        if (!mayRefresh()) {
            if (!quiet) showNotice("Có cập nhật mới. Dữ liệu bạn đang nhập được giữ nguyên; tải lại khi sẵn sàng.");
            return;
        }
        if (refreshing) { refreshPending = true; return; }
        refreshing = true;
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 15000);
        try {
            const response = await fetch(window.location.href, {
                credentials: "same-origin", cache: "no-store", signal: controller.signal,
                headers: { "Accept": "text/html", "X-Requested-With": "XMLHttpRequest" }
            });
            if (!response.ok || response.redirected) throw new Error("Không lấy được trang đã xác thực.");
            const updated = new DOMParser().parseFromString(await response.text(), "text/html");
            if (stopped) return;
            if (!mayRefresh()) { showNotice("Có cập nhật mới; tải lại sau khi nhập xong."); return; }
            let changed = false;
            document.querySelectorAll("[data-realtime-region]").forEach(function (region) {
                const replacement = Array.from(updated.querySelectorAll("[data-realtime-region]"))
                        .find(candidate => candidate.dataset.realtimeRegion === region.dataset.realtimeRegion);
                if (!replacement) throw new Error("Trang cập nhật không còn vùng dữ liệu tương ứng.");
                if (stableHtml(region) !== stableHtml(replacement)) { region.replaceWith(replacement); changed = true; }
            });
            synchronizeSubscriptions();
            if (changed) {
                document.dispatchEvent(new Event("ute:regions-updated"));
                showNotice("Đã đồng bộ dữ liệu mới và các thao tác hiện được phép.");
            }
        } catch (error) {
            console.warn("Chưa đồng bộ được trang vận đơn.", error);
            showNotice("Có thay đổi nhưng chưa tải được dữ liệu mới. Hãy kiểm tra kết nối hoặc đăng nhập lại.");
        } finally {
            clearTimeout(timeout);
            refreshing = false;
            if (refreshPending) { refreshPending = false; scheduleRefresh(); }
        }
    }

    function scheduleRefresh() {
        clearTimeout(refreshTimer);
        refreshTimer = window.setTimeout(refreshRegions, 350);
    }

    function stableHtml(region) {
        const copy = region.cloneNode(true);
        // Spring đổi token CSRF đã che ở mỗi GET; không coi đó là thay đổi nghiệp vụ.
        copy.querySelectorAll('input[name="_csrf"]').forEach(input => input.removeAttribute("value"));
        copy.querySelectorAll("[data-realtime-volatile]").forEach(element => { element.textContent = ""; });
        // Trạng thái mở khối và các thuộc tính/hint do JS tạo không phải thay đổi từ server.
        // Nếu so cả chúng, mỗi lần polling sẽ đóng form vừa mở dù dữ liệu nghiệp vụ chưa thay đổi.
        copy.querySelectorAll("details[open]").forEach(element => element.removeAttribute("open"));
        copy.querySelectorAll(".sx-count-hint").forEach(element => element.remove());
        copy.querySelectorAll("[data-sx-schedule]").forEach(input => {
            input.removeAttribute("min"); input.removeAttribute("max"); input.classList.remove("is-invalid");
        });
        copy.querySelectorAll("[data-sx-note]").forEach(input => {
            input.removeAttribute("required"); input.removeAttribute("minlength");
        });
        return copy.outerHTML;
    }

    function updateStatus(event) {
        if (!event || !trackingPattern.test(event.trackingCode || "")
                || !Object.prototype.hasOwnProperty.call(statusLabels, event.status)) return;
        const matching = trackedElements().filter(element => element.dataset.trackingCode === event.trackingCode);
        if (!matching.length) return;
        const timestamp = Date.parse(event.updatedAt);
        const renderedTimes = matching.map(element => Date.parse(element.dataset.updatedAt)).filter(Number.isFinite);
        const rendered = renderedTimes.length ? Math.max(...renderedTimes) : -Infinity;
        const last = Math.max(lastEvents.get(event.trackingCode) || -Infinity, rendered);
        if (Number.isFinite(timestamp) && last !== undefined && timestamp < last) return;
        if (Number.isFinite(timestamp)) lastEvents.set(event.trackingCode, timestamp);
        matching.forEach(function (element) {
            const badge = element.querySelector("[data-order-status]");
            if (!badge) return;
            Object.keys(statusLabels).forEach(status => badge.classList.remove("badge-status-" + status.toLowerCase()));
            badge.classList.add("badge-status-" + event.status.toLowerCase());
            badge.textContent = statusLabels[event.status]; badge.dataset.orderStatus = event.status;
        });
        showNotice("Vận đơn " + event.trackingCode + " vừa có cập nhật: " + statusLabels[event.status] + ".");
        scheduleRefresh();
    }

    function synchronizeSubscriptions() {
        if (!client || !client.connected) return;
        const codes = new Set(trackedElements().map(element => element.dataset.trackingCode).filter(code => trackingPattern.test(code)));
        subscriptions.forEach(function (subscription, code) {
            if (!codes.has(code)) { subscription.unsubscribe(); subscriptions.delete(code); }
        });
        codes.forEach(function (code) {
            if (!subscriptions.has(code)) subscriptions.set(code, client.subscribe("/topic/order/" + code, function (message) {
                try { updateStatus(JSON.parse(message.body)); }
                catch (error) { console.warn("Không đọc được sự kiện trạng thái.", error); }
            }));
        });
    }

    function connect() {
        if (stopped) return;
        client = Stomp.over(new SockJS("/ws-tracking")); client.debug = null;
        client.connect({}, function () {
            subscriptions.clear(); reconnectDelay = 3000;
            synchronizeSubscriptions();
            // Bù các sự kiện đã bỏ lỡ trong thời gian mất kết nối.
            if (connectedOnce) scheduleRefresh();
            connectedOnce = true;
        }, function () {
            if (stopped) return;
            clearTimeout(reconnectTimer);
            reconnectTimer = window.setTimeout(connect, reconnectDelay);
            reconnectDelay = Math.min(reconnectDelay * 2, 30000);
        });
    }

    document.addEventListener("input", event => { if (event.target.closest("form")) dirty = true; });
    document.addEventListener("change", event => { if (event.target.closest("form")) dirty = true; });
    document.addEventListener("pointerdown", event => {
        if (event.target.tagName === "CANVAS" && event.target.closest("form")) dirty = true;
    });
    document.addEventListener("submit", event => {
        // Hộp xác nhận có thể chặn submit; chỉ khóa làm mới khi form thực sự được gửi.
        Promise.resolve().then(() => { if (!event.defaultPrevented) submitting = true; });
    });
    function startPolling() {
        clearInterval(pollTimer);
        // Mỗi máy có broker riêng: đối chiếu GET định kỳ để không bỏ lỡ thay đổi từ máy dùng chung Aiven.
        pollTimer = window.setInterval(() => { if (!document.hidden) return refreshRegions(true); }, 30000);
    }
    document.addEventListener("visibilitychange", () => { if (!document.hidden) refreshRegions(true); });
    window.addEventListener("pagehide", function () {
        stopped = true; clearTimeout(reconnectTimer); clearTimeout(refreshTimer); clearInterval(pollTimer);
        if (client && client.connected) client.disconnect();
    });
    window.addEventListener("pageshow", event => {
        if (event.persisted) {
            stopped = false; submitting = false; startPolling();
            if (hasWebSocket && trackedElements().length) connect();
        }
    });
    startPolling();
    if (hasWebSocket && trackedElements().length) connect();
}());
