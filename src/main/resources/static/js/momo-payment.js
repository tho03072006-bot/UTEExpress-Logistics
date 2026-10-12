(function () {
    'use strict';
    const polling = document.getElementById('momoPolling');
    const status = document.getElementById('momoStatus');
    const form = document.getElementById('momoCheckForm');
    if (!polling || !status || !form) return;
    const csrf = form.querySelector('input[type="hidden"]');
    if (!csrf) return;
    let attempts = 0;
    let timer;
    async function check() {
        if (document.hidden) { timer = setTimeout(check, 10000); return; }
        try {
            const body = new URLSearchParams();
            body.set(csrf.name, csrf.value);
            const response = await fetch(polling.dataset.url, { method: 'POST', body,
                credentials: 'same-origin', headers: { 'Accept': 'application/json' } });
            if (response.redirected || response.status === 401 || response.status === 403) {
                status.textContent = 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại để kiểm tra.';
                return;
            }
            const result = await response.json();
            status.textContent = result.message || 'Chưa xác nhận được kết quả từ MoMo.';
            if (result.state === 'PAID') { window.location.reload(); return; }
            if (result.state === 'FAILED') return;
        } catch (error) {
            status.textContent = 'Tạm thời chưa kết nối được MoMo. Bạn có thể bấm kiểm tra lại.';
        }
        if (++attempts < 30) timer = setTimeout(check, 10000);
        else status.textContent = 'Tạm dừng kiểm tra tự động. Bấm kiểm tra kết quả khi đã hoàn tất thanh toán.';
    }
    timer = setTimeout(check, polling.dataset.checkNow === 'true' ? 100 : 5000);
    window.addEventListener('pagehide', function () { clearTimeout(timer); });
})();
