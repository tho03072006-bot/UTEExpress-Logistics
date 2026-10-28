'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const createPoller = require('../../main/resources/static/js/momo-simulator.js');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/momo-simulator.js'), 'utf8');
const flush = async () => { for (let index = 0; index < 12; index++) await Promise.resolve(); };
const response = (state, status = 200) => ({status, json: async () => ({state, message: state})});
function clock() {
    let now = 0, serial = 0;
    const timers = new Map();
    const setTimeout = (fn, delay) => { timers.set(++serial, {fn, at: now + delay}); return serial; };
    const clearTimeout = id => timers.delete(id);
    return {timers, setTimeout, clearTimeout, now: () => now, async advance(ms) {
        const end = now + ms;
        for (;;) {
            const next = [...timers].sort((a, b) => a[1].at - b[1].at).find(([, timer]) => timer.at <= end);
            if (!next) break;
            now = next[1].at; timers.delete(next[0]); next[1].fn(); await flush();
        }
        now = end; await flush();
    }};
}
function harness(fetch) {
    const time = clock(), updates = [];
    const poller = createPoller({url: '/momo/phien/test/trang-thai', fetch, onUpdate: item => updates.push(item),
        setTimeout: time.setTimeout, clearTimeout: time.clearTimeout});
    return {time, updates, poller};
}

test('MoMo kiểm tra mỗi 3 giây và dừng ngay khi QR bị thay thế', async () => {
    let calls = 0;
    const h = harness(async () => response(++calls === 3 ? 'REPLACED' : 'UNPAID', calls === 3 ? 409 : 200));
    h.poller.start(); await flush(); assert.equal(calls, 1);
    await h.time.advance(2999); assert.equal(calls, 1); await h.time.advance(1); assert.equal(calls, 2);
    await h.time.advance(3000); assert.equal(calls, 3); assert.equal(h.updates.at(-1).state, 'REPLACED');
    await h.time.advance(60000); assert.equal(calls, 3); assert.equal(h.time.timers.size, 0);
});
test('MoMo dừng khi thiết bị khác đã trả hoặc phiên đã hết hiệu lực', async () => {
    for (const [state, status] of [['ALREADY_PAID', 409], ['PAID', 200], ['UNAVAILABLE', 410]]) {
        const h = harness(async () => response(state, status)); h.poller.start(); await flush();
        assert.equal(h.updates.at(-1).state, state); assert.equal(h.time.timers.size, 0);
    }
});
test('MoMo không gửi chồng request dù gọi start nhiều lần khi Aiven chậm', async () => {
    let resolve, calls = 0;
    const h = harness(() => { calls++; return new Promise(done => { resolve = done; }); });
    h.poller.start(); h.poller.start(); h.poller.start(); await h.time.advance(7000); assert.equal(calls, 1);
    resolve(response('UNPAID')); await flush(); await h.time.advance(3000); assert.equal(calls, 2);
    h.poller.stop(); resolve(response('UNPAID')); await flush(); assert.equal(h.time.timers.size, 0);
});
test('Mất mạng chỉ báo thử lại, có backoff tối đa 15 giây và không giả thành công', async () => {
    let calls = 0;
    const h = harness(async () => { calls++; throw new Error('offline'); });
    h.poller.start(); await flush(); assert.equal(h.updates.at(-1).state, 'RETRY');
    await h.time.advance(6000); assert.equal(calls, 2); await h.time.advance(12000); assert.equal(calls, 3);
    await h.time.advance(15000); assert.equal(calls, 4); assert.ok(h.updates.every(item => item.state === 'RETRY'));
    h.poller.stop(); assert.equal(h.time.timers.size, 0);
});
test('Dừng tab và khởi động lại bỏ qua phản hồi cũ, kể cả phản hồi đã trả tiền', async () => {
    let resolve, calls = 0;
    const h = harness(() => { calls++; return new Promise(done => { resolve = done; }); });
    h.poller.start(); h.poller.stop(); h.poller.start(); resolve(response('PAID')); await flush();
    assert.equal(h.updates.length, 0); await h.time.advance(0); assert.equal(calls, 2);
    h.poller.stop(); resolve(response('PAID')); await flush(); assert.equal(h.updates.length, 0);
});
test('Không nhận HTML redirect, trạng thái lạ hoặc nội dung sai như kết quả thanh toán', async () => {
    for (const reply of [{...response('PAID'), redirected: true}, response('PAID', 503), response('success'), response('UNPAID', 409),
        {status: 200, json: async () => null}, {status: 200, json: async () => ({state: 'PAID', message: 'x'.repeat(501)})}]) {
        const h = harness(async () => reply); h.poller.start(); await flush();
        assert.equal(h.updates.at(-1).state, 'RETRY'); h.poller.stop();
    }
});

function widget() {
    const time = clock(), events = {}, requests = [], form = {hidden: false}, button = {disabled: false, dataset: {label: 'Xác nhận thanh toán'}};
    const feedback = {textContent: ''}, countdown = {textContent: ''}, money = {textContent: '18000'};
    let reloads = 0;
    const checkout = {dataset: {expires: '600', statusUrl: '/momo/phien/test/trang-thai'}, querySelector(selector) {
        return {'[data-confirm-form]': form, '[data-confirm-button]': button, '[data-feedback]': feedback}[selector];
    }};
    form.addEventListener = (type, handler) => { events['form:' + type] = handler; };
    const document = {hidden: false, querySelectorAll: () => [money], querySelector: selector => selector === '[data-checkout]' ? checkout : countdown,
        addEventListener: (type, handler) => { events[type] = handler; }};
    const window = {document, navigator: {onLine: true}, location: {reload: () => reloads++},
        addEventListener: (type, handler) => { events[type] = handler; }, setTimeout: time.setTimeout, clearTimeout: time.clearTimeout,
        fetch: (url, options) => new Promise(resolve => requests.push({url, options, resolve}))};
    vm.runInNewContext(source, {window, Date: {now: time.now}, Intl, AbortController, setTimeout: time.setTimeout, clearTimeout: time.clearTimeout});
    return {time, window, document, events, requests, form, button, feedback, money, reloads: () => reloads};
}
test('Giao diện khóa form khi QR bị thay thế và không còn timer chạy', async () => {
    const h = widget(); h.requests[0].resolve(response('REPLACED', 409)); await flush();
    assert.equal(h.form.hidden, true); assert.equal(h.button.disabled, true); assert.equal(h.time.timers.size, 0);
    assert.equal(h.feedback.textContent, 'REPLACED'); assert.match(h.money.textContent, /18\.000/);
});
test('Giao diện chặn submit lần hai ngay lập tức, backend vẫn là nguồn chống thu trùng', async () => {
    const h = widget(); let prevented = 0;
    h.events['form:submit']({defaultPrevented: false, preventDefault: () => prevented++});
    h.events['form:submit']({defaultPrevented: false, preventDefault: () => prevented++});
    assert.equal(prevented, 1); assert.equal(h.button.disabled, true); assert.equal(h.time.timers.size, 0);
    assert.equal(h.requests[0].options.signal.aborted, true);
});
test('Hết hạn hoặc mất mạng không thể submit; tab ẩn không tiếp tục hỏi database', async () => {
    const h = widget(); h.window.navigator.onLine = false; h.events.offline(); assert.equal(h.button.disabled, true);
    let prevented = false; h.events['form:submit']({preventDefault: () => { prevented = true; }}); assert.equal(prevented, true);
    const expired = widget(); await expired.time.advance(600000); assert.equal(expired.form.hidden, true); assert.equal(expired.button.disabled, true);
    const hidden = widget(); hidden.document.hidden = true; hidden.events.visibilitychange();
    assert.equal(hidden.time.timers.size, 0); assert.equal(hidden.requests[0].options.signal.aborted, true);
});
test('Thiết bị khác thanh toán thì đóng form và đọc lại kết quả từ server', async () => {
    const h = widget(); h.requests[0].resolve(response('ALREADY_PAID', 409)); await flush();
    assert.equal(h.form.hidden, true); assert.equal(h.reloads(), 1); assert.equal(h.time.timers.size, 0);
});
