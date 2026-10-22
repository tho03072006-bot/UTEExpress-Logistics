/*
 * Đổi thông báo kiểm tra form có sẵn của trình duyệt (required, minlength, type=email...) sang tiếng Việt.
 * Chrome/Edge hiện theo ngôn ngữ máy ("Please fill out this field"), nên bắt sự kiện "invalid" và đặt câu riêng.
 * Không đè thông báo mà script khác đã đặt bằng setCustomValidity (ví dụ "Hai mật khẩu chưa khớp").
 */
(function (root) {
    'use strict';
    const number = value => {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed.toLocaleString('vi-VN') : value;
    };

    function messageFor(el) {
        const v = el.validity, type = (el.type || '').toLowerCase(), length = (el.value || '').length;
        if (v.valueMissing) {
            if (type === 'checkbox') return 'Vui lòng đánh dấu ô này để tiếp tục.';
            if (type === 'radio') return 'Vui lòng chọn một trong các lựa chọn.';
            if (type === 'file') return 'Vui lòng chọn tệp.';
            if (el.tagName === 'SELECT') return 'Vui lòng chọn một mục trong danh sách.';
            return 'Vui lòng nhập thông tin vào ô này.';
        }
        if (v.badInput) {
            if (type === 'number') return 'Vui lòng nhập một số.';
            if (type.startsWith('date') || type === 'time' || type === 'month') return 'Vui lòng nhập thời gian hợp lệ.';
            return 'Giá trị chưa hợp lệ.';
        }
        if (v.typeMismatch) {
            if (type === 'email') return 'Vui lòng nhập email hợp lệ, ví dụ ten@gmail.com.';
            if (type === 'url') return 'Vui lòng nhập đường dẫn hợp lệ, bắt đầu bằng https://.';
            return 'Giá trị chưa đúng định dạng.';
        }
        if (v.tooShort) return `Vui lòng nhập ít nhất ${el.minLength} ký tự (hiện có ${length} ký tự).`;
        if (v.tooLong) return `Vui lòng nhập tối đa ${el.maxLength} ký tự (hiện có ${length} ký tự).`;
        if (v.patternMismatch) return el.title ? `Chưa đúng định dạng: ${el.title}` : 'Vui lòng nhập đúng định dạng yêu cầu.';
        if (v.rangeUnderflow) return `Giá trị phải từ ${number(el.min)} trở lên.`;
        if (v.rangeOverflow) return `Giá trị không được lớn hơn ${number(el.max)}.`;
        if (v.stepMismatch) {
            const step = Number(el.step);
            return Number.isInteger(step) && step === 1 ? 'Vui lòng nhập số nguyên.' : `Giá trị phải theo bước ${number(el.step)}.`;
        }
        return '';
    }

    function onInvalid(event) {
        const el = event.target;
        if (!el || typeof el.setCustomValidity !== 'function') return;
        if (el.validity.customError && !el.uteViMessage) return; // thông báo riêng của script khác
        const message = messageFor(el);
        if (!message) return;
        el.setCustomValidity(message);
        el.uteViMessage = true;
    }

    // Người dùng sửa ô thì gỡ câu đã đặt để trình duyệt kiểm tra lại từ đầu.
    function onEdit(event) {
        const el = event.target;
        if (el && el.uteViMessage) { el.setCustomValidity(''); el.uteViMessage = false; }
    }

    if (root.document) {
        root.document.addEventListener('invalid', onInvalid, true);
        root.document.addEventListener('input', onEdit, true);
        root.document.addEventListener('change', onEdit, true);
    }
    if (typeof module !== 'undefined') module.exports = { messageFor, onInvalid, onEdit };
})(typeof window !== 'undefined' ? window : globalThis);
