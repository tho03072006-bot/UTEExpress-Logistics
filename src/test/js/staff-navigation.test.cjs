const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/staff-navigation.js'), 'utf8');

function createNav() {
    const groups = [{open:false, hidden:false, links:[]}, {open:true, hidden:false, links:[]}];
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
    groups.forEach(group => {group.querySelectorAll = () => group.links;});
    const nav = {querySelectorAll:selector => selector === 'a[href]' ? links : groups,
        querySelector:selector => selector === '[data-staff-menu-search]' ? input : empty};
    return {nav, groups, input, empty, links, search(value) {input.value=value; input.listeners.input();}};
}
function boot(pathname='/manager/trang-chu', navs=[createNav()]) {
    vm.runInNewContext(source, {window:{location:{pathname}}, document:{querySelectorAll:()=>navs.map(state=>state.nav)}});
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
