(function () {
    'use strict';
    document.querySelectorAll('[data-money]').forEach(function (node) {
        var amount = Number(node.textContent);
        if (Number.isSafeInteger(amount) && amount >= 0) node.textContent = new Intl.NumberFormat('vi-VN').format(amount) + ' ₫';
    });
    var checkout = document.querySelector('[data-checkout]');
    if (!checkout) return;
    var button = checkout.querySelector('[data-confirm-button]');
    var form = checkout.querySelector('[data-confirm-form]');
    var feedback = checkout.querySelector('[data-feedback]');
    var timer = document.querySelector('[data-countdown]');
    function tick() {
        var left = Math.max(0, Number(checkout.dataset.expires) - Math.floor(Date.now() / 1000));
        if (timer) timer.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
        if (!left) { button.disabled = true; feedback.textContent = 'Phiên đã hết hạn. Hãy tạo QR mới tại UTEExpress.'; return; }
        window.setTimeout(tick, 1000);
    }
    form.addEventListener('submit', function (event) {
        if (Number(checkout.dataset.expires) <= Date.now() / 1000 || !navigator.onLine) {
            event.preventDefault(); feedback.textContent = 'Phiên hết hạn hoặc mất kết nối. Kiểm tra mạng rồi thử lại.'; return;
        }
        button.disabled = true; button.textContent = 'Đang xử lý giao dịch…';
    });
    window.addEventListener('pageshow', function () { button.disabled = Number(checkout.dataset.expires) <= Date.now() / 1000; button.textContent = button.dataset.label || 'Xác nhận thanh toán'; });
    tick();
}());
