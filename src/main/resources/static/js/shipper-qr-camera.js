(function () {
    'use strict';
    const video = document.getElementById('qrVideo');
    if (!video) return;
    const start = document.getElementById('qrCameraStart');
    const stop = document.getElementById('qrCameraStop');
    const status = document.getElementById('qrCameraStatus');
    const placeholder = document.getElementById('qrCameraPlaceholder');
    const frame = document.getElementById('qrCameraFrame');
    const form = document.getElementById('qrCodeForm');
    const consent = document.getElementById('qrCameraConsent');
    let controls = null;
    let generation = 0;
    let submitted = false;
    let running = false;

    /* ---- Danh sách điều kiện: hệ thống tự kiểm tra, người dùng tick đồng ý ---- */
    const secureOk = window.isSecureContext === true;
    const deviceOk = Boolean(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    function mark(name, ok, failText) {
        const item = document.querySelector('[data-cam-check="' + name + '"]');
        if (!item) return;
        item.classList.toggle('sx-ok', ok);
        item.classList.toggle('sx-fail', !ok);
        item.querySelector('.sx-check-mark i').className = 'bi ' + (ok ? 'bi-check-lg' : 'bi-x-lg');
        if (!ok && failText) item.querySelector('span:not(.sx-check-mark)').textContent = failText;
    }
    mark('secure', secureOk, 'Trang đang mở qua HTTP thường nên trình duyệt chặn camera. Hãy dùng ảnh QR bên cạnh.');
    mark('device', deviceOk, 'Trình duyệt này không hỗ trợ camera. Hãy dùng ảnh QR bên cạnh.');
    try { if (consent && localStorage.getItem('ute-camera-consent') === 'yes') consent.checked = true; } catch (_) { /* chế độ riêng tư */ }
    function syncReady() {
        const agreed = !consent || consent.checked;
        if (consent) consent.closest('.sx-consent').classList.toggle('sx-ok', agreed);
        if (running) return;
        start.disabled = !(agreed && secureOk && deviceOk);
        if (!secureOk || !deviceOk) status.textContent = 'Thiết bị hoặc kết nối chưa đủ điều kiện dùng camera. Hãy chọn ảnh QR bên cạnh.';
        else if (!agreed) status.textContent = 'Tick ô đồng ý ở trên để mở nút bắt đầu quét.';
        else status.textContent = 'Đã sẵn sàng. Nhấn "Bắt đầu quét" và cho phép camera khi trình duyệt hỏi.';
    }
    if (consent) consent.addEventListener('change', () => {
        try { localStorage.setItem('ute-camera-consent', consent.checked ? 'yes' : 'no'); } catch (_) { /* bỏ qua */ }
        if (!consent.checked && running) stopCamera('Đã tắt camera vì bạn bỏ đồng ý.');
        syncReady();
    });
    syncReady();

    function stopCamera(message) {
        generation++;
        if (controls) { controls.stop(); controls = null; }
        if (video.srcObject) {
            video.srcObject.getTracks().forEach(track => track.stop());
            video.srcObject = null;
        }
        running = false;
        stop.disabled = true;
        placeholder.hidden = false;
        frame.hidden = true;
        syncReady();
        if (message) status.textContent = message;
    }
    start.addEventListener('click', async () => {
        if (consent && !consent.checked) { syncReady(); return; }
        if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            status.textContent = 'Camera cần HTTPS hoặc localhost. Vui lòng dùng ảnh QR hoặc tìm theo mã vận đơn.';
            return;
        }
        if (!window.ZXingBrowser) {
            status.textContent = 'Chưa tải được bộ đọc QR. Hãy tải lại trang hoặc dùng ảnh QR.';
            return;
        }
        running = true;
        start.disabled = true;
        stop.disabled = false;
        status.textContent = 'Đang mở camera. Vui lòng cho phép quyền camera.';
        const current = ++generation;
        try {
            const reader = new window.ZXingBrowser.BrowserQRCodeReader();
            const scanning = await reader.decodeFromConstraints(
                { audio: false, video: { facingMode: { ideal: 'environment' } } }, video,
                (result, error, activeControls) => {
                    if (current !== generation || submitted || !result) return;
                    const code = result.getText().trim();
                    if (!/^[A-Za-z0-9-]{1,20}$/.test(code)) {
                        status.textContent = 'QR này không chứa mã vận đơn hợp lệ. Hãy quét QR trên vận đơn UTEExpress.';
                        return;
                    }
                    submitted = true;
                    activeControls.stop();
                    stopCamera('Đã đọc mã ' + code + '. Đang kiểm tra vận đơn…');
                    document.getElementById('qrDecodedCode').value = code;
                    form.requestSubmit();
                });
            if (current !== generation || submitted) { scanning.stop(); return; }
            controls = scanning;
            placeholder.hidden = true;
            frame.hidden = false;
            status.textContent = 'Camera đã sẵn sàng. Đưa QR vào khung để mở vận đơn.';
        } catch (error) {
            if (current !== generation) return;
            const messages = {
                NotAllowedError: 'Chưa được phép dùng camera. Hãy cho phép camera trong trình duyệt hoặc chọn ảnh QR.',
                NotFoundError: 'Không tìm thấy camera. Hãy chọn ảnh QR để tiếp tục.',
                NotReadableError: 'Camera đang bận hoặc không mở được. Hãy đóng ứng dụng dùng camera và thử lại.'
            };
            stopCamera(messages[error.name] || 'Không mở được camera. Hãy thử lại hoặc chọn ảnh QR.');
        }
    });
    stop.addEventListener('click', () => stopCamera('Đã dừng camera. Bạn có thể bắt đầu quét lại.'));
    document.addEventListener('visibilitychange', () => { if (document.hidden) stopCamera('Camera đã dừng khi bạn rời trang.'); });
    window.addEventListener('pagehide', () => stopCamera());
}());
