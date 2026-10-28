(function (root, factory) {
    'use strict';
    var createStatusPoller = factory();
    if (typeof module === 'object' && module.exports) module.exports = createStatusPoller;
    if (!root.document) return;
    root.document.querySelectorAll('[data-money]').forEach(function (node) {
        var amount = Number(node.textContent);
        if (Number.isSafeInteger(amount) && amount >= 0) node.textContent = new Intl.NumberFormat('vi-VN').format(amount) + ' ₫';
    });
    var checkout = root.document.querySelector('[data-checkout]');
    if (!checkout) return;
    var button = checkout.querySelector('[data-confirm-button]'), form = checkout.querySelector('[data-confirm-form]');
    var feedback = checkout.querySelector('[data-feedback]'), timer = root.document.querySelector('[data-countdown]');
    var terminal = false, submitting = false, countdownTimer = null;
    var expiresAt = Number(checkout.dataset.expires);
    function expired() { return !Number.isFinite(expiresAt) || expiresAt <= Math.floor(Date.now() / 1000); }
    function stopCountdown() { root.clearTimeout(countdownTimer); countdownTimer = null; }
    function closePayment(message) {
        terminal = true; form.hidden = true; button.disabled = true; feedback.textContent = message;
        stopCountdown(); poller.stop();
    }
    var poller = createStatusPoller({
        url: checkout.dataset.statusUrl, fetch: root.fetch.bind(root),
        onUpdate: function (data) {
            if (data.state === 'PAID' || data.state === 'ALREADY_PAID') {
                closePayment(data.message); root.location.reload();
            } else if (data.state === 'REPLACED' || data.state === 'UNAVAILABLE') closePayment(data.message);
            else {
                feedback.textContent = data.state === 'RETRY' ? data.message : '';
                if (!submitting) button.disabled = expired() || !root.navigator.onLine;
            }
        }
    });
    function tick() {
        stopCountdown();
        var left = Math.max(0, expiresAt - Math.floor(Date.now() / 1000));
        if (timer) timer.textContent = Number.isFinite(left) ? Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0') : '0:00';
        if (expired()) { closePayment('Phiên đã hết hạn. Hãy tạo QR mới tại trang đơn hàng.'); return; }
        if (!terminal && !submitting && !root.document.hidden) countdownTimer = root.setTimeout(tick, 1000);
    }
    function sync() {
        if (terminal || submitting) return;
        if (root.document.hidden || !root.navigator.onLine) {
            poller.stop(); stopCountdown();
            if (!root.navigator.onLine) { button.disabled = true; feedback.textContent = 'Mất kết nối. Chưa xác minh được trạng thái thanh toán.'; }
        } else { tick(); if (!terminal) poller.start(); }
    }
    form.addEventListener('submit', function (event) {
        if (event.defaultPrevented) return;
        if (terminal || submitting || expired() || !root.navigator.onLine) {
            event.preventDefault(); feedback.textContent = 'Không thể gửi lại: QR đã hết hiệu lực, đang xử lý hoặc mất kết nối.'; return;
        }
        submitting = true; poller.stop(); stopCountdown(); button.disabled = true; button.textContent = 'Đang xử lý giao dịch…';
    });
    root.document.addEventListener('visibilitychange', sync);
    root.addEventListener('online', sync); root.addEventListener('offline', sync);
    root.addEventListener('pagehide', function () { poller.stop(); stopCountdown(); });
    root.addEventListener('pageshow', function (event) {
        if (event.persisted && !terminal) { submitting = false; button.textContent = button.dataset.label || 'Xác nhận thanh toán'; sync(); }
    });
    sync();
}(typeof window === 'undefined' ? globalThis : window, function () {
    'use strict';
    return function createStatusPoller(options) {
        var setTimer = options.setTimeout || setTimeout, clearTimer = options.clearTimeout || clearTimeout;
        var timer = null, request = null, requestTimeout = null, active = false, generation = 0, failures = 0;
        function clear() { clearTimer(timer); timer = null; }
        async function poll() {
            clear(); if (!active || request) return;
            var run = generation, controller = new AbortController(); request = controller;
            var timeout = setTimer(function () { controller.abort(); }, 8000); requestTimeout = timeout;
            try {
                var response = await options.fetch(options.url, {signal: controller.signal, credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}});
                if (!active || generation !== run) return;
                if (response.redirected) throw new Error('Unexpected redirect');
                if (![200, 409, 410].includes(response.status)) throw new Error('Cannot verify checkout');
                var data = await response.json();
                if (!active || generation !== run) return;
                if (!data || typeof data.message !== 'string' || data.message.length > 500
                        || !['UNPAID', 'PAID', 'ALREADY_PAID', 'REPLACED', 'UNAVAILABLE'].includes(data.state)
                        || (response.status === 200 && ['REPLACED', 'UNAVAILABLE'].includes(data.state))
                        || (response.status !== 200 && !['REPLACED', 'UNAVAILABLE', 'ALREADY_PAID'].includes(data.state))) throw new Error('Invalid checkout response');
                failures = 0;
                if (data.state !== 'UNPAID') active = false;
                options.onUpdate(data);
            } catch (error) {
                if (active && generation === run) {
                    failures++;
                    options.onUpdate({state: 'RETRY', message: 'Chưa xác minh được trạng thái. Đang thử lại; không ghi nhận thành công khi mất kết nối.'});
                }
            } finally {
                clearTimer(timeout); requestTimeout = null; request = null;
                if (active) timer = setTimer(poll, generation !== run ? 0 : Math.min(15000, 3000 * Math.pow(2, Math.min(failures, 3))));
            }
        }
        return {
            start: function () { active = true; poll(); },
            stop: function () { active = false; generation++; clear(); clearTimer(requestTimeout); requestTimeout = null; if (request) request.abort(); }
        };
    };
}));
