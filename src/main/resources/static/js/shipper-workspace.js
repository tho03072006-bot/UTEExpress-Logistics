(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', () => {
        if (/\/shipper\/(don|khieu-nai)\//.test(window.location.pathname)) {
            document.querySelectorAll('.ute-nav-link[href$="/shipper/trang-chu"]').forEach(link => link.classList.add('active'));
        }
    });
    const filters = document.querySelector('[data-shipper-filters]');
    if (filters && window.matchMedia('(max-width: 767px)').matches && filters.dataset.filterActive !== 'true') {
        filters.open = false;
    }
    document.querySelectorAll('[data-copy-text]').forEach(button => {
        button.addEventListener('click', async () => {
            const message = document.querySelector('[data-copy-status]');
            try {
                await navigator.clipboard.writeText(button.dataset.copyText);
                if (message) message.textContent = 'Đã sao chép địa chỉ.';
            } catch (_) {
                if (message) message.textContent = 'Trình duyệt chưa cho phép sao chép. Bạn có thể chọn địa chỉ để sao chép thủ công.';
            }
        });
    });
    const from = document.getElementById('statsFrom');
    const to = document.getElementById('statsTo');
    if (from && to) {
        function validate() {
            let error = '';
            if (from.value && to.value) {
                const days = (Date.parse(to.value) - Date.parse(from.value)) / 86400000 + 1;
                if (days < 1) error = 'Ngày kết thúc phải từ ngày bắt đầu trở đi.';
                else if (days > 92) error = 'Vui lòng chọn tối đa 92 ngày.';
            }
            to.setCustomValidity(error);
        }
        from.addEventListener('change', validate);
        to.addEventListener('change', validate);
    }
    const reason = document.getElementById('failureReason');
    const note = document.getElementById('failureNote');
    if (reason && note) {
        reason.addEventListener('change', () => {
            note.required = reason.value === 'OTHER';
            note.minLength = note.required ? 5 : 0;
        });
    }
}());
