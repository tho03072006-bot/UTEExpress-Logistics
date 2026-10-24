'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/shipper-workspace.js'),'utf8');
function field(value='') {return {value,dataset:{},listeners:{},classList:{toggle(){}},addEventListener(type,fn){this.listeners[type]=fn;},setCustomValidity(message){this.error=message;},reportValidity(){},checkValidity(){return !this.error;}};}
function setup() {
 const amount=field(),note=field(),diff={},count={},total={};
 const checks=[18000,12000].map(n=>({checked:true,dataset:{amount:String(n)},closest(){return {classList:{toggle(){}}};},addEventListener(){}}));
 const parts={'[data-cod-amount]':amount,'[data-cod-note]':note,'[data-cod-diff]':diff,'[data-cod-count]':count,'[data-cod-total]':total};
 const form={dataset:{},listeners:{},querySelector(s){return parts[s];},addEventListener(type,fn){this.listeners[type]=fn;},checkValidity(){return amount.checkValidity()&&note.checkValidity();},reportValidity(){}};
 const document={getElementById(){return null;},addEventListener(){},querySelector(s){return s==='[data-sx-cod-form]'?form:null;},querySelectorAll(s){return s==='.sx-cod-check'?checks:[];}};
 vm.runInNewContext(source,{document,window:{location:{pathname:'/shipper/doi-soat'},addEventListener(){}},Intl,Number,Date,Set});
 const edit=value=>{amount.value=value;amount.listeners.input();};
 return {form,amount,note,diff,edit};
}
test('COD cho nộp thừa đúng quy trình nhưng bắt buộc ghi lý do',()=>{
 const q=setup();q.edit('31.000');assert.equal(q.amount.error,'');assert.equal(q.note.required,true);assert.match(q.diff.textContent,/Nộp thừa/);
 q.note.value='Đã đếm lại tiền nộp thừa';q.note.listeners.input();assert.equal(q.note.error,'');
});
test('COD từ chối dấu phân cách sai và số vượt 12 chữ số',()=>{
 for(const value of ['1,5','1.2.3','12 34','1..000','9999999999999','NaN','Infinity']) {const q=setup();q.edit(value);assert.ok(q.amount.error,value);}
});
test('COD chỉ nhận ghi chú có nội dung khi lệch tiền, không nhận toàn khoảng trắng',()=>{
 const q=setup();q.edit('29.000');q.note.value=' '.repeat(20);if(q.note.listeners.input)q.note.listeners.input();
 assert.ok(q.note.error);q.note.value='Đã ghi rõ khoản thiếu';q.note.listeners.input();assert.equal(q.note.error,'');
});
test('COD kiểm tra lại số tiền và ghi chú ngay trước khi gửi',()=>{
 const q=setup();q.amount.value='1,5';let prevented=false;
 assert.equal(typeof q.form.listeners.submit,'function');q.form.listeners.submit({preventDefault(){prevented=true;}});assert.equal(prevented,true);
});

test('Hẹn lại đã qua giờ khi người dùng để form mở lâu phải bị chặn lúc gửi',()=>{
 let now=new Date('2026-10-23T10:00:00').getTime();
 class TestDate extends Date { constructor(...args){super(...(args.length?args:[now]));} }
 const schedule=field('2026-10-23T10:02'),form={listeners:{},addEventListener(type,fn){this.listeners[type]=fn;}};schedule.form=form;
 const document={getElementById(){return null;},addEventListener(){},querySelector(){return null;},querySelectorAll(s){return s==='input[data-sx-schedule]'?[schedule]:[];}};
 vm.runInNewContext(source,{document,window:{location:{pathname:'/shipper/don/1'},addEventListener(){}},Intl,Number,Date:TestDate,Set});
 schedule.listeners.change();assert.equal(schedule.error,'');now+=3*60000;
 let prevented=false;form.listeners.submit({preventDefault(){prevented=true;}});assert.equal(prevented,true);assert.match(schedule.error,/sau thời điểm hiện tại/);
});
