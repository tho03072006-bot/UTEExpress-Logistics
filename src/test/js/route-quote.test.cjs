'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/route-quote.js'),'utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(){
 const timers=new Map(),calls=[],events={};let next=0,mode='COURIER';
 const fields={pickupProvinceCode:{value:'79'},pickupAreaCode:{value:'SGN-THUDUC'},deliveryProvinceCode:{value:'48'},deliveryAreaCode:{value:'DAD-HAICHAU'},weightKg:{value:'1',addEventListener(type,fn){this[type]=fn;}}};
 const service={value:'STANDARD',options:['STANDARD','EXPRESS','SUPER_EXPRESS'].map(value=>({value}))};
 Object.defineProperty(service,'selectedOptions',{get(){return service.options.filter(option=>option.value===service.value);}});
 fields.serviceType=service;
 const output={textContent:''},cart={disabled:false},empty={hidden:true},hub={dataset:{provinceCode:'79'},hidden:true};
 const hubs={hidden:true,querySelectorAll(){return [hub];},querySelector(){return empty;}};
 const form={dataset:{},elements:{namedItem(name){return fields[name];}},addEventListener(type,fn){events[type]=fn;},
  querySelector(selector){if(selector==='[data-route-quote-output]')return output;if(selector==='[data-cart-button]')return cart;if(selector==='[data-drop-off-hubs]')return hubs;return {value:mode};}};
 vm.runInNewContext(source,{document:{querySelectorAll(){return [form];}},window:{addEventListener(type,fn){events[type]=fn;}},
  clearTimeout(id){timers.delete(id);},setTimeout(fn){timers.set(++next,fn);return next;},AbortController,URLSearchParams,Intl,Number,
  fetch(url,options){return new Promise((resolve,reject)=>calls.push({url,options,resolve,reject}));}});
 return {fields,service,form,output,cart,hubs,hub,empty,calls,events,
  mode(value){mode=value;},run(){for(const [id,fn] of timers){timers.delete(id);fn();}},
  async answer(index,data,ok=true){calls[index].resolve({ok,async json(){return data;}});await tick();}};
}
function quote(total=43000){return {serviceType:'STANDARD',baseFee:18000,distanceFee:0,routeFee:25000,dropOffDiscount:0,total,leadTimeText:'2 ngày',pickupMode:'COURIER'};}
test('Báo giá tuyến gửi đủ địa bàn, không gửi tổng tiền tự nhập và không cache',async()=>{
 const q=setup();q.run();assert.equal(q.calls.length,1);
 const url=new URL(q.calls[0].url,'https://test.invalid');assert.equal(url.searchParams.get('pickupAreaCode'),'SGN-THUDUC');
 assert.equal(url.searchParams.get('deliveryAreaCode'),'DAD-HAICHAU');assert.equal(url.searchParams.has('total'),false);
 assert.equal(q.calls[0].options.cache,'no-store');await q.answer(0,[quote()]);
 assert.match(q.output.textContent,/43\.000 đ/);assert.match(q.output.textContent,/tuyến/);
 assert.equal(q.service.options[2].disabled,true);
});
test('Đầu vào thiếu hoặc cân nặng không hữu hạn/ngoài giới hạn không gọi server',()=>{
 for(const value of ['', '0','0.001','100.01','NaN','Infinity']){
  const q=setup();q.fields.weightKg.value=value;q.events.change();q.run();assert.equal(q.calls.length,0);
 }
 const q=setup();q.fields.pickupAreaCode.value='';q.events.change();q.run();assert.equal(q.calls.length,0);
});
test('Đổi sang dữ liệu chưa hợp lệ bỏ qua phản hồi cũ dù mạng không hủy được',async()=>{
 const q=setup();q.run();q.fields.weightKg.value='0.001';q.fields.weightKg.input();
 assert.equal(q.calls[0].options.signal.aborted,true);await q.answer(0,[quote()]);
 assert.doesNotMatch(q.output.textContent,/43\.000/);q.run();assert.equal(q.calls.length,1);
});
test('Hai phản hồi ngược thứ tự chỉ hiện báo giá của lần chọn mới nhất',async()=>{
 const q=setup();q.run();q.fields.weightKg.value='2';q.events.change();q.run();
 await q.answer(1,[quote(99000)]);await q.answer(0,[quote(43000)]);
 assert.match(q.output.textContent,/99\.000/);assert.doesNotMatch(q.output.textContent,/43\.000/);
});
test('Không dùng Hỏa tốc khi tuyến không hỗ trợ, tự chuyển về dịch vụ phù hợp',async()=>{
 const q=setup();q.service.value='SUPER_EXPRESS';q.run();await q.answer(0,[quote()]);
 assert.equal(q.service.value,'STANDARD');assert.equal(q.service.options[2].hidden,true);
});
test('Phản hồi lỗi hoặc sai cấu trúc không được hiện thành báo giá thành công',async()=>{
 for(const payload of [null,{},[],[{...quote(),total:-1}],[{...quote(),total:null}]]){
  const q=setup();q.run();await q.answer(0,payload);assert.match(q.output.textContent,/không hợp lệ/);
 }
 const q=setup();q.run();await q.answer(0,null,false);assert.match(q.output.textContent,/Không tính được cước/);
});
test('Đơn tại quầy khóa thêm giỏ, hiện đúng kho và vẫn khóa sau quay lại từ bộ nhớ trang',()=>{
 const q=setup();q.mode('DROP_OFF');q.events.change();assert.equal(q.cart.disabled,true);assert.equal(q.hubs.hidden,false);assert.equal(q.hub.hidden,false);
 q.cart.disabled=false;q.events.pageshow({persisted:true});assert.equal(q.cart.disabled,true);
 q.mode('COURIER');q.events.change();assert.equal(q.cart.disabled,false);assert.equal(q.hubs.hidden,true);
 q.form.dataset.orderSubmitting='true';q.events.change();assert.equal(q.cart.disabled,true);
});
