'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/account-validation.js'),'utf8');
function input(value=''){return {value,dataset:{},listeners:{},error:'',setCustomValidity(value){this.error=value;},addEventListener(type,fn){const previous=this.listeners[type];this.listeners[type]=()=>{if(previous)previous();fn();};}};}
function setup(){
 const password=input(),confirm=input();confirm.dataset.passwordConfirm='newPassword';let ready;
 vm.runInNewContext(source,{TextEncoder,document:{addEventListener(type,fn){ready=fn;},querySelectorAll(selector){return selector.includes('data-public-password')?[password]:[confirm];},getElementById(){return password;}}});ready();return {password,confirm};
}
test('Mật khẩu frontend kiểm tra chiều dài, chữ/số, khoảng trắng và giới hạn UTF-8',()=>{
 const {password}=setup();
 for(const value of ['a','abcdefgh','12345678','Password 123','ắ'.repeat(30)+'1']){password.value=value;password.listeners.input();assert.notEqual(password.error,'');}
 for(const value of ['Password123','MậtKhẩu123']){password.value=value;password.listeners.input();assert.equal(password.error,'');}
});
test('Xác nhận mật khẩu cập nhật khi sửa một trong hai ô',()=>{
 const {password,confirm}=setup();password.value='Password123';confirm.value='Different123';confirm.listeners.input();assert.notEqual(confirm.error,'');
 confirm.value='Password123';confirm.listeners.input();assert.equal(confirm.error,'');password.value='Changed123';password.listeners.input();assert.notEqual(confirm.error,'');
});
