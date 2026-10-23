(function () {
    'use strict';
    document.querySelectorAll('[data-order-form], [data-route-quote-form]').forEach(form => {
        let timer, pending, sequence = 0;
        const output = form.querySelector('[data-route-quote-output]');
        const hubs = form.querySelector('[data-drop-off-hubs]');
        const cart = form.querySelector('[data-cart-button]');
        const money = value => new Intl.NumberFormat('vi-VN').format(value) + ' đ';
        const names = ['pickupProvinceCode', 'pickupAreaCode', 'deliveryProvinceCode', 'deliveryAreaCode', 'weightKg'];
        const mode = () => form.querySelector('input[name="pickupMode"]:checked')?.value || 'COURIER';
        // Khách tự mang hàng: hiện địa chỉ quầy của tỉnh gửi; giỏ đơn chỉ nhận đơn shipper đến lấy.
        function showHub() {
            const dropOff = mode() === 'DROP_OFF';
            if (cart) cart.disabled = dropOff || form.dataset.orderSubmitting === 'true';
            if (!hubs) return;
            hubs.hidden = !dropOff;
            const province = form.elements.namedItem('pickupProvinceCode')?.value;
            let found = false;
            hubs.querySelectorAll('[data-province-code]').forEach(item => {
                item.hidden = item.dataset.provinceCode !== province;
                found = found || !item.hidden;
            });
            const empty = hubs.querySelector('[data-drop-off-empty]');
            if (empty) empty.hidden = found;
        }
        function refresh() {
            const run = ++sequence;
            clearTimeout(timer);
            if (pending) pending.abort();
            pending = null;
            showHub();
            const service = form.elements.namedItem('serviceType');
            Array.from(service.options).forEach(option => { option.hidden = option.disabled = false; });
            if (output) output.textContent = 'Chọn đủ địa bàn và khối lượng để xem cước trước mã giảm giá.';
            const values = names.map(name => form.elements.namedItem(name)?.value || '');
            const kg = Number(values[4]);
            if (!values.every(Boolean) || !Number.isFinite(kg) || kg < 0.01 || kg > 100) return;
            timer = setTimeout(async () => {
                if (run !== sequence) return;
                const request = new AbortController();
                pending = request;
                const params = new URLSearchParams();
                names.forEach((name, index) => params.set(name, values[index]));
                params.set('pickupMode', mode());
                try {
                    const response = await fetch('/api/routing/quote?' + params, {signal: request.signal, cache:'no-store', headers:{Accept:'application/json'}});
                    if (!response.ok) throw new Error('Không tính được cước. Kiểm tra địa bàn, khối lượng và trạng thái chi nhánh.');
                    const quotes = await response.json();
                    if (run !== sequence || request.signal.aborted) return;
                    const types = ['STANDARD', 'EXPRESS', 'SUPER_EXPRESS'];
                    if (!Array.isArray(quotes) || quotes.length === 0 || quotes.length > 3 || !quotes.every(q => q && types.includes(q.serviceType)
                            && ['baseFee','distanceFee','routeFee','dropOffDiscount','total'].every(key => typeof q[key] === 'number' && Number.isFinite(q[key]) && q[key] >= 0)))
                        throw new Error('Dữ liệu báo giá không hợp lệ. Vui lòng thử lại.');
                    Array.from(service.options).forEach(option => {
                        if (!option.value) return;
                        option.hidden = option.disabled = !quotes.some(q => q.serviceType === option.value);
                    });
                    if (service.selectedOptions[0]?.disabled) service.value = quotes[0]?.serviceType || '';
                    const selected = quotes.find(q => q.serviceType === service.value);
                    if (output && selected) output.textContent = 'Cước gốc ' + money(selected.baseFee)
                        + ' + khoảng cách ' + money(selected.distanceFee) + ' + tuyến ' + money(selected.routeFee)
                        + (selected.pickupMode === 'DROP_OFF' ? ' - giảm tự mang ra chi nhánh ' + money(selected.dropOffDiscount) : '')
                        + ' = ' + money(selected.total) + ' · ' + selected.leadTimeText;
                } catch (error) {
                    if (run === sequence && error.name !== 'AbortError' && output) output.textContent = error.message;
                } finally {
                    if (pending === request) pending = null;
                }
            }, 300);
        }
        form.addEventListener('change', refresh);
        form.elements.namedItem('weightKg').addEventListener('input', refresh);
        window.addEventListener('pageshow', event => { if (event.persisted) refresh(); });
        refresh();
    });
}());
