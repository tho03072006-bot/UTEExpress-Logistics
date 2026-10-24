'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/account-validation.js'),'utf8');
function input(value=''){return {value,dataset:{},listeners:{},error:'',setCustomValidity(value){this.error=value;},addEventListener(type,fn){const previous=this.listeners[type];this.listeners[type]=()=>{if(previous)previous();fn();};}};}
function setup(){
 const password=input(),confirm=input(),phone=input();confirm.dataset.passwordConfirm='newPassword';let ready;
 vm.runInNewContext(source,{TextEncoder,document:{addEventListener(type,fn){ready=fn;},querySelectorAll(selector){return selector.includes('data-public-password')?[password]:selector.includes('data-password-confirm')?[confirm]:selector.includes('data-vn-mobile')?[phone]:[];},getElementById(){return password;}}});ready();return {password,confirm,phone};
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
test('Điện thoại tài xế đồng bộ quy tắc backend, nhận +84 và từ chối đầu số sai',()=>{
 const {phone}=setup();
 for(const value of ['0901234567','+84901234567','090 123-45.67']){phone.value=value;phone.listeners.input();assert.equal(phone.error,'',value);}
 for(const value of ['0101234567','090123456','(+84)901234567','abc','+84 901234567'+' '.repeat(15)]){phone.value=value;phone.listeners.input();assert.notEqual(phone.error,'',value);}
});

test('Các form tài khoản, tạo đơn và sổ địa chỉ dùng chung kiểm tra điện thoại',()=>{
 for(const file of ['shipper/account.html','user/profile.html','user/address-form.html','user/order-form.html','admin/all-account-create.html','admin/all-account-edit.html']) {
  const html=fs.readFileSync(path.join(__dirname,'../../main/resources/templates',file),'utf8');
  const input=html.match(/<input[^>]+(?:Phone|phone)[^>]*>/g)?.find(s=>/type="tel"/.test(s));
  assert.ok(input,file);assert.match(input,/data-vn-mobile/,file);assert.match(input,/maxlength="20"/,file);
 }
});
