'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const photo=require('../../main/resources/static/js/user-photo.js');
function storage(initial){const data=Object.assign({},initial);return {data,getItem(k){return k in data?data[k]:null;},setItem(k,v){data[k]=String(v);}};}
function doc(){const attrs={};return {attrs,documentElement:{setAttribute(k,v){attrs[k]=v;},removeAttribute(k){delete attrs[k];}}};}
test('Mặc định là Bưu điện Sài Gòn; giá trị lạ trong localStorage bị bỏ qua',()=>{
 assert.equal(photo.current(storage({})),'post-office');
 assert.equal(photo.current(storage({'ute-user-photo':'<script>'})),'post-office');
 assert.equal(photo.current(storage({'ute-user-photo':'locker'})),'locker');
});
test('Chọn ảnh thì đặt data-user-photo và lưu lại; chọn mặc định thì gỡ thuộc tính',()=>{
 const d=doc(),s=storage({});
 assert.equal(photo.apply(d,s,'doorstep'),'doorstep');
 assert.equal(d.attrs['data-user-photo'],'doorstep');assert.equal(s.data['ute-user-photo'],'doorstep');
 photo.apply(d,s,'post-office');
 assert.equal(d.attrs['data-user-photo'],undefined);assert.equal(s.data['ute-user-photo'],'post-office');
});
test('Mã ảnh không có trong danh sách quay về mặc định; localStorage bị chặn vẫn đổi được ảnh',()=>{
 const d=doc();
 assert.equal(photo.apply(d,{setItem(){throw new Error('blocked');}},'khong-co'),'post-office');
 assert.equal(photo.apply(d,{setItem(){throw new Error('blocked');}},'locker'),'locker');
 assert.equal(d.attrs['data-user-photo'],'locker');
});
test('Mỗi ảnh trong bảng chọn có CSS tương ứng',()=>{
 const css=require('node:fs').readFileSync(require('node:path').join(__dirname,'../../main/resources/static/css/ute-app.css'),'utf8');
 for(const p of photo.PHOTOS.filter(p=>p.id!=='post-office')) assert.match(css,new RegExp(`data-user-photo="${p.id}"`));
});
