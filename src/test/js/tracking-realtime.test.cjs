const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/tracking-realtime.js'), 'utf8');

// DOM và STOMP giả lập; không cần mạng, trình duyệt hay package npm bổ sung.
function environment({ codes = ['UTESYNC01'], region = false, libraries = true } = {}) {
    const timers = new Map(), intervals = new Map(), listeners = new Map(), windowListeners = new Map();
    const callbacks = new Map(), removed = [], warnings = [], requests = [];
    let nextTimer = 0, connections = 0, disconnected = 0, reloads = 0, replacements = 0, connectionError;
    class Element {
        constructor() {
            this.dataset = {}; this.children = []; this.textContent = ''; this.listeners = new Map();
            const classes = new Set(); this.classList = { add: x => classes.add(x), remove: x => classes.delete(x) };
            this.outerHTML = '<section>old</section>';
        }
        setAttribute() { }
        querySelectorAll() { return []; }
        cloneNode() { const copy = new Element(); copy.outerHTML = this.outerHTML; return copy; }
        appendChild(child) { this.children.push(child); }
        prepend(child) { this.children.unshift(child); }
        addEventListener(type, handler) { this.listeners.set(type, handler); }
        querySelector(selector) {
            if (selector === '[data-order-status]') return this.badge;
            return this.children.find(child => child.dataset.realtimeMessage);
        }
        replaceWith(replacement) { state.regions = state.regions.map(item => item === this ? replacement : item); replacements++; }
    }
    const main = new Element();
    const state = { regions: [], elements: codes.map(code => {
        const element = new Element(); element.dataset.trackingCode = code; element.badge = new Element(); return element;
    }) };
    const replacement = new Element(); replacement.dataset.realtimeRegion = 'test'; replacement.outerHTML = '<section>new</section>';
    if (region) { const original = new Element(); original.dataset.realtimeRegion = 'test'; state.regions.push(original); }
    const document = {
        body: main, activeElement: null, hidden: false,
        querySelectorAll(selector) { return selector === '[data-tracking-code]' ? state.elements : state.regions; },
        querySelector(selector) { return selector === 'main' ? main : selector === '[data-realtime-region]' ? state.regions[0] : null; },
        createElement() { return new Element(); },
        dispatchEvent(event) { listeners.get(event.type)?.(event); },
        addEventListener(type, handler) { listeners.set(type, handler); }
    };
    const client = {
        connected: true,
        connect(headers, onSuccess, onError) { connections++; connectionError = onError; onSuccess(); },
        subscribe(destination, handler) {
            callbacks.set(destination, handler);
            return { unsubscribe() { removed.push(destination); callbacks.delete(destination); } };
        },
        disconnect() { disconnected++; }
    };
    const window = {
        location: { href: 'https://ute.test/nguoi-dung/don-hang/1', reload() { reloads++; } },
        confirm: () => false,
        setTimeout(handler, delay) { timers.set(++nextTimer, { handler, delay }); return nextTimer; },
        setInterval(handler, delay) { intervals.set(++nextTimer, { handler, delay }); return nextTimer; },
        addEventListener(type, handler) { windowListeners.set(type, handler); }
    };
    const clearTimeout = id => timers.delete(id), clearInterval = id => intervals.delete(id);
    let fetchResponse = async () => ({ ok: true, redirected: false, text: async () => '<html></html>' });
    const context = {
        document, window, clearTimeout, clearInterval, AbortController, Event,
        DOMParser: class { parseFromString() { return { querySelectorAll() { return [replacement]; } }; } },
        fetch: async (...args) => { requests.push(args); return fetchResponse(...args); },
        console: { warn: (...args) => warnings.push(args) }
    };
    if (libraries) { context.SockJS = function () { }; context.Stomp = { over: () => client }; }
    vm.runInNewContext(source, context);
    return {
        state, document, timers, intervals, requests, removed, warnings, replacement, listeners,
        get connections() { return connections; }, get disconnected() { return disconnected; },
        get replacements() { return replacements; }, get reloads() { return reloads; },
        get notice() { return main.children[0]?.children[0]?.textContent || ''; },
        send(event) { callbacks.get('/topic/order/UTESYNC01')({ body: typeof event === 'string' ? event : JSON.stringify(event) }); },
        emit(type, value = {}) { listeners.get(type)?.(value); },
        emitWindow(type, value = {}) { windowListeners.get(type)?.(value); },
        failConnection() { connectionError(); },
        setFetch(handler) { fetchResponse = handler; },
        async flush(delay = 350) {
            for (const [id, timer] of Array.from(timers)) if (timer.delay === delay) { timers.delete(id); await timer.handler(); }
        },
        async poll() { for (const timer of intervals.values()) await timer.handler(); }
    };
}
const event = (status = 'DELIVERED', updatedAt = '2026-10-16T10:00:00') => ({ trackingCode: 'UTESYNC01', status, updatedAt });
const input = { target: { closest: () => ({}) } };

test('trang không có vận đơn/vùng đọc không mở kết nối hoặc timer', () => {
    const env = environment({ codes: [] }); assert.equal(env.connections, 0); assert.equal(env.intervals.size, 0);
});
test('sự kiện hợp lệ cập nhật trạng thái bằng text an toàn', async () => {
    const env = environment(); env.send(event());
    assert.equal(env.state.elements[0].badge.textContent, 'Giao thành công');
    assert.equal(env.state.elements[0].badge.dataset.orderStatus, 'DELIVERED'); await env.flush();
});
test('bỏ qua enum lạ, mã không thuộc trang và mã chứa HTML', () => {
    const env = environment();
    env.send(event('<img onerror=alert(1)>'));
    env.send({ ...event(), trackingCode: 'OTHER' });
    env.send({ ...event(), trackingCode: '<script>' });
    assert.equal(env.state.elements[0].badge.textContent, ''); assert.equal(env.notice, '');
});
test('sự kiện cũ đến sau không ghi đè trạng thái mới', () => {
    const env = environment(); env.send(event('DELIVERED', '2026-10-16T11:00:00'));
    env.send(event('IN_TRANSIT', '2026-10-16T10:00:00')); assert.equal(env.state.elements[0].badge.dataset.orderStatus, 'DELIVERED');
});
test('sự kiện cũ hơn ảnh chụp trang cũng bị bỏ qua', () => {
    const env = environment(); env.state.elements[0].dataset.updatedAt = '2026-10-16T12:00:00';
    env.send(event('IN_TRANSIT', '2026-10-16T11:00:00')); assert.equal(env.state.elements[0].badge.textContent, '');
});
test('dữ liệu không thay đổi không bị thay vùng trang vô ích', async () => {
    const env = environment({ region: true }); env.replacement.outerHTML = env.state.regions[0].outerHTML;
    await env.poll(); assert.equal(env.replacements, 0);
});
test('vùng trang thay mới thông báo để khởi tạo lại validation đúng một lần',async()=>{
    const env=environment({region:true});let initialized=0;
    env.document.addEventListener('ute:regions-updated',()=>initialized++);
    await env.poll();assert.equal(env.replacements,1);assert.equal(initialized,1);
    env.replacement.outerHTML=env.state.regions[0].outerHTML;
    await env.poll();assert.equal(initialized,1);
});
test('JSON lỗi không làm chết kết nối', () => {
    const env = environment(); env.send('{'); assert.equal(env.warnings.length, 1); env.send(event());
    assert.equal(env.state.elements[0].badge.dataset.orderStatus, 'DELIVERED');
});
test('không thay form người dùng đang nhập hoặc đã gửi', async () => {
    for (const type of ['input', 'submit']) {
        const env = environment({ region: true }); env.emit(type, input); await Promise.resolve(); env.send(event()); await env.flush();
        assert.equal(env.requests.length, 0); assert.equal(env.replacements, 0);
    }
});
test('ký trên canvas cũng giữ nguyên form khi có cập nhật realtime',async()=>{
    const env=environment({region:true});env.emit('pointerdown',{target:{tagName:'CANVAS',closest:()=>({})}});
    env.send(event());await env.flush();assert.equal(env.requests.length,0);assert.equal(env.replacements,0);
});
test('submit bị hộp xác nhận chặn không khóa làm mới vĩnh viễn', async () => {
    const env = environment({ region: true }); env.emit('submit', { defaultPrevented: true }); await Promise.resolve();
    env.send(event()); await env.flush(); assert.equal(env.replacements, 1);
});
test('GET mới thay vùng dữ liệu, không thực hiện POST hay bỏ thông tin xác thực', async () => {
    const env = environment({ region: true }); env.send(event()); await env.flush();
    assert.equal(env.replacements, 1); assert.equal(env.requests[0][1].credentials, 'same-origin');
    assert.equal(env.requests[0][1].cache, 'no-store'); assert.equal(env.requests[0][1].method, undefined);
});
test('401 hoặc chuyển về đăng nhập giữ nguyên trang và báo người dùng', async () => {
    for (const response of [{ ok: false }, { ok: true, redirected: true }]) {
        const env = environment({ region: true }); env.setFetch(async () => response); env.send(event()); await env.flush();
        assert.equal(env.replacements, 0); assert.match(env.notice, /đăng nhập lại/);
    }
});
test('nếu bắt đầu nhập trong lúc GET đang chờ thì không thay dữ liệu', async () => {
    const env = environment({ region: true }); let resolve;
    env.setFetch(() => new Promise(done => { resolve = done; })); env.send(event());
    const pending = env.flush(); env.emit('input', input);
    resolve({ ok: true, redirected: false, text: async () => '<html></html>' }); await pending;
    assert.equal(env.replacements, 0);
});
test('khi thiếu thư viện CDN vẫn đối chiếu Aiven qua GET mỗi 30 giây', async () => {
    const env = environment({ codes: [], region: true, libraries: false });
    assert.equal(env.connections, 0); assert.equal(Array.from(env.intervals.values())[0].delay, 30000);
    await env.poll(); await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    assert.equal(env.requests.length, 1);
});
test('không polling tab ẩn hoặc form đã chỉnh sửa', async () => {
    const hidden = environment({ region: true }); hidden.document.hidden = true; await hidden.poll(); assert.equal(hidden.requests.length, 0);
    const dirty = environment({ region: true }); dirty.emit('change', input); await dirty.poll(); assert.equal(dirty.requests.length, 0);
});
test('đóng trang dọn timer, còn quay lại từ bộ nhớ trang thì kết nối lại', () => {
    const env = environment({ region: true }); env.send(event()); env.emitWindow('pagehide');
    assert.equal(env.disconnected, 1); assert.equal(env.intervals.size, 0); assert.equal(env.timers.size, 0);
    env.emitWindow('pageshow', { persisted: true }); assert.equal(env.connections, 2); assert.equal(env.intervals.size, 1);
});
test('mất kết nối sử dụng backoff tối đa 30 giây', () => {
    const env = environment();
    for (const delay of [3000, 6000, 12000, 24000, 30000, 30000]) {
        env.failConnection(); assert.equal(Array.from(env.timers.values()).at(-1).delay, delay);
    }
});
