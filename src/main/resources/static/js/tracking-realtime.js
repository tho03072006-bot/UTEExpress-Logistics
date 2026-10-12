(function () {
    'use strict';

    var statusLabels = {
        PENDING_PICKUP: 'Chờ lấy hàng',
        PICKED_UP: 'Đã lấy hàng',
        IN_TRANSIT: 'Đang giao',
        DELIVERED: 'Giao thành công',
        FAILED: 'Giao thất bại',
        CANCELLED: 'Đã hủy',
        RETURNED: 'Đã hoàn trả'
    };
    var trackedElements = document.querySelectorAll('[data-tracking-code]');
    if (!trackedElements.length || typeof SockJS === 'undefined' || typeof Stomp === 'undefined') return;

    var trackingCodes = new Set();
    var unloading = false;
    trackedElements.forEach(function (element) {
        if (element.dataset.trackingCode) trackingCodes.add(element.dataset.trackingCode);
    });

    function currentStatus(badge) {
        if (badge.dataset.orderStatus) return badge.dataset.orderStatus;
        return Object.keys(statusLabels).find(function (status) {
            return badge.classList.contains('badge-status-' + status.toLowerCase());
        });
    }

    function requireReload(element, label) {
        var actionForms = element.querySelectorAll('form[data-order-action], form[action*="/trang-thai"], form[action*="/bang-chung"], form[action$="/huy"]');
        if (!actionForms.length) return;
        actionForms.forEach(function (form) {
            form.dataset.uteStale = 'true';
            form.querySelectorAll('button, input, textarea, select').forEach(function (control) {
                control.disabled = true;
            });
        });
        var message = element.querySelector('#realtimeStatusMessage, [data-realtime-reload]');
        if (!message) {
            message = document.createElement('div');
            message.className = 'alert alert-success';
            message.dataset.realtimeReload = 'true';
            element.prepend(message);
        }
        message.setAttribute('role', 'status');
        message.setAttribute('aria-live', 'polite');
        message.classList.remove('d-none');
        message.replaceChildren();
        var text = document.createElement('p');
        text.className = 'mb-2';
        text.textContent = 'Trạng thái vừa đổi thành ' + label + '. Làm mới để tiếp tục với thao tác phù hợp.';
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-primary';
        button.textContent = 'Làm mới đơn hàng';
        button.addEventListener('click', function () { window.location.reload(); });
        message.append(text, button);
    }

    document.addEventListener('submit', function (event) {
        if (event.target.dataset.uteStale === 'true') {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    function updateStatus(event) {
        if (!event || !trackingCodes.has(event.trackingCode) || !Object.prototype.hasOwnProperty.call(statusLabels, event.status)) return;
        trackedElements.forEach(function (element) {
            if (element.dataset.trackingCode !== event.trackingCode) return;
            var changed = false;
            element.querySelectorAll('[data-order-status]').forEach(function (badge) {
                if (currentStatus(badge) !== event.status) changed = true;
                Object.keys(statusLabels).forEach(function (status) {
                    badge.classList.remove('badge-status-' + status.toLowerCase());
                });
                badge.classList.add('badge-status-' + event.status.toLowerCase());
                badge.textContent = statusLabels[event.status];
                badge.dataset.orderStatus = event.status;
            });
            element.dataset.orderFilterStatus = event.status;
            if (changed) requireReload(element, statusLabels[event.status]);
            var cardAction = element.querySelector('.ute-shipper-card-actions .btn-primary');
            if (changed && cardAction) cardAction.textContent = 'Xem chi tiết mới';
        });
        document.dispatchEvent(new CustomEvent('ute:order-status-change'));
    }

    function connect() {
        if (unloading) return;
        var socket = new SockJS('/ws-tracking');
        var client = Stomp.over(socket);
        client.debug = null;
        client.connect({}, function () {
            trackingCodes.forEach(function (trackingCode) {
                client.subscribe('/topic/order/' + trackingCode, function (message) {
                    try { updateStatus(JSON.parse(message.body)); }
                    catch (error) { /* Ignore malformed events without logging event contents. */ }
                });
            });
        }, function () {
            if (!unloading) window.setTimeout(connect, 3000);
        });
    }
    window.addEventListener('pagehide', function () { unloading = true; });
    connect();
}());
