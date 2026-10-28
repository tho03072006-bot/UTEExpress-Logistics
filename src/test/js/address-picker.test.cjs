'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/address-picker.js'), 'utf8');
const messages = require('../../main/resources/static/js/form-messages-vi.js');

function setup() {
    const inputs = {}, events = [], attributes = {
        'data-fill-name': 'receiverName', 'data-fill-phone': 'receiverPhone', 'data-fill-address': 'receiverAddress',
        'data-fill-province': 'province', 'data-fill-area': 'area'
    };
    for (const id of Object.values(attributes)) inputs[id] = {
        value: '', validity: {valueMissing: true}, type: 'text',
        setCustomValidity(message) { this.message = message; this.validity.customError = Boolean(message); },
        dispatchEvent(event) { events.push({id, type: event.type, bubbles: event.bubbles}); messages.onEdit({target: this}); }
    };
    const saved = {'data-contact-name': 'Người Nhận', 'data-contact-phone': '0901234567',
        'data-address-line': 'Số 01 đường kiểm thử', 'data-province-code': '79', 'data-area-code': 'SGN-THUDUC'};
    const picker = {selectedIndex: 1, options: [{value: ''}, {value: '1', getAttribute(key) { return saved[key] ?? null; }}],
        getAttribute(key) { return attributes[key] ?? null; }, addEventListener(type, handler) { this[type] = handler; }};
    vm.runInNewContext(source, {Event, document: {getElementById(id) { return inputs[id]; },
        querySelectorAll() { return [picker]; }, addEventListener(type, handler) { handler(); }}});
    return {inputs, events, picker};
}

test('Chọn sổ địa chỉ phát input/change để xóa lỗi required cũ và chạy lại validation', () => {
    const q = setup();
    for (const id of ['receiverName', 'receiverPhone', 'receiverAddress']) messages.onInvalid({target: q.inputs[id]});
    assert.equal(q.inputs.receiverPhone.validity.customError, true);
    q.picker.change();
    assert.equal(q.inputs.receiverName.value, 'Người Nhận');
    assert.equal(q.inputs.receiverPhone.value, '0901234567');
    for (const id of ['receiverName', 'receiverPhone', 'receiverAddress']) {
        assert.equal(q.inputs[id].validity.customError, false, id + ' không được giữ lỗi của dữ liệu cũ');
        assert.deepEqual(q.events.filter(event => event.id === id).map(event => event.type), ['input', 'change']);
    }
    assert.equal(q.inputs.province.value, '79'); assert.equal(q.inputs.area.value, 'SGN-THUDUC');
    assert.ok(q.events.every(event => event.bubbles));
});

test('Chọn lại nhập địa chỉ mới không xóa dữ liệu đang điền hoặc phát sự kiện thừa', () => {
    const q = setup(); q.inputs.receiverAddress.value = 'Địa chỉ đang gõ'; q.picker.selectedIndex = 0; q.picker.change();
    assert.equal(q.inputs.receiverAddress.value, 'Địa chỉ đang gõ'); assert.equal(q.events.length, 0);
});
