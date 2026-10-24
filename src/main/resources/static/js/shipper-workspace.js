/*
 * Khu vực Shipper: kiểm tra dữ liệu ngay trên trình duyệt (server vẫn kiểm tra lại),
 * chọn nhiều đơn, tính tổng tiền COD và chống bấm gửi hai lần.
 * Trang có thể nạp file này hai lần (fragment + trang) nên chỉ khởi tạo một lần.
 */
(function () {
    'use strict';
    if (window.__uteShipperWorkspace) return;
    window.__uteShipperWorkspace = true;

    const money = value => new Intl.NumberFormat('vi-VN').format(value) + ' đ';
    const pad = n => String(n).padStart(2, '0');
    const localInput = date => date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
            + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());

    document.addEventListener('DOMContentLoaded', () => {
        if (/\/shipper\/(don|khieu-nai)\//.test(window.location.pathname)) {
            document.querySelectorAll(':is(.ute-nav-link, .sx-tabbar-link)[href$="/shipper/trang-chu"]').forEach(link => link.classList.add('active'));
        }
        if (/\/shipper\/doi-soat\//.test(window.location.pathname)) {
            document.querySelectorAll(':is(.ute-nav-link, .sx-tabbar-link)[href$="/shipper/doi-soat"]').forEach(link => link.classList.add('active'));
        }
    });

    const filters = document.querySelector('[data-shipper-filters]');
    if (filters && window.matchMedia('(max-width: 767px)').matches && filters.dataset.filterActive !== 'true') {
        filters.open = false;
    }

    /* ---------- Sao chép địa chỉ ---------- */
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

    /* ---------- Khoảng ngày: từ ngày <= đến ngày, tối đa N ngày nếu có ---------- */
    document.querySelectorAll('[data-sx-range-form]').forEach(form => {
        const from = form.querySelector('[data-sx-from]');
        const to = form.querySelector('[data-sx-to]');
        const maxDays = Number(form.dataset.sxMaxDays || 0);
        if (!from || !to) return;
        function validate() {
            let error = '';
            if (from.value && to.value) {
                const days = (Date.parse(to.value) - Date.parse(from.value)) / 86400000 + 1;
                if (days < 1) error = 'Ngày kết thúc phải từ ngày bắt đầu trở đi.';
                else if (maxDays && days > maxDays) error = 'Vui lòng chọn tối đa ' + maxDays + ' ngày.';
            } else if (maxDays && (from.value || to.value) && !(from.value && to.value)) {
                error = 'Hãy chọn cả từ ngày và đến ngày.';
            }
            to.setCustomValidity(error);
            to.classList.toggle('is-invalid', Boolean(error));
            const feedback = to.parentElement.querySelector('.invalid-feedback');
            if (feedback && error) feedback.textContent = error;
            return !error;
        }
        from.addEventListener('change', validate);
        to.addEventListener('change', validate);
        form.addEventListener('submit', event => {
            if (!validate() || !form.checkValidity()) {
                event.preventDefault();
                form.reportValidity();
            }
        });
    });

    /* ---------- Hẹn lại: sau hiện tại và trong 30 ngày (giờ máy = giờ Việt Nam của Shipper) ---------- */
    document.querySelectorAll('input[data-sx-schedule]').forEach(input => {
        function bounds() {
            const now = new Date();
            now.setSeconds(0, 0);
            const min = new Date(now.getTime() + 60000);
            const max = new Date(now.getTime() + 30 * 86400000);
            input.min = localInput(min);
            input.max = localInput(max);
            return { min, max };
        }
        bounds();
        function validate() {
            const { min, max } = bounds();
            let error = '';
            if (input.value) {
                const chosen = new Date(input.value);
                if (Number.isNaN(chosen.getTime())) error = 'Thời gian hẹn không hợp lệ.';
                else if (chosen < min) error = 'Thời gian hẹn phải sau thời điểm hiện tại.';
                else if (chosen > max) error = 'Chỉ hẹn trong 30 ngày tới.';
            }
            input.setCustomValidity(error);
            input.classList.toggle('is-invalid', Boolean(error));
        }
        input.addEventListener('change', validate);
        input.addEventListener('focus', bounds);
        if (input.form) input.form.addEventListener('submit', event => {
            validate();
            if (!input.checkValidity()) { event.preventDefault(); input.reportValidity(); }
        }, true);
    });

    /* ---------- Lý do "khác" bắt buộc ghi chú >= 5 ký tự ---------- */
    document.querySelectorAll('[data-sx-failure-form]').forEach(form => {
        const reason = form.querySelector('[data-sx-reason]');
        const note = form.querySelector('[data-sx-note]');
        if (!reason || !note) return;
        function sync() {
            const other = reason.value === 'OTHER';
            note.required = other;
            note.minLength = other ? 5 : 0;
            note.placeholder = other ? 'Bắt buộc mô tả ít nhất 5 ký tự.' : note.placeholder;
        }
        reason.addEventListener('change', sync);
        sync();
    });

    /* ---------- Đếm ký tự cho ô nhập dài ---------- */
    document.querySelectorAll('textarea[data-sx-count][maxlength]').forEach(area => {
        const hint = document.createElement('div');
        hint.className = 'sx-count-hint';
        hint.setAttribute('aria-live', 'polite');
        area.insertAdjacentElement('afterend', hint);
        const update = () => {
            const max = Number(area.getAttribute('maxlength'));
            const length = area.value.trim().length;
            hint.textContent = length + '/' + max + ' ký tự';
            hint.classList.toggle('sx-over', length > max * 0.9);
        };
        area.addEventListener('input', update);
        update();
    });

    /* ---------- Ảnh: kiểm tra dung lượng/định dạng trước khi tải lên ---------- */
    document.querySelectorAll('input[type=file][data-sx-max]').forEach(input => {
        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            const max = Number(input.dataset.sxMax);
            let error = '';
            if (file && !/^image\/(jpeg|png)$/.test(file.type)) error = 'Chỉ nhận ảnh JPG hoặc PNG.';
            else if (file && file.size > max && !input.hasAttribute('data-sx-compress')) error = 'Ảnh tối đa ' + Math.round(max / 1048576) + ' MB. Hãy chọn ảnh nhỏ hơn.';
            input.setCustomValidity(error);
            input.classList.toggle('is-invalid', Boolean(error));
            if (error) input.reportValidity();
        });
    });

    /* ---------- Chống gửi hai lần: chạy sau hộp xác nhận (main.js chặn trước nếu cần hỏi) ---------- */
    document.addEventListener('submit', event => {
        const form = event.target;
        if (!form.matches || !form.matches('[data-sx-once]') || event.defaultPrevented) return;
        // Form cần hỏi lại: main.js tự gửi một lần sau khi đồng ý; khóa ở đây sẽ kẹt nút nếu người dùng bấm Quay lại.
        if (form.hasAttribute('data-ute-confirm') && form.dataset.uteConfirmed !== 'true') return;
        if (form.dataset.sxSubmitting === 'true') { event.preventDefault(); return; }
        form.dataset.sxSubmitting = 'true';
        // Đợi trình duyệt lấy giá trị nút đã bấm rồi mới khóa nút.
        setTimeout(() => {
            if (event.defaultPrevented) { delete form.dataset.sxSubmitting; return; }
            form.querySelectorAll('button[type=submit], button:not([type])').forEach(button => {
                button.disabled = true;
                button.classList.add('sx-busy');
            });
        }, 0);
    });
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        document.querySelectorAll('form[data-sx-submitting]').forEach(form => {
            delete form.dataset.sxSubmitting;
            form.querySelectorAll('.sx-busy').forEach(button => { button.disabled = false; button.classList.remove('sx-busy'); });
        });
    });

    /* ---------- Cập nhật nhiều đơn ---------- */
    const bulkForm = document.getElementById('bulkForm');
    const bulkBar = document.querySelector('[data-bulkbar]');
    if (bulkForm && bulkBar) {
        const checks = Array.from(document.querySelectorAll('.sx-bulk-check'));
        const all = document.querySelector('[data-bulk-all]');
        const max = Number(bulkBar.dataset.max || 50);
        const labels = { PICKED_UP: 'Đã lấy hàng', IN_TRANSIT: 'Đang giao' };
        const source = { PICKED_UP: 'PENDING_PICKUP', IN_TRANSIT: 'PICKED_UP' };
        if (checks.length) document.querySelectorAll('[data-bulk-only]').forEach(el => { el.hidden = false; });
        function render() {
            const selected = checks.filter(c => c.checked);
            bulkBar.hidden = selected.length === 0;
            bulkBar.querySelector('[data-bulk-count]').textContent = selected.length;
            document.querySelectorAll('[data-bulk-n]').forEach(span => {
                const n = selected.filter(c => c.dataset.status === span.dataset.bulkN).length;
                span.textContent = n;
                span.closest('button').disabled = n === 0;
            });
            checks.forEach(c => c.closest('.sx-order-row, .shipper-order-card').classList.toggle('sx-selected', c.checked));
            if (all) {
                all.checked = selected.length > 0 && selected.length === checks.length;
                all.indeterminate = selected.length > 0 && selected.length < checks.length;
            }
            const hint = bulkBar.querySelector('[data-bulk-hint]');
            hint.textContent = selected.length > max ? 'Mỗi lần tối đa ' + max + ' đơn.'
                    : 'Chỉ cho lấy hàng và bắt đầu giao.';
        }
        checks.forEach(c => c.addEventListener('change', render));
        if (all) all.addEventListener('change', () => { checks.forEach(c => { c.checked = all.checked; }); render(); });
        bulkBar.querySelector('[data-bulk-clear]').addEventListener('click', () => { checks.forEach(c => { c.checked = false; }); render(); });
        bulkBar.querySelectorAll('[data-bulk-action]').forEach(button => button.addEventListener('click', () => {
            const action = button.dataset.bulkAction;
            const ids = checks.filter(c => c.checked && c.dataset.status === source[action]).map(c => c.dataset.id);
            if (!ids.length || ids.length > max) return;
            bulkForm.querySelectorAll('input[name=orderIds]').forEach(input => input.remove());
            ids.forEach(id => {
                const input = document.createElement('input');
                input.type = 'hidden'; input.name = 'orderIds'; input.value = id;
                bulkForm.appendChild(input);
            });
            bulkForm.querySelector('[data-bulk-status-input]').value = action;
            bulkForm.setAttribute('data-ute-confirm', 'Chuyển ' + ids.length + ' đơn sang "' + labels[action]
                    + '"? Thao tác này không hoàn tác được.');
            delete bulkForm.dataset.uteConfirmed;
            if (bulkForm.requestSubmit) bulkForm.requestSubmit(); else bulkForm.submit();
        }));
        render();
    }

    /* ---------- Nộp tiền COD: tổng tiền theo đơn chọn, chênh lệch và ghi chú bắt buộc ---------- */
    const codForm = document.querySelector('[data-sx-cod-form]');
    if (codForm) {
        const checks = Array.from(document.querySelectorAll('.sx-cod-check'));
        const all = document.querySelector('[data-cod-all]');
        const amount = codForm.querySelector('[data-cod-amount]');
        const note = codForm.querySelector('[data-cod-note]');
        const diff = codForm.querySelector('[data-cod-diff]');
        let total = 0;
        let touched = Boolean(amount.value);
        // Cùng quy tắc với CodSettlementService.parseAmount: VND nguyên, nhóm nghìn phải đủ 3 số.
        const parse = raw => {
            const value = String(raw).replace(/\u00a0/g, ' ').replace(/\s*(đ|vnd)$/iu, '').trim();
            if (!/^(?:\d{1,12}|\d{1,3}(?:\.\d{3})+|\d{1,3}(?:,\d{3})+|\d{1,3}(?: \d{3})+)$/.test(value)) return NaN;
            const digits = value.replace(/[., ]/g, '');
            return /^\d{1,12}$/.test(digits) ? Number(digits) : NaN;
        };
        function render() {
            const selected = checks.filter(c => c.checked);
            const previousTotal = total;
            total = selected.reduce((sum, c) => sum + Number(c.dataset.amount), 0);
            // Số tiền đang bằng tổng cũ thì coi như chưa tự sửa, cập nhật theo tổng mới.
            if (touched && parse(amount.value) === previousTotal) touched = false;
            codForm.querySelector('[data-cod-count]').textContent = selected.length;
            codForm.querySelector('[data-cod-total]').textContent = money(total);
            checks.forEach(c => c.closest('tr').classList.toggle('sx-selected', c.checked));
            if (all) {
                all.checked = selected.length === checks.length;
                all.indeterminate = selected.length > 0 && selected.length < checks.length;
            }
            if (!touched) amount.value = total ? new Intl.NumberFormat('vi-VN').format(total) : '';
            validate();
        }
        function validate() {
            const selected = checks.filter(c => c.checked).length;
            const value = parse(amount.value);
            let error = '';
            if (!selected) error = 'Hãy chọn ít nhất một đơn.';
            else if (selected > 200) error = 'Mỗi phiếu tối đa 200 đơn.';
            else if (!Number.isFinite(total) || total <= 0 || total > 999999999999) error = 'Tổng cước không hợp lệ hoặc vượt giới hạn 12 chữ số.';
            else if (!Number.isFinite(value)) error = 'Nhập VND nguyên, tối đa 12 chữ số; dấu phân cách đúng nhóm 3 số.';
            else if (value <= 0) error = 'Số tiền nộp phải lớn hơn 0.';
            amount.setCustomValidity(error);
            amount.classList.toggle('is-invalid', Boolean(error));
            const different = !error && value !== total;
            diff.textContent = error ? error : different ? (value < total ? 'Nộp thiếu ' : 'Nộp thừa ') + money(Math.abs(total - value)) + '.' : 'Khớp tổng cước.';
            diff.className = 'sx-diff ' + (error || different ? 'sx-short' : 'sx-even');
            note.required = different;
            note.minLength = different ? 10 : 0;
            note.setCustomValidity(different && note.value.trim().length < 10 ? 'Ghi rõ lý do chênh lệch ít nhất 10 ký tự có nội dung.' : '');
        }
        checks.forEach(c => c.addEventListener('change', render));
        if (all) all.addEventListener('change', () => { checks.forEach(c => { c.checked = all.checked; }); render(); });
        amount.addEventListener('input', () => { touched = true; validate(); });
        note.addEventListener('input', validate);
        codForm.addEventListener('submit', event => {
            validate();
            if (!codForm.checkValidity()) { event.preventDefault(); codForm.reportValidity(); }
        });
        render();
    }
}());
