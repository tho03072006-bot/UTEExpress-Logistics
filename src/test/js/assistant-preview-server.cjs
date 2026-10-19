// Chỉ phục vụ ảnh chụp UI bằng HTML đã render từ test H2. KHÔNG dùng cho ứng dụng/demo Groq thật.
// Không đăng nhập, không kết nối database/cloud; dữ liệu AI là fixture có nhãn rõ ràng.
'use strict';
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../..');
const conversations=new Map();
http.createServer((request,response)=>{
    const url=new URL(request.url,'http://127.0.0.1:18081');
    const send=(status,body,type='application/json; charset=utf-8')=>{response.writeHead(status,{'Content-Type':type,'Cache-Control':'no-store'});response.end(typeof body==='string'?body:JSON.stringify(body));};
    const cookie=request.headers.cookie || '';
    let id=(cookie.match(/(?:^|; )preview=([a-f0-9-]+)/)||[])[1];
    if(!id) {id=crypto.randomUUID();response.setHeader('Set-Cookie','preview='+id+'; SameSite=Strict; HttpOnly');}
    const fresh=role=>({conversationId:crypto.randomUUID(),role,configured:true,suggestions:role==='SHIPPER'?['Công việc hôm nay?','Bằng chứng giao hàng cần gì?']:['Cách đóng gói hàng dễ vỡ?','Ước tính cước cho kiện 2 kg'],turns:[]});
    if(url.pathname==='/preview-user' || url.pathname==='/preview-shipper') {
        const role=url.pathname.endsWith('shipper')?'SHIPPER':'USER'; conversations.set(id,fresh(role));
        const file=path.join(root,'target/assistant-'+role.toLowerCase()+'-preview.html');
        if(!fs.existsSync(file)) return send(404,'Chạy AssistantIntegrationTest với -Dassistant.export-preview=true trước.','text/plain; charset=utf-8');
        return send(200,fs.readFileSync(file,'utf8').replace('<body ','<body data-ui-test="fixture" ')
            .replace('</body>','<div style="position:fixed;left:8px;bottom:6px;z-index:1100;background:#fff1ba;color:#4a3500;padding:5px;font-size:11px">UI TEST · H2 HTML · Groq mock · Không có dữ liệu thật</div></body>'),'text/html; charset=utf-8');
    }
    if(url.pathname.startsWith('/api/assistant/')) {
        if(!conversations.has(id)) conversations.set(id,fresh('USER'));
        let state=conversations.get(id);
        if(request.method==='DELETE') {state=fresh(state.role);conversations.set(id,state);return send(200,state);}
        if(request.method==='GET') return send(200,state);
        let input=''; request.on('data',chunk=>{input+=chunk;if(input.length>16384) request.destroy();});
        request.on('end',()=>{
            let body; try {body=JSON.parse(input);} catch {return send(400,{code:'INVALID_INPUT'});}
            const previous=state.turns.find(turn=>turn.requestId===body.requestId);
            const turn=previous || {requestId:body.requestId,question:body.question,links:[]};
            if(!previous) state.turns.push(turn);
            setTimeout(()=>{
                if(!body.retry) {turn.status='FAILED';turn.answer=null;turn.error={code:'TEST_TIMEOUT',message:'[UI TEST] Mô phỏng lỗi AI. Câu hỏi vẫn được giữ; hãy thử lại.',retryAfterSeconds:3};send(503,state);}
                else {turn.status='COMPLETE';turn.error=null;turn.answer='[UI TEST / MOCK] Đây là dữ liệu kiểm thử hiển thị, không phải trả lời Groq thật.\n<img src=x onerror=alert(1)> phải hiện dưới dạng chữ.\nKhông thực hiện thay đổi đơn hàng.';turn.checkedAt=new Date().toISOString();send(200,state);}
            },1000);
        }); return;
    }
    if(/^\/(css|js)\/[a-zA-Z0-9._-]+$/.test(url.pathname)) {
        const file=path.join(root,'src/main/resources/static',url.pathname.slice(1));
        if(fs.existsSync(file)) return send(200,fs.readFileSync(file,'utf8'),url.pathname.endsWith('.css')?'text/css; charset=utf-8':'text/javascript; charset=utf-8');
    }
    send(404,'Not found','text/plain');
}).listen(18081,'127.0.0.1',()=>console.log('UI fixture only: http://127.0.0.1:18081/preview-user or /preview-shipper'));
