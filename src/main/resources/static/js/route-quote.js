(function () {
    'use strict';
    document.querySelectorAll('[data-order-form], [data-route-quote-form]').forEach(form => {
        let timer, pending;
        const output = form.querySelector('[data-route-quote-output]');
        const hubs = form.querySelector('[data-drop-off-hubs]');
        const cart = form.querySelector('[data-cart-button]');
        const money = value => new Intl.NumberFormat('vi-VN').format(value) + ' đ';
        const names = ['pickupProvinceCode', 'pickupAreaCode', 'deliveryProvinceCode', 'deliveryAreaCode', 'weightKg'];
        const mode = () => form.querySelector('input[name="pickupMode"]:checked')?.value || 'COURIER';
        // Khách tự mang hàng: hiện địa chỉ quầy của tỉnh gửi; giỏ đơn chỉ nhận đơn shipper đến lấy.
        function showHub() {
            const dropOff = mode() === 'DROP_OFF';
            if (cart) cart.disabled = dropOff;
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
            clearTimeout(timer);
            if (pending) pending.abort();
            showHub();
            const service = form.elements.namedItem('serviceType');
            Array.from(service.options).forEach(option => { option.hidden = option.disabled = false; });
            if (output) output.textContent = 'Chọn đủ địa bàn và khối lượng để xem cước trước mã giảm giá.';
            if (!names.every(name => form.elements.namedItem(name)?.value)) return;
            timer = setTimeout(async () => {
                pending = new AbortController();
                const params = new URLSearchParams();
                names.forEach(name => params.set(name, form.elements.namedItem(name).value));
                params.set('pickupMode', mode());
                try {
                    const response = await fetch('/api/routing/quote?' + params, {signal: pending.signal, headers:{Accept:'application/json'}});
                    if (!response.ok) throw new Error('Không tính được cước. Kiểm tra địa bàn, khối lượng và trạng thái chi nhánh.');
                    const quotes = await response.json();
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
                    if (error.name !== 'AbortError' && output) output.textContent = error.message;
                }
            }, 300);
        }
        form.addEventListener('change', refresh);
        form.elements.namedItem('weightKg').addEventListener('input', refresh);
        refresh();
    });
}());
