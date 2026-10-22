(function (root, factory) {
    'use strict';
    var createPoller = factory();
    if (typeof module === 'object' && module.exports) module.exports = createPoller;
    if (!root.document) return;
    var node = root.document.querySelector('[data-momo-payment]');
    if (!node) return;
    var status = node.querySelector('[data-momo-status]');
    var reference = node.querySelector('[data-momo-reference]');
    var countdown = node.querySelector('[data-momo-countdown]');
    var check = node.querySelector('[data-momo-check]');
    var terminal = false;
    var poller = createPoller({
        fetch: root.fetch.bind(root), url: node.dataset.statusUrl, expiresAt: Number(node.dataset.expires),
        onUpdate: function (data) {
            status.textContent = data.message;
            var render = node.querySelector('[data-momo-render-status]');
            if (render && data.render && typeof data.render.message === 'string') { render.textContent = data.render.message; render.dataset.state = String(data.render.state || ''); }
            if (data.state === 'PAID') {
                terminal = true; status.className = 'alert alert-success mt-4';
                if (render) render.hidden = true;
                reference.hidden = false; reference.textContent = 'Mã giao dịch mô phỏng: ' + (data.transactionRef || '—');
            } else if (['CLOSED', 'UNAVAILABLE', 'EXPIRED', 'UNAUTHORIZED', 'NOT_FOUND'].includes(data.state)) terminal = true;
            if (terminal) {
                node.querySelector('[data-checkout-controls]').hidden = true;
                node.querySelector('[data-momo-refresh]').hidden = data.state !== 'EXPIRED';
                check.hidden = data.state !== 'EXPIRED';
            }
        }
    });
    function sync() { if (root.document.hidden || !root.navigator.onLine) poller.pause(); else if (!terminal) poller.resume(); }
    check.addEventListener('click', function () { if (root.navigator.onLine && !root.document.hidden) poller.check(); });
    root.document.addEventListener('visibilitychange', sync); root.addEventListener('online', sync);
    root.addEventListener('offline', function () { poller.pause(); status.textContent = 'Mất kết nối. Chưa thể xác minh thanh toán; hệ thống sẽ kiểm tra khi có mạng.'; });
    root.addEventListener('pagehide', function () { poller.stop(); });
    root.addEventListener('pageshow', function (event) { if (event.persisted && !terminal) { poller.start(); sync(); } });
    function tick() {
        var seconds = Math.max(0, Number(node.dataset.expires) - Math.floor(Date.now() / 1000));
        countdown.textContent = Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
        if (seconds && !terminal) root.setTimeout(tick, 1000);
    }
    if (!root.document.hidden && root.navigator.onLine) poller.start();
    tick();
}(typeof window === 'undefined' ? globalThis : window, function () {
    'use strict';
    return function createPaymentPoller(options) {
        var setTimer = options.setTimeout || setTimeout, clearTimer = options.clearTimeout || clearTimeout;
        var now = options.now || Date.now;
        var timer = null, request = null, active = false, paused = false, failures = 0, generation = 0;
        function update(state, message, extra) { options.onUpdate(Object.assign({state: state, message: message}, extra || {})); }
        function cancelTimer() { if (timer !== null) clearTimer(timer); timer = null; }
        async function poll() {
            cancelTimer();
            if (!active || paused || request) return;
            var run = generation, controller = new AbortController(); request = controller;
            var timeout = setTimer(function () { controller.abort(); }, 8000);
            try {
                var response = await options.fetch(options.url, {credentials: 'same-origin', cache: 'no-store',
                    headers: {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest'}, signal: controller.signal});
                if (!active || generation !== run) return;
                if (response.redirected || response.status === 401 || response.status === 403) {
                    active = false; update('UNAUTHORIZED', 'Phiên đăng nhập đã hết hoặc quyền đã thay đổi. Vui lòng đăng nhập lại.'); return;
                }
                if (response.status === 404) { active = false; update('NOT_FOUND', 'Không còn quyền truy cập thanh toán này.'); return; }
                if (!response.ok) throw new Error('Không đọc được database');
                var data = await response.json();
                if (!active || generation !== run) return;
                if (!data || !['PAID', 'UNPAID', 'CLOSED', 'UNAVAILABLE'].includes(data.state)) throw new Error('Phản hồi không hợp lệ');
                failures = 0; options.onUpdate(data);
                if (data.state !== 'UNPAID') active = false;
                else if (Number(options.expiresAt) <= Math.floor(now() / 1000)) {
                    active = false; update('EXPIRED', 'QR đã hết hạn. Tạo QR mới hoặc kiểm tra lại trạng thái thanh toán.');
                }
            } catch (error) {
                if (active && generation === run) { failures++; update('RETRY', 'Chưa xác minh được thanh toán. Đang thử kết nối lại, chưa ghi nhận thành công.'); }
            } finally {
                clearTimer(timeout); request = null;
                if (active && !paused) timer = setTimer(poll, generation !== run ? 0 : Math.min(15000, 3000 * Math.pow(2, Math.min(failures, 3))));
            }
        }
        return {
            start: function () { active = true; paused = false; poll(); },
            pause: function () { paused = true; cancelTimer(); },
            resume: function () { paused = false; if (active) poll(); else { active = true; poll(); } },
            check: function () { if (!paused) { active = true; poll(); } },
            stop: function () { active = false; generation++; cancelTimer(); if (request) request.abort(); }
        };
    };
}));
