'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/main.js'),'utf8');
function setup(onceBeforeConfirmation=false) {
 const loaded=[],listeners={},timers=[],accept={},dialog={},message={};
 for(const el of [accept,dialog])el.addEventListener=(name,fn)=>el[name]=fn;
 const fields={uteConfirmDialog:dialog,uteConfirmMessage:message,uteConfirmAccept:accept};
 const document={addEventListener(name,fn){if(name==='DOMContentLoaded')loaded.push(fn);else(listeners[name]??=[]).push(fn);},
  removeEventListener(name,fn){listeners[name]=listeners[name].filter(f=>f!==fn);},getElementById(id){return fields[id]||null;},querySelectorAll(){return [];},querySelector(){return null;}};
 if(onceBeforeConfirmation)document.addEventListener('submit',event=>{
  const form=event.target;if(event.defaultPrevented||form.dataset.uteConfirmed!=='true')return;
  if(form.dataset.sxSubmitting==='true')event.preventDefault();else form.dataset.sxSubmitting='true';
 });
 vm.runInNewContext(source,{document,window:{location:{pathname:'/test'},addEventListener(){},bootstrap:{Modal:class{show(){}hide(){}}},setTimeout(fn){timers.push(fn);}},setTimeout(fn){timers.push(fn);}});
 loaded.forEach(fn=>fn());
 let native=0,requested=0,sent=0,requester=null;const button={disabled:false,classList:{add(){}}};
 const form={dataset:{},getAttribute(name){return name==='data-ute-confirm'?'Xác nhận?':null;},querySelectorAll(){return [button];},valid:true,blocked:false,
  submit(){native++;sent++;},requestSubmit(submitter){requested++;requester=submitter;if(!this.valid)return;const e=dispatch();if(!e.defaultPrevented)sent++;}};
 const submitter={form,disabled:false,name:'decision',value:'approve'};
 function dispatch(){const e={target:form,submitter,defaultPrevented:form.blocked,preventDefault(){this.defaultPrevented=true;}};(listeners.submit||[]).slice().forEach(fn=>fn(e));return e;}
 return {form,submitter,button,dispatch,confirm(){accept.click();timers.splice(0).forEach(fn=>fn());},stats(){return {native,requested,sent,requester};}};
}
test('Xác nhận phải giữ nút đã bấm và chạy lại validation/submit handlers',()=>{const q=setup();q.dispatch();q.confirm();assert.equal(q.stats().native,0);assert.equal(q.stats().requested,1);assert.equal(q.stats().requester,q.submitter);assert.equal(q.stats().sent,1);});
test('Dữ liệu trở nên không hợp lệ khi đang xác nhận không được gửi hay khóa form',()=>{const q=setup();q.dispatch();q.form.valid=false;q.confirm();assert.equal(q.stats().sent,0);assert.equal(q.button.disabled,false);assert.notEqual(q.form.dataset.uteConfirmed,'true');});
test('Submit bị validation khác chặn thì không mở hộp xác nhận',()=>{const q=setup();q.form.blocked=true;q.dispatch();q.confirm();assert.equal(q.stats().sent,0);assert.equal(q.stats().requested,0);});
test('Xác nhận không bỏ qua validation submit khác và không khóa form khi bị chặn',()=>{const q=setup();q.dispatch();q.form.blocked=true;q.confirm();assert.equal(q.stats().sent,0);assert.equal(q.button.disabled,false);assert.notEqual(q.form.dataset.uteConfirmed,'true');});
test('Sau xác nhận chỉ gửi một lần dù submit lặp lại',()=>{const q=setup();q.dispatch();q.confirm();assert.equal(q.dispatch().defaultPrevented,true);q.confirm();assert.equal(q.stats().sent,1);});
test('Bộ chống gửi trùng được đăng ký trước hộp xác nhận không chặn lần gửi hợp lệ đầu tiên',()=>{const q=setup(true);q.dispatch();q.confirm();assert.equal(q.stats().sent,1);assert.equal(q.dispatch().defaultPrevented,true);});
