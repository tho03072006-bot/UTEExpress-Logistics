(function () {
    "use strict";
    const badges = document.querySelectorAll("[data-notification-count]");
    if (!badges.length) return;
    let previousCount = null;
    let sessionExpired = false;
    function showAssignment() {
        const banner = document.getElementById("shipperNotificationBanner");
        if (!banner) return;
        // Shipper đã tắt "thông báo nổi" ở trang tài khoản (lưu trên thiết bị): chỉ chuông đổi số.
        try { if (localStorage.getItem("ute-shipper-popup") === "off") return; } catch (e) { /* mặc định vẫn hiện */ }
        banner.querySelector("[data-notification-message]").textContent = "Bạn vừa nhận thông báo phân công vận đơn mới.";
        banner.hidden = false;
        // Đang ở tab khác và Shipper đã cho phép: hiện thêm thông báo của trình duyệt.
        try {
            if (document.hidden && "Notification" in window && Notification.permission === "granted"
                    && localStorage.getItem("ute-shipper-desktop") === "on") {
                const note = new Notification("UTEExpress · Đơn mới", {body: "Bạn vừa được phân công vận đơn mới.", tag: "ute-assignment", icon: "/images/shipper/notify-bell.svg"});
                note.onclick = function () { window.focus(); window.location.href = "/shipper/thong-bao"; note.close(); };
            }
        } catch (e) { /* trình duyệt không hỗ trợ: bỏ qua */ }
    }
    async function refresh() {
        if (sessionExpired) return;
        try {
            const response = await fetch("/shipper/thong-bao/chua-doc", {
                cache: "no-store", credentials: "same-origin", redirect: "error",
                headers: {"Accept": "application/json", "X-Requested-With": "XMLHttpRequest"}
            });
            if (response.status === 401) {
                sessionExpired = true;
                window.clearInterval(timer); window.clearTimeout(reconnect);
                if (client?.connected) client.disconnect();
                const banner = document.getElementById("shipperNotificationBanner");
                if (banner) {
                    banner.querySelector("[data-notification-message]").textContent = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                    const link = banner.querySelector("a");
                    link.href = "/dang-nhap"; link.textContent = "Đăng nhập";
                    banner.hidden = false;
                }
                return;
            }
            if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return;
            const data = await response.json();
            const count = Number(data.count);
            if (!Number.isSafeInteger(count) || count < 0) return;
            badges.forEach(badge => { badge.textContent = count > 99 ? "99+" : String(count); badge.hidden = count === 0; });
            // Chấm đỏ: rung chuông khi có thông báo mới (lần đầu tải trang có thư chưa đọc cũng nhắc một lần).
            const isNew = count > 0 && (previousCount === null || count > previousCount);
            document.querySelectorAll("[data-notification-bell]").forEach(bell => {
                bell.classList.toggle("sx-has-unread", count > 0);
                bell.setAttribute("aria-label", count > 0 ? "Thông báo nhận đơn: " + count + " chưa đọc" : "Thông báo nhận đơn");
                if (isNew) { bell.classList.remove("sx-ring"); void bell.offsetWidth; bell.classList.add("sx-ring"); }
            });
            document.title = (count > 0 ? "(" + (count > 99 ? "99+" : count) + ") " : "") + document.title.replace(/^\(\d+\+?\) /, "");
            if (previousCount !== null && count > previousCount) {
                showAssignment();
            }
            previousCount = count;
        } catch (_) { /* Lần kiểm tra sau sẽ thử lại khi có mạng. */ }
    }
    refresh();
    const timer = window.setInterval(refresh, 30000);
    let client;
    let reconnect;
    function connect() {
        if (sessionExpired) return;
        if (typeof SockJS === "undefined" || typeof Stomp === "undefined") return;
        client = Stomp.over(new SockJS("/ws-tracking"));
        client.debug = null;
        client.connect({}, function () {
            client.subscribe("/user/queue/shipper-notifications", function () {
                showAssignment();
                refresh();
            });
            refresh();
        }, function () { if (!sessionExpired) reconnect = window.setTimeout(connect, 5000); });
    }
    connect();
    window.addEventListener("pagehide", function () {
        window.clearInterval(timer); window.clearTimeout(reconnect);
        if (client?.connected) client.disconnect();
    });
}());
