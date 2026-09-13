(function () {
    "use strict";

    const statusLabels = {
        PENDING_PICKUP: "Chờ lấy hàng",
        PICKED_UP: "Đã lấy hàng",
        IN_TRANSIT: "Đang giao",
        DELIVERED: "Giao thành công",
        FAILED: "Giao thất bại",
        CANCELLED: "Đã hủy",
        RETURNED: "Đã hoàn trả"
    };

    const trackedElements = document.querySelectorAll(
            "[data-tracking-code]");

    if (trackedElements.length === 0
            || typeof SockJS === "undefined"
            || typeof Stomp === "undefined") {
        return;
    }

    const trackingCodes = new Set();

    trackedElements.forEach(function (element) {
        const trackingCode = element.dataset.trackingCode;

        if (trackingCode) {
            trackingCodes.add(trackingCode);
        }
    });

    function updateStatus(event) {
        if (!event || !event.trackingCode || !event.status) {
            return;
        }

        trackedElements.forEach(function (element) {
            if (element.dataset.trackingCode !== event.trackingCode) {
                return;
            }

            const badge = element.querySelector("[data-order-status]");

            if (!badge) {
                return;
            }

            Object.keys(statusLabels).forEach(function (status) {
                badge.classList.remove(
                        "badge-status-" + status.toLowerCase());
            });

            badge.classList.add(
                    "badge-status-" + event.status.toLowerCase());
            badge.textContent = statusLabels[event.status] || event.status;
            badge.dataset.orderStatus = event.status;
        });

        const message = document.getElementById(
                "realtimeStatusMessage");

        if (message) {
            message.textContent = "Trạng thái vừa được cập nhật: "
                    + (statusLabels[event.status] || event.status) + ".";
            message.classList.remove("d-none");
        }
    }

    function connect() {
        const socket = new SockJS("/ws-tracking");
        const client = Stomp.over(socket);

        client.debug = null;

        client.connect({}, function () {
            trackingCodes.forEach(function (trackingCode) {
                client.subscribe(
                        "/topic/order/" + trackingCode,
                        function (message) {
                            try {
                                updateStatus(JSON.parse(message.body));
                            } catch (error) {
                                console.error(
                                        "Không đọc được sự kiện trạng thái.",
                                        error);
                            }
                        });
            });
        }, function () {
            window.setTimeout(connect, 3000);
        });
    }

    connect();
}());
