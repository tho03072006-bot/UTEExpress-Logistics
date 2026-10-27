const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/staff-navigation.js'), 'utf8');

function createNav(role='MANAGER') {
    const groups = [{open:false, hidden:false, links:[], label:'Đơn hàng & điều phối', key:'operations'},
        {open:true, hidden:false, links:[], label:'Chăm sóc & tài chính', key:'finance'}];
    const input = {value:'', listeners:{}, addEventListener(type, listener) {this.listeners[type] = listener;}};
    const empty = {hidden:true};
    const links = [['Tổng quan vận hành','/manager/trang-chu', null], ['Tra soát đơn hàng','/manager/don-hang', groups[0]],
        ['Chi tiết đơn','/manager/don-hang/chi-tiet', groups[0]], ['Đối soát cước COD','/manager/doi-soat-cod',groups[1]]]
        .map(([textContent, pathname, group]) => {
            const classes = new Set();
            const link = {textContent, pathname, hidden:false, classList:{add:name=>classes.add(name)}, classes, attributes:{},
                setAttribute(name,value) {this.attributes[name]=value;}, closest:()=>group};
            if (group) group.links.push(link); return link;
        });
    groups.forEach(group => {
        group.querySelectorAll = () => group.links;
        group.querySelector = () => ({textContent:group.label});
        group.dataset = {staffGroup:group.key}; group.listeners = {};
        group.addEventListener = (type, listener) => {group.listeners[type] = listener;};
    });
    const nav = {dataset:{staffRole:role}, querySelectorAll:selector => selector === 'a[href]' ? links : groups,
        querySelector:selector => selector === '[data-staff-menu-search]' ? input : empty};
    return {nav, groups, input, empty, links, search(value) {input.value=value; input.listeners.input();}};
}
function boot(pathname='/manager/trang-chu', navs=[createNav()], storage=new Map()) {
    const sessionStorage = storage ? {getItem:key=>storage.get(key) ?? null, setItem:(key,value)=>storage.set(key,value)}
        : {getItem() {throw new Error('Storage blocked');}, setItem() {throw new Error('Storage blocked');}};
    vm.runInNewContext(source, {window:{location:{pathname},sessionStorage}, document:{querySelectorAll:()=>navs.map(state=>state.nav)}});
    return navs;
}
test('Chọn đường dẫn phù hợp dài nhất và mở nhóm hiện tại', () => {
    const [state] = boot('/manager/don-hang/chi-tiet/4');
    assert.equal(state.links[2].attributes['aria-current'], 'page'); assert.equal(state.groups[0].open, true);
    assert.equal(state.links[1].attributes['aria-current'], undefined);
});
test('Tìm tiếng Việt không dấu, có dấu, chữ hoa Đ đều cho cùng kết quả', () => {
    const [state] = boot();
    for (const query of ['doi soat','ĐỐI SOÁT','đối soát']) {
        state.search(query); assert.equal(state.links[3].hidden, false); assert.equal(state.links[1].hidden, true);
        assert.equal(state.groups[1].hidden, false); assert.equal(state.groups[0].hidden, true);
    }
});
test('Không khớp hiển thị thông báo, xóa tìm kiếm khôi phục nhóm trước đó', () => {
    const [state] = boot(); state.search('không tồn tại'); assert.equal(state.empty.hidden, false);
    state.search(''); assert.equal(state.empty.hidden, true);
    assert.equal(state.groups[0].open, false); assert.equal(state.groups[1].open, true);
    assert.ok(state.links.every(link=>!link.hidden));
});
test('Thanh bên desktop và mobile tìm kiếm độc lập', () => {
    const [desktop, mobile] = boot('/manager/trang-chu',[createNav(),createNav()]); desktop.search('đối soát');
    assert.equal(desktop.links[1].hidden,true); assert.equal(mobile.links[1].hidden,false);
});
test('Chuỗi HTML không bị thực thi và đường dẫn không khớp không đánh dấu nhầm', () => {
    const [state] = boot('/manager/don-hang-khac'); state.search('<script>alert(1)</script>');
    assert.equal(state.empty.hidden,false); assert.ok(state.links.every(link=>!link.classes.has('active')));
});

test('Tìm theo tên nhóm hiển thị đủ các chức năng trong nhóm', () => {
    const [state] = boot(); state.search('dieu phoi');
    assert.equal(state.links[1].hidden,false); assert.equal(state.links[2].hidden,false);
    assert.equal(state.links[3].hidden,true); assert.equal(state.groups[0].open,true);
});

test('Escape xóa tìm kiếm và khôi phục menu', () => {
    const [state] = boot(); state.search('không có'); let prevented=false;
    state.input.listeners.keydown({key:'Escape',preventDefault() {prevented=true;}});
    assert.equal(state.input.value,''); assert.equal(state.empty.hidden,true);
    assert.ok(state.links.every(link=>!link.hidden)); assert.equal(prevented,true);
});

test('Nhớ nhóm mở qua lần chuyển trang và tách lựa chọn theo vai trò', () => {
    const storage = new Map(); const [first] = boot(undefined,undefined,storage);
    first.groups[0].open=true; first.groups[0].listeners.toggle();
    first.groups[1].open=false; first.groups[1].listeners.toggle();
    const [next] = boot(undefined,undefined,storage);
    assert.equal(next.groups[0].open,true); assert.equal(next.groups[1].open,false);
    const [admin] = boot(undefined,[createNav('ADMIN')],storage);
    assert.equal(admin.groups[0].open,false); assert.equal(admin.groups[1].open,true);
});

test('Luôn mở nhóm chứa trang hiện tại kể cả lựa chọn đã lưu là đóng', () => {
    const saved = JSON.stringify({operations:false});
    const storage = new Map([['ute.staff.menu.MANAGER',saved]]);
    const [state] = boot('/manager/don-hang',undefined,storage);
    assert.equal(state.groups[0].open,true); assert.equal(state.links[1].attributes['aria-current'],'page');
    state.groups[0].listeners.toggle();
    assert.equal(storage.get('ute.staff.menu.MANAGER'),saved);
});

test('Tìm kiếm không ghi đè lựa chọn mở nhóm của người dùng', () => {
    const saved = JSON.stringify({operations:false,finance:true});
    const storage = new Map([['ute.staff.menu.MANAGER',saved]]);
    const [state] = boot(undefined,undefined,storage); state.search('điều phối');
    state.groups[0].listeners.toggle(); assert.equal(storage.get('ute.staff.menu.MANAGER'),saved);
    state.search(''); assert.equal(state.groups[0].open,false); assert.equal(state.groups[1].open,true);
});

test('Storage bị chặn hoặc dữ liệu hỏng không làm hỏng điều hướng', () => {
    for (const saved of ['{bad json}', '[]','null','{"operations":"false","finance":1}']) {
        const [state] = boot(undefined,undefined,new Map([['ute.staff.menu.MANAGER',saved]]));
        assert.equal(state.groups[0].open,false); assert.equal(state.groups[1].open,true);
        state.search('đối soát'); assert.equal(state.links[3].hidden,false);
    }
    const [blocked] = boot(undefined,undefined,null);
    blocked.groups[0].open=true; assert.doesNotThrow(()=>blocked.groups[0].listeners.toggle());
    blocked.search('đối soát'); assert.equal(blocked.links[3].hidden,false);
});
