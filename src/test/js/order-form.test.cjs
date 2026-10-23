'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/order-form.js'),'utf8');
// Trình duyệt giả: chạy MỌI hàm DOMContentLoaded (file có cả chống gửi trùng lẫn báo giá trực tiếp).
function run(form,panel,extra){
 const tasks=[],events={},ready=[];
 vm.runInNewContext(source,Object.assign({document:{addEventListener(type,fn){if(type==='DOMContentLoaded')ready.push(fn);},
  querySelectorAll(selector){return selector==='form[data-order-form]'?[form]:[];},querySelector(){return panel||null;}},
  window:{setTimeout(fn){tasks.push(fn);return tasks.length;},clearTimeout(){},addEventListener(type,fn){events[type]=fn;}},
  URLSearchParams,Intl,Number},extra||{}));
 ready.forEach(fn=>fn());
 return {tasks,events};
}
function setup(){
 const button={disabled:false},form={dataset:{},listeners:{},querySelectorAll(){return [button];},addEventListener(type,fn){this.listeners[type]=fn;}};
 const {tasks,events}=run(form,null);
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

function input(value){return {value,listeners:{},addEventListener(type,fn){this.listeners[type]=fn;}};}
function quoteSetup(response){
 const el=()=>({textContent:'',hidden:false});
 const parts={'[data-quote-status]':el(),'[data-quote-rows]':Object.assign(el(),{hidden:true}),'[data-quote-km]':el(),
  '[data-quote-base]':el(),'[data-quote-distance-fee]':el(),'[data-quote-total]':el()};
 const fields={'#senderAddress':input('Số 01 Võ Văn Ngân, Phường Thủ Đức, TP.HCM'),'#receiverAddress':input('Số nhà 112, Tân Hòa, An Định, Vĩnh Long'),
  '#weightKg':input('9'),'#serviceType':input('EXPRESS'),'input[name="_csrf"]':{value:'csrf-token'}};
 const form={dataset:{},addEventListener(){},querySelectorAll(selector){return selector==='select[data-fill-address]'?[]:[];},querySelector(selector){return fields[selector];}};
 const panel={dataset:{quoteUrl:'/nguoi-dung/tao-don/bao-gia'},closest(){return form;},querySelector(selector){return parts[selector];}};
 const calls=[];let resolveJson;
 const fetch=(url,options)=>{calls.push({url,options});return Promise.resolve({json(){return new Promise(resolve=>{resolveJson=()=>resolve(response);});}});};
 const {tasks}=run(form,panel,{fetch});
 const tick=()=>new Promise(resolve=>setImmediate(resolve));
 // response.json() chỉ được gọi sau một lượt microtask, nên chờ trước rồi mới trả dữ liệu.
 return {panel,parts,fields,calls,tasks,answer:async()=>{await tick();resolveJson();await tick();}};
}
test('Báo giá gửi hai địa chỉ kèm CSRF và hiện số km, phụ phí, tổng cước',async()=>{
 const q=quoteSetup({ok:true,distanceKm:96.5,baseFee:52000,distanceFee:58400,total:110400});
 q.tasks.at(-1)();
 assert.equal(q.calls.length,1);assert.equal(q.calls[0].options.method,'POST');
 const body=new URLSearchParams(q.calls[0].options.body.toString());
 assert.equal(body.get('senderAddress'),'Số 01 Võ Văn Ngân, Phường Thủ Đức, TP.HCM');assert.equal(body.get('_csrf'),'csrf-token');assert.equal(body.get('serviceType'),'EXPRESS');
 assert.equal(q.panel.dataset.state,'loading');
 await q.answer();
 assert.equal(q.panel.dataset.state,'ok');assert.equal(q.parts['[data-quote-rows]'].hidden,false);
 assert.match(q.parts['[data-quote-km]'].textContent,/96,5 km/);
 assert.match(q.parts['[data-quote-total]'].textContent.replace(/\s/g,' '),/110\.400 đ/);
 q.tasks.at(-1)();assert.equal(q.calls.length,1,'Không hỏi lại khi dữ liệu không đổi');
});
test('Địa chỉ không rõ: hiện thông báo của server, ẩn bảng tiền',async()=>{
 const q=quoteSetup({ok:false,message:'Không xác định được vị trí địa chỉ giao hàng.'});
 q.tasks.at(-1)();await q.answer();
 assert.equal(q.panel.dataset.state,'error');assert.equal(q.parts['[data-quote-rows]'].hidden,true);
 assert.equal(q.parts['[data-quote-status]'].textContent,'Không xác định được vị trí địa chỉ giao hàng.');
});
test('Thiếu địa chỉ hoặc khối lượng thì không gọi server',()=>{
 const q=quoteSetup({ok:true});q.fields['#receiverAddress'].value='';
 q.tasks.at(-1)();assert.equal(q.calls.length,0);assert.equal(q.panel.dataset.state,'idle');
});

test('Khối lượng ngoài 0,01–100 kg và địa chỉ có ký tự điều khiển không gọi báo giá',()=>{
 for(const weight of ['0.001','0','100.01','NaN','Infinity','']){
  const q=quoteSetup({ok:true});q.fields['#weightKg'].value=weight;
  q.tasks.at(-1)();assert.equal(q.calls.length,0);assert.equal(q.parts['[data-quote-rows]'].hidden,true);
 }
 const q=quoteSetup({ok:true});q.fields['#senderAddress'].value='Địa\nchỉ thử';
 q.tasks.at(-1)();assert.equal(q.calls.length,0);
});

test('Không hiện lại kết quả cũ khi người dùng đổi sang đầu vào chưa hợp lệ',async()=>{
 const q=quoteSetup({ok:true,distanceKm:10,baseFee:18000,distanceFee:5000,total:23000});
 q.tasks.at(-1)();assert.equal(q.calls.length,1);
 q.fields['#weightKg'].value='0.001';q.fields['#weightKg'].listeners.input();q.tasks.at(-1)();
 await q.answer();
 assert.equal(q.panel.dataset.state,'idle');assert.equal(q.parts['[data-quote-rows]'].hidden,true);
 assert.equal(q.calls.length,1);
});
