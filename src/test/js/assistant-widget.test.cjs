'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

// DOM giả nhỏ để chạy render và sự kiện thật của widget, không phải câu trả lời AI trong ứng dụng.
class Node {
    constructor(tag='div'){this.tag=tag;this.children=[];this.listeners={};this.dataset={};this.attrs={};this.hidden=false;this.disabled=false;this.value='';this.scrollTop=0;this.scrollLeft=0;this.classList={add(){},remove(){}};}
    append(...nodes){this.children.push(...nodes);}
    replaceChildren(...nodes){this.children=nodes;}
    setAttribute(key,value){this.attrs[key]=value;}
    addEventListener(type,callback){this.listeners[type]=callback;}
    click(){if(!this.disabled && this.listeners.click)this.listeners.click({preventDefault(){}});}
    focus(){}
    querySelectorAll(){return [];}
}
async function setup(){
    const ids=['uteAssistant','uteAssistantPanel','uteAssistantLauncher','uteAssistantMessages','uteAssistantNotice','uteAssistantForm','uteAssistantQuestion','uteAssistantSend','uteAssistantClear','uteAssistantSuggestions','uteAssistantCount','uteAssistantOrderPicker','uteAssistantOrders','uteAssistantOrdersNotice','uteAssistantSelectedOrder','uteAssistantChooseOrder','uteAssistantUnselectOrder','uteAssistantPreviousOrders','uteAssistantNextOrders','uteAssistantRefreshOrders','uteAssistantOrderPage','uteAssistantOrderHeading'];
    const nodes=Object.fromEntries(ids.map(id=>[id,new Node()])); nodes.uteAssistantPanel.hidden=true; nodes.uteAssistantOrderPicker.hidden=true;
    nodes.uteAssistant.dataset={endpoint:'/api/assistant',context:'/',csrfHeader:'X-CSRF-TOKEN',csrfToken:'test'};
    nodes.uteAssistant.querySelector=()=>new Node();
    let nextId=0; const posted=[];
    const conversation={conversationId:'c1',role:'USER',configured:true,suggestions:['Cách đóng gói hàng dễ vỡ?','Đơn của tôi đang ở đâu?'],turns:[],selectedOrderCode:null};
    const fetch=async(url,options)=>{
        const clone=value=>JSON.parse(JSON.stringify(value));
        if(url.includes('/orders'))return {status:200,json:async()=>({conversationId:'c1',role:'USER',page:0,total:2,hasNext:false,orders:[{trackingCode:'UTEOWN01',status:'Chờ lấy'},{trackingCode:'UTEOWN02',status:'Đang giao'}]})};
        let status=200;
        if(options.method==='DELETE'){conversation.turns=[];conversation.selectedOrderCode=null;}
        if(options.method==='POST'){
            const body=JSON.parse(options.body);posted.push(body);let turn=conversation.turns.find(t=>t.requestId===body.requestId);
            if(!turn){turn={requestId:body.requestId,question:body.question};conversation.turns.push(turn);}
            if(body.question.includes('đang ở đâu') && !body.selectedOrderCode){turn.status='NEEDS_ORDER';turn.error={code:'ORDER_SELECTION_REQUIRED',message:'Chọn đơn của bạn'};status=409;}
            else {turn.status='COMPLETE';turn.error=null;turn.answer='<img src=x onerror=alert(1)> là chuỗi văn bản';turn.links=[];conversation.selectedOrderCode=body.selectedOrderCode;}
        }
        const body=clone(conversation);return {status,json:async()=>body};
    };
    const document={getElementById:id=>nodes[id],createElement:tag=>new Node(tag),body:new Node(),addEventListener(){},hidden:false};
    const window={matchMedia:()=>({matches:true}),addEventListener(){},setInterval:()=>1,clearInterval(){},confirm:()=>true,
        setTimeout:(fn,ms)=>ms===0?setTimeout(fn,0):1,clearTimeout(){}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/assistant-chat.js'),'utf8'),
        {document,window,fetch,crypto:{randomUUID:()=> 'r'+(++nextId)},AbortController,URL,location:{href:'http://example.invalid'},console});
    const flush=async()=>{for(let i=0;i<6;i++)await new Promise(resolve=>setImmediate(resolve));await new Promise(resolve=>setTimeout(resolve,2));};
    nodes.uteAssistantLauncher.click();await flush();
    return {nodes,posted,flush};
}
test('Gợi ý nằm trên ô nhập, luôn còn sau trả lời/xóa và bấm là gửi ngay',async()=>{
    const {nodes,posted,flush}=await setup();
    assert.equal(nodes.uteAssistantSuggestions.children.length,2);
    nodes.uteAssistantSuggestions.children[0].click();await flush();
    assert.equal(posted.length,1);assert.equal(posted[0].question,'Cách đóng gói hàng dễ vỡ?');
    assert.equal(nodes.uteAssistantSuggestions.children.length,2);
    assert.equal(nodes.uteAssistantMessages.children[0].children[1].textContent,'<img src=x onerror=alert(1)> là chuỗi văn bản');
    nodes.uteAssistantClear.click();await flush();assert.equal(nodes.uteAssistantSuggestions.children.length,2);
    const template=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/fragments/assistant.html'),'utf8');
    assert.ok(template.indexOf('id="uteAssistantSuggestions"')<template.indexOf('id="uteAssistantQuestion"'));
    const css=fs.readFileSync(path.join(__dirname,'../../main/resources/static/css/assistant-chat.css'),'utf8');
    assert.match(css,/flex-wrap:nowrap/);assert.match(css,/overflow-x:auto/);
});
test('Câu hỏi cần đơn mở bộ chọn; chọn đơn tiếp tục đúng UUID và không mất gợi ý',async()=>{
    const {nodes,posted,flush}=await setup();
    nodes.uteAssistantSuggestions.children[1].click();await flush();await flush();
    assert.equal(nodes.uteAssistantOrderPicker.hidden,false);assert.equal(nodes.uteAssistantOrders.children.length,2);
    nodes.uteAssistantOrders.children[0].click();await flush();
    assert.equal(posted.length,2);assert.equal(posted[0].requestId,posted[1].requestId);assert.equal(posted[1].selectedOrderCode,'UTEOWN01');
    assert.equal(nodes.uteAssistantMessages.children.length,1);assert.equal(nodes.uteAssistantSuggestions.children.length,2);
});
