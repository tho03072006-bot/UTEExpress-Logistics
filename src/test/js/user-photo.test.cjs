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

test('bảng chọn ảnh được khôi phục sau realtime và không giữ handler của tiêu đề cũ',()=>{
 const vm=require('node:vm'),fs=require('node:fs');
 class Element {
  constructor(){this.children=[];this.dataset={};this.attributes={};this.listeners={};this.className='';}
  setAttribute(k,v){this.attributes[k]=v;} removeAttribute(k){delete this.attributes[k];}
  appendChild(child){child.parent=this;this.children.push(child);}
  addEventListener(type,fn){this.listeners[type]=fn;}
  querySelectorAll(selector){return this.children.filter(child=>'.'+child.className===selector);}
  querySelector(selector){return this.querySelectorAll(selector)[0]||null;}
  remove(){if(this.parent)this.parent.children=this.parent.children.filter(child=>child!==this);}
  contains(node){return node===this||this.children.some(child=>child.contains(node));} focus(){}
 }
 const listeners=new Map(),document={head:new Element(),documentElement:new Element(),
  querySelector(selector){return selector.startsWith('.ute-page-head')?this.head:{href:'https://fixture.invalid/css/ute-app.css?v=1'};},
  createElement(){return new Element();},addEventListener(type,fn){listeners.set(type,[...(listeners.get(type)||[]),fn]);},
  removeEventListener(type,fn){listeners.set(type,(listeners.get(type)||[]).filter(handler=>handler!==fn));}};
 const emit=type=>(listeners.get(type)||[]).slice().forEach(fn=>fn({}));
 vm.runInNewContext(fs.readFileSync(require.resolve('../../main/resources/static/js/user-photo.js'),'utf8'),{window:{document,localStorage:storage({})},URL});
 emit('DOMContentLoaded');assert.equal(document.head.querySelectorAll('.sx-photo-btn').length,1);
 document.head=new Element();emit('ute:regions-updated');emit('ute:regions-updated');
 assert.equal(document.head.querySelectorAll('.sx-photo-btn').length,1);
 assert.equal(document.head.querySelectorAll('.sx-photo-menu').length,1);
 assert.equal(listeners.get('keydown').length,1);assert.equal(listeners.get('click').length,1);
});
