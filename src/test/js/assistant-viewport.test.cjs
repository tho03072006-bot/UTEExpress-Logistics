'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/assistant-chat.js'),'utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(wide=false) {
 const nodes=new Map(),classes=new Set(),intervals=new Set(),requests=[];let serial=0;
 function element(id='') {
  return {id,hidden:id==='uteAssistantPanel',value:'',dataset:{},children:[],listeners:{},scrollLeft:0,scrollTop:0,scrollHeight:0,
   addEventListener(name,fn){(this.listeners[name]??=[]).push(fn);},setAttribute(name,value){this[name]=value;},focus(){},
   append(...items){this.children.push(...items);},replaceChildren(...items){this.children=[...items];},querySelectorAll(){return [];}};
 }
 const get=id=>{if(!nodes.has(id))nodes.set(id,element(id));return nodes.get(id);};
 const root=get('uteAssistant');root.dataset={endpoint:'/api/assistant',csrfHeader:'X-CSRF-TOKEN',csrfToken:'fixture'};
 root.querySelector=selector=>get(selector);
 const media={matches:wide,listeners:[],addEventListener(name,fn){this.listeners.push(fn);}};
 const document={getElementById:get,createElement(){return element();},addEventListener(){},body:{classList:{add(v){classes.add(v);},remove(v){classes.delete(v);}}}};
 const window={matchMedia(){return media;},addEventListener(){},setTimeout(){return ++serial;},clearTimeout(){},
  setInterval(){const id=++serial;intervals.add(id);return id;},clearInterval(id){intervals.delete(id);}};
 const conversation={conversationId:'viewport-conversation',role:'SHIPPER',turns:[],suggestions:['Tra cứu đơn của tôi']};
 vm.runInNewContext(source,{document,window,AbortController,URL,Date,fetch:async(url)=>{
  requests.push(url);return {status:200,redirected:false,json:async()=>url.includes('/orders')?{...conversation,orders:[],page:0,hasNext:false}:conversation};
 }});
 return {get,classes,intervals,requests,resize(value){media.matches=value;media.listeners.forEach(fn=>fn({matches:value}));},
  async open(){for(const fn of get('uteAssistantLauncher').listeners.click||[])fn();await tick();await tick();}};
}
test('Mở ở cửa sổ hẹp rồi chuyển sang desktop vẫn mở được chatbot',async()=>{
 const q=setup(false);assert.equal(q.intervals.size,0);q.resize(true);await q.open();
 assert.equal(q.get('uteAssistantPanel').hidden,false);assert.equal(q.get('uteAssistantLauncher')['aria-expanded'],'true');assert.ok(q.requests.some(p=>p.endsWith('/conversation')));
});
test('Thu cửa sổ dưới ngưỡng desktop đóng khung chat, xóa trạng thái riêng và dừng timer',async()=>{
 const q=setup(true);await q.open();q.get('uteAssistantQuestion').value='Câu hỏi riêng';q.resize(false);
 assert.equal(q.get('uteAssistantPanel').hidden,true);assert.equal(q.get('uteAssistantQuestion').value,'');assert.equal(q.classes.has('ute-assistant-open'),false);assert.equal(q.intervals.size,0);
});
test('Đổi kích thước nhiều lần không nhân đôi handler hoặc timer',async()=>{
 const q=setup(false);for(let i=0;i<3;i++){q.resize(true);await q.open();assert.equal(q.intervals.size,1);q.resize(false);}
 assert.equal(q.get('uteAssistantLauncher').listeners.click.length,1);assert.equal(q.intervals.size,0);
});
test('Mở chat dùng chiều rộng nội dung thực để xuống cột thay vì ép chữ và nút thành cột hẹp',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../../main/resources/static/css/assistant-chat.css'),'utf8');
 assert.match(css,/container-name:ute-assistant-workspace/);assert.match(css,/@container ute-assistant-workspace \(max-width:900px\)/);
 assert.match(css,/\.sx-hello-grid,\.sx-cod-grid\s*\{\s*grid-template-columns:minmax\(0,1fr\)/);
 assert.match(css,/\.sx-cell-actions\s*\{\s*grid-area:actions/);
});
