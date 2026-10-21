'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/order-form.js'),'utf8');
function setup(){
 const button={disabled:false},form={dataset:{},listeners:{},querySelectorAll(){return [button];},addEventListener(type,fn){this.listeners[type]=fn;}};
 const tasks=[],events={};let ready;
 vm.runInNewContext(source,{document:{addEventListener(type,fn){ready=fn;},querySelectorAll(){return [form];}},window:{setTimeout(fn){tasks.push(fn);},addEventListener(type,fn){events[type]=fn;}}});ready();
 return {form,button,tasks,events};
}
test('Khóa gửi trùng tức thì nhưng không khóa nút trước khi browser lấy formaction',()=>{
 const {form,button,tasks}=setup();let prevented=false;
 form.listeners.submit({defaultPrevented:false,preventDefault(){prevented=true;}});assert.equal(prevented,false);assert.equal(button.disabled,false);
 form.listeners.submit({defaultPrevented:false,preventDefault(){prevented=true;}});assert.equal(prevented,true);
 tasks[0]();assert.equal(button.disabled,true);
});
test('Form bị validation chặn không khóa và trở lại từ bfcache được gửi lại',()=>{
 const {form,button,tasks,events}=setup();form.listeners.submit({defaultPrevented:true});assert.equal(form.dataset.orderSubmitting,undefined);
 form.listeners.submit({defaultPrevented:false});tasks[0]();events.pageshow({persisted:true});assert.equal(button.disabled,false);assert.equal(form.dataset.orderSubmitting,undefined);
});
