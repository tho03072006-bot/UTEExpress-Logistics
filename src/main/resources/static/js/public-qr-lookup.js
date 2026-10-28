/*
 * Tra cứu vận đơn bằng mã QR (trang công khai /tra-cuu).
 * Giải mã ngay trên trình duyệt bằng ZXing: quét camera hoặc chọn ảnh. Không gửi ảnh lên máy chủ;
 * đọc được mã hợp lệ thì điền vào ô tra cứu và gửi form như khi gõ tay.
 */
(function () {
    'use strict';
    const panel = document.querySelector('[data-qr-panel]');
    const form = document.querySelector('[data-lookup-form]');
    if (!panel || !form) return;
    const input = form.querySelector('input[name=trackingCode]');
    const video = document.getElementById('lookupVideo');
    const start = document.getElementById('lookupStart');
    const stop = document.getElementById('lookupStop');
    const status = document.getElementById('lookupStatus');
    const placeholder = document.getElementById('lookupPlaceholder');
    const frame = document.getElementById('lookupFrame');
    const consent = document.getElementById('lookupConsent');
    const image = document.getElementById('lookupImage');
    const CODE = /^[A-Za-z0-9-]{1,20}$/;
    const MAX_IMAGE = 5 * 1024 * 1024;
    let controls = null;
    let generation = 0;
    let running = false;
    let done = false;

    const cameraSupported = () => window.isSecureContext === true && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    const reader = () => window.ZXingBrowser ? new window.ZXingBrowser.BrowserQRCodeReader() : null;

    function openPanel() {
        panel.hidden = false;
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    function closePanel() {
        stopCamera();
        panel.hidden = true;
    }
    document.querySelectorAll('[data-qr-open]').forEach(button => button.addEventListener('click', openPanel));
    panel.querySelector('[data-qr-close]').addEventListener('click', closePanel);
    if (window.location.hash === '#quet-qr') openPanel();

    function syncReady() {
        const agreed = consent.checked;
        consent.closest('.sx-consent').classList.toggle('sx-ok', agreed);
        if (running) return;
        start.disabled = !(agreed && cameraSupported());
        if (agreed && !cameraSupported()) status.textContent = 'Trình duyệt hoặc kết nối hiện tại không bật được camera (cần HTTPS hoặc localhost). Hãy chọn ảnh có mã QR.';
    }
    try { if (localStorage.getItem('ute-camera-consent') === 'yes') consent.checked = true; } catch (_) { /* chế độ riêng tư */ }
    consent.addEventListener('change', () => {
        try { localStorage.setItem('ute-camera-consent', consent.checked ? 'yes' : 'no'); } catch (_) { /* bỏ qua */ }
        if (!consent.checked && running) stopCamera('Đã tắt camera vì bạn bỏ đồng ý.');
        syncReady();
    });
    syncReady();

    function submitCode(raw) {
        const code = String(raw || '').trim();
        if (!CODE.test(code)) {
            status.textContent = 'Mã QR này không phải mã vận đơn UTEExpress hợp lệ. Hãy quét QR in trên vận đơn.';
            return false;
        }
        done = true;
        input.value = code;
        status.textContent = 'Đã đọc mã ' + code + '. Đang tra cứu…';
        if (form.requestSubmit) form.requestSubmit(); else form.submit();
        return true;
    }

    function stopCamera(message) {
        generation++;
        if (controls) { try { controls.stop(); } catch (_) { /* đã dừng */ } controls = null; }
        if (video.srcObject) { video.srcObject.getTracks().forEach(track => track.stop()); video.srcObject = null; }
        running = false;
        stop.disabled = true;
        placeholder.hidden = false;
        frame.hidden = true;
        syncReady();
        if (message) status.textContent = message;
    }

    start.addEventListener('click', async () => {
        if (!consent.checked) { syncReady(); return; }
        const qr = reader();
        if (!qr) { status.textContent = 'Chưa tải được bộ đọc QR. Hãy tải lại trang.'; return; }
        if (!cameraSupported()) { syncReady(); return; }
        running = true;
        done = false;
        start.disabled = true;
        stop.disabled = false;
        status.textContent = 'Đang mở camera. Hãy chọn "Cho phép" khi trình duyệt hỏi.';
        const current = ++generation;
        try {
            const scanning = await qr.decodeFromConstraints({ audio: false, video: { facingMode: { ideal: 'environment' } } }, video,
                (result, error, active) => {
                    if (current !== generation || done || !result) return;
                    const text = result.getText();
                    if (!CODE.test(String(text).trim())) { status.textContent = 'Mã QR này không phải mã vận đơn UTEExpress hợp lệ.'; return; }
                    active.stop();
                    stopCamera();
                    submitCode(text);
                });
            if (current !== generation) { scanning.stop(); return; }
            controls = scanning;
            placeholder.hidden = true;
            frame.hidden = false;
            status.textContent = 'Camera đã sẵn sàng. Đưa mã QR vào khung để tra cứu.';
        } catch (error) {
            if (current !== generation) return;
            const messages = {
                NotAllowedError: 'Bạn chưa cho phép dùng camera. Hãy cho phép trong trình duyệt hoặc chọn ảnh có mã QR.',
                NotFoundError: 'Không tìm thấy camera. Hãy chọn ảnh có mã QR.',
                NotReadableError: 'Camera đang được ứng dụng khác dùng. Hãy đóng ứng dụng đó rồi thử lại.'
            };
            stopCamera(messages[error && error.name] || 'Không mở được camera. Hãy thử lại hoặc chọn ảnh có mã QR.');
        }
    });
    stop.addEventListener('click', () => stopCamera('Đã dừng camera.'));

    image.addEventListener('change', async () => {
        const file = image.files && image.files[0];
        if (!file) return;
        if (!/^image\/(jpeg|png|webp)$/.test(file.type)) { status.textContent = 'Chỉ nhận ảnh JPG, PNG hoặc WEBP.'; image.value = ''; return; }
        if (file.size > MAX_IMAGE) { status.textContent = 'Ảnh tối đa 5 MB. Hãy chọn ảnh nhỏ hơn.'; image.value = ''; return; }
        const qr = reader();
        if (!qr) { status.textContent = 'Chưa tải được bộ đọc QR. Hãy tải lại trang.'; return; }
        stopCamera();
        status.textContent = 'Đang đọc mã QR trong ảnh…';
        const url = URL.createObjectURL(file);
        try {
            const result = await qr.decodeFromImageUrl(url);
            if (!submitCode(result.getText())) image.value = '';
        } catch (_) {
            status.textContent = 'Không đọc được mã QR trong ảnh. Hãy chụp rõ, đủ sáng và chứa trọn mã QR.';
            image.value = '';
        } finally {
            URL.revokeObjectURL(url);
        }
    });

    document.addEventListener('visibilitychange', () => { if (document.hidden && running) stopCamera('Camera đã dừng khi bạn rời trang.'); });
    window.addEventListener('pagehide', () => stopCamera());
}());
