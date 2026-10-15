/*
 * Biểu đồ đường cho trang Thống kê Shipper (SVG thuần, không thư viện ngoài).
 * Dữ liệu đọc từ danh sách ẩn [data-stats-days] do máy chủ in sẵn; màu lấy từ biến CSS --tone
 * của thẻ chứa nên tự đổi theo chế độ sáng/tối.
 */
(function () {
    'use strict';
    const list = document.querySelector('[data-stats-days]');
    if (!list) return;
    const days = Array.from(list.children).map(li => ({
        date: li.dataset.date,
        delivered: Number(li.dataset.delivered) || 0,
        failed: Number(li.dataset.failed) || 0,
        returned: Number(li.dataset.returned) || 0,
        cod: Number(li.dataset.cod) || 0,
        // Ngày chưa tới (kỳ "Tháng" gồm cả cuối tháng): không vẽ, tránh đường rơi về 0 như thể bị sụt.
        future: li.dataset.future === 'true'
    }));
    const NS = 'http://www.w3.org/2000/svg';
    const money = v => new Intl.NumberFormat('vi-VN').format(Math.round(v)) + ' đ';
    // Trục tiền ghi kèm đơn vị: 25k đ, 1,2 tr đ, 0 đ.
    const short = v => (v >= 1e6 ? (Math.round(v / 1e5) / 10).toString().replace('.', ',') + ' tr' : v >= 1e3 ? Math.round(v / 1e3) + 'k' : String(Math.round(v))) + ' đ';
    const range = list.dataset.range || '';
    let uid = 0;

    function el(name, attrs, parent) {
        const node = document.createElementNS(NS, name);
        Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
        if (parent) parent.appendChild(node);
        return node;
    }

    function draw(container, values, options) {
        // Vẽ đúng bằng bề rộng thật của khung để chữ trên trục không bị thu nhỏ khi thẻ hẹp.
        container.replaceChildren();
        const W = Math.min(900, Math.max(280, Math.round(container.clientWidth) || 560)), H = 220, L = 58, R = 14, T = 34, B = 30;
        const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, class: 'sx-chart-svg' }, container);
        const id = 'sxg' + (++uid);
        const defs = el('defs', {}, svg);
        const gradient = el('linearGradient', { id, x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
        el('stop', { offset: '0%', class: 'sx-chart-stop-top' }, gradient);
        el('stop', { offset: '100%', class: 'sx-chart-stop-bottom' }, gradient);

        const present = values.filter(v => v !== null);
        const max = options.max || Math.max(1, ...present);
        const n = values.length;
        const x = i => n <= 1 ? (L + (W - R)) / 2 : L + i * (W - L - R) / (n - 1);
        const y = v => T + (1 - v / max) * (H - T - B);

        [0, 0.5, 1].forEach(f => {
            const gy = y(max * f);
            el('line', { x1: L, x2: W - R, y1: gy, y2: gy, class: 'sx-chart-grid' }, svg);
            el('text', { x: L - 8, y: gy + 4, 'text-anchor': 'end', class: 'sx-chart-axis' }, svg).textContent = options.axis(max * f);
        });
        const step = Math.max(1, Math.ceil(n / Math.max(3, Math.floor((W - L - R) / 64))));
        values.forEach((_, i) => {
            if (i % step !== 0 && i !== n - 1) return;
            el('text', { x: x(i), y: H - 8, 'text-anchor': 'middle', class: 'sx-chart-axis' }, svg).textContent = days[i].date;
        });

        const points = values.map((v, i) => v === null ? null : [x(i), y(v), v, i]).filter(Boolean);
        if (!present.length || (options.hideZero && present.every(v => v === 0))) {
            el('text', { x: W / 2, y: H / 2, 'text-anchor': 'middle', class: 'sx-chart-empty' }, svg).textContent =
                options.empty + (range ? ' (' + range + ')' : '');
            return;
        }
        if (points.length > 1) {
            const line = points.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
            if (options.area) {
                el('path', { d: `${line} L ${points[points.length - 1][0].toFixed(1)} ${y(0)} L ${points[0][0].toFixed(1)} ${y(0)} Z`,
                    fill: `url(#${id})`, class: 'sx-chart-area' }, svg);
            }
            el('path', { d: line, class: 'sx-chart-line' }, svg);
        }
        // Ghi số ở điểm cao nhất (đỉnh) để người xem thấy ngay mức tốt nhất trong kỳ.
        const peak = points.reduce((best, p) => (best === null || p[2] > best[2] ? p : best), null);
        // Kỳ dài (trên 14 ngày): bỏ vòng tròn ở giá trị 0 cho đỡ rối, chỉ giữ ngày có số liệu.
        points.forEach(p => {
            if (n > 14 && p[2] === 0 && p !== peak) return;
            const dot = el('circle', { cx: p[0], cy: p[1], r: n > 14 ? 3.5 : 4.5, class: 'sx-chart-dot' }, svg);
            el('title', {}, dot).textContent = days[p[3]].date + ': ' + options.format(p[2]);
        });
        if (peak && peak[2] > 0) {
            // Lề trên (T) chừa sẵn chỗ nên nhãn luôn nằm trên đỉnh, không đè lên đường hay số ở trục.
            const anchor = peak[0] > W - 90 ? 'end' : peak[0] < L + 80 ? 'start' : 'middle';
            el('circle', { cx: peak[0], cy: peak[1], r: 6.5, class: 'sx-chart-peak' }, svg);
            el('text', { x: peak[0], y: peak[1] - 13, 'text-anchor': anchor, class: 'sx-chart-value' }, svg)
                .textContent = 'Cao nhất ' + options.format(peak[2]) + ' · ' + days[peak[3]].date;
        }
    }

    function render() {
        const cod = document.querySelector('[data-line-chart="cod"]');
        if (cod) draw(cod, days.map(d => d.future ? null : d.cod), {
            area: true, hideZero: true, axis: short, format: money, empty: 'Chưa thu cước COD trong kỳ này.'
        });
        const rate = document.querySelector('[data-line-chart="rate"]');
        if (rate) draw(rate, days.map(d => {
            if (d.future) return null;
            const total = d.delivered + d.failed + d.returned;
            return total ? Math.round(d.delivered * 1000 / total) / 10 : null;
        }), { area: true, max: 100, axis: v => Math.round(v) + '%', format: v => v + '%', empty: 'Chưa có lượt giao nào trong kỳ này.' });
    }
    render();
    // Vẽ lại khi đổi kích thước cửa sổ (xoay điện thoại, thu nhỏ trình duyệt).
    let lastWidth = window.innerWidth, timer = null;
    window.addEventListener('resize', () => {
        if (window.innerWidth === lastWidth) return;
        lastWidth = window.innerWidth;
        clearTimeout(timer);
        timer = setTimeout(render, 150);
    });
}());
