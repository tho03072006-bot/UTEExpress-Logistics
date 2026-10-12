(function () {
    'use strict';

    // Only presentation and browser checks. Server validation remains authoritative.
    document.addEventListener('DOMContentLoaded', function () {
        var firstServerError = null;
        document.querySelectorAll('form').forEach(function (form, formIndex) {
            var fields = Array.from(form.elements).filter(function (field) {
                return field.matches && field.matches('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), textarea, select');
            });
            if (!fields.length) return;

            var records = [];
            var sequence = 0;
            var summary = null;
            var pendingInvalid = false;

            function groupFor(field) {
                return field.type === 'radio' && field.name
                    ? fields.filter(function (candidate) { return candidate.type === 'radio' && candidate.name === field.name; })
                    : [field];
            }

            function labelFor(field) {
                var fieldset = field.closest('fieldset');
                var legend = field.type === 'radio' && fieldset ? fieldset.querySelector('legend') : null;
                var label = legend || (field.labels && field.labels[0]);
                return label ? label.textContent.trim() : (field.getAttribute('aria-label') || 'Thông tin cần nhập');
            }

            function describe(field, id, add) {
                var ids = (field.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean);
                ids = ids.filter(function (current) { return current !== id; });
                if (add) ids.push(id);
                if (ids.length) field.setAttribute('aria-describedby', ids.join(' '));
                else field.removeAttribute('aria-describedby');
            }

            function recordFor(field, message) {
                var record = records.find(function (item) { return item.fields.indexOf(field) !== -1; });
                if (record) return record;
                var group = groupFor(field);
                if (!field.id) field.id = 'ute-field-' + formIndex + '-' + (++sequence);
                if (!message) {
                    message = document.createElement('div');
                    var anchor = field.closest('.ute-rating') || field;
                    anchor.insertAdjacentElement('afterend', message);
                }
                if (!message.id) message.id = 'ute-field-error-' + formIndex + '-' + (++sequence);
                message.classList.add('ute-field-error');
                record = { field: field, fields: group, message: message, active: false, server: false };
                records.push(record);
                return record;
            }

            function show(record, text, server) {
                record.message.textContent = text;
                record.message.hidden = false;
                record.active = true;
                record.server = server;
                record.fields.forEach(function (field) {
                    field.classList.add('is-invalid');
                    field.setAttribute('aria-invalid', 'true');
                    describe(field, record.message.id, true);
                });
            }

            function clear(record) {
                record.message.hidden = true;
                record.active = false;
                record.server = false;
                record.fields.forEach(function (field) {
                    field.classList.remove('is-invalid');
                    field.removeAttribute('aria-invalid');
                    describe(field, record.message.id, false);
                });
            }

            function updateSummary(focusField) {
                var active = records.filter(function (record) { return record.active; });
                if (!active.length) {
                    if (summary) summary.hidden = true;
                    return;
                }
                if (!summary) {
                    summary = document.createElement('div');
                    summary.className = 'ute-form-errors alert alert-danger';
                    summary.setAttribute('role', 'alert');
                    form.prepend(summary);
                }
                summary.replaceChildren();
                summary.hidden = false;
                var heading = document.createElement('p');
                heading.className = 'fw-semibold mb-2';
                heading.textContent = 'Vui lòng kiểm tra ' + active.length + ' mục bên dưới:';
                summary.appendChild(heading);
                var list = document.createElement('ul');
                list.className = 'mb-0 ps-3';
                active.forEach(function (record) {
                    var item = document.createElement('li');
                    var link = document.createElement('a');
                    link.href = '#' + record.field.id;
                    link.textContent = labelFor(record.field) + ': ' + record.message.textContent;
                    link.addEventListener('click', function (event) {
                        event.preventDefault();
                        record.field.focus();
                    });
                    item.appendChild(link);
                    list.appendChild(item);
                });
                summary.appendChild(list);
                if (focusField) active[0].field.focus();
            }

            function browserMessage(field) {
                var validity = field.validity;
                if (validity.customError) return field.validationMessage;
                if (validity.valueMissing) return field.type === 'radio' || field.tagName === 'SELECT'
                    ? 'Vui lòng chọn một giá trị.' : 'Vui lòng điền thông tin này.';
                if (validity.typeMismatch) return field.type === 'email' ? 'Nhập địa chỉ email hợp lệ.' : 'Thông tin chưa đúng định dạng.';
                if (validity.tooShort) return 'Nhập ít nhất ' + field.minLength + ' ký tự.';
                if (validity.tooLong) return 'Nhập tối đa ' + field.maxLength + ' ký tự.';
                if (validity.rangeUnderflow) return 'Giá trị tối thiểu là ' + field.min + '.';
                if (validity.rangeOverflow) return 'Giá trị tối đa là ' + field.max + '.';
                if (validity.stepMismatch) return field.step === '0.001'
                    ? 'Nhập tối đa 3 chữ số thập phân.' : 'Giá trị chưa đúng bước cho phép.';
                if (validity.patternMismatch) return field.title || 'Thông tin chưa đúng định dạng.';
                if (validity.badInput) return 'Vui lòng nhập số hợp lệ.';
                return field.validationMessage || 'Vui lòng kiểm tra thông tin này.';
            }

            function applyExtraChecks(field) {
                if (field.dataset.minLength) {
                    var minimum = Number(field.dataset.minLength);
                    field.setCustomValidity(field.value.length > 0 && field.value.trim().length < minimum
                        ? 'Nhập ít nhất ' + minimum + ' ký tự sau khi bỏ khoảng trắng đầu và cuối.' : '');
                }
                if (field.type !== 'file' || !field.dataset.maxFileBytes) return;
                var file = field.files && field.files[0];
                var limit = Number(field.dataset.maxFileBytes);
                var error = '';
                if (file && file.size > limit) error = 'Tệp phải có dung lượng tối đa ' + Math.round(limit / 1048576) + ' MB.';
                if (file && !error && field.accept) {
                    var typeExtensions = { 'image/jpeg': ['.jpg', '.jpeg'], 'image/png': ['.png'] };
                    var supported = field.accept.toLowerCase().split(',').some(function (rule) {
                        rule = rule.trim();
                        var filename = file.name.toLowerCase();
                        if (rule.charAt(0) === '.') return filename.endsWith(rule);
                        if (rule.endsWith('/*')) return file.type.indexOf(rule.slice(0, -1)) === 0;
                        return file.type === rule || (!file.type && (typeExtensions[rule] || []).some(function (extension) { return filename.endsWith(extension); }));
                    });
                    if (!supported) error = 'Chọn tệp có định dạng được hỗ trợ bên dưới.';
                }
                field.setCustomValidity(error);
            }

            form.querySelectorAll('.ute-field-error, .text-danger.small, .invalid-feedback, [data-error-for]').forEach(function (message) {
                if (!message.textContent.trim() || message.hidden) return;
                var field = message.dataset.errorFor ? document.getElementById(message.dataset.errorFor) : null;
                if (field && !form.contains(field)) field = null;
                var parent = message.parentElement;
                while (!field && parent && parent !== form) {
                    field = fields.find(function (candidate) { return parent.contains(candidate); });
                    parent = parent.parentElement;
                }
                if (!field) return;
                var record = recordFor(field, message);
                show(record, message.textContent.trim(), true);
                if (!firstServerError) firstServerError = field;
            });

            fields.forEach(function (field) {
                applyExtraChecks(field);
                function updateField() {
                    applyExtraChecks(field);
                    var record = records.find(function (item) { return item.fields.indexOf(field) !== -1; });
                    if (!record || !record.active) return;
                    if (field.validity.valid) clear(record);
                    else show(record, browserMessage(field), false);
                    updateSummary(false);
                }
                field.addEventListener('input', updateField);
                field.addEventListener('change', updateField);
            });
            form.addEventListener('invalid', function (event) {
                var field = event.target;
                if (fields.indexOf(field) === -1) return;
                event.preventDefault();
                show(recordFor(field), browserMessage(field), false);
                if (pendingInvalid) return;
                pendingInvalid = true;
                window.setTimeout(function () {
                    pendingInvalid = false;
                    updateSummary(true);
                }, 0);
            }, true);
            updateSummary(false);
        });
        if (firstServerError) window.requestAnimationFrame(function () { firstServerError.focus(); });
    });
}());
