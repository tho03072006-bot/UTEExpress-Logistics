const test=require('node:test');
const assert=require('node:assert/strict');
const {AssistantClient,safeSource}=require('../../main/resources/static/js/assistant-chat.js');
const state=(turns=[])=>({conversationId:'conversation-A',role:'USER',configured:true,suggestions:[],turns});
test('Retry dùng cùng ID và không thêm trùng tin nhắn',async()=>{
    let now=1000; const posted=[];
    const transport=async(method,path,body)=>{
        if(method==='GET') return {status:200,body:state()};
        posted.push(body);
        const error=posted.length===1;
        return {status:error?503:200,body:state([{requestId:body.requestId,question:body.question,status:error?'FAILED':'COMPLETE',answer:error?null:'OK',error:error?{message:'Lỗi',retryAfterSeconds:5}:null}])};
    };
    const client=new AssistantClient(transport,null,()=>now,()=> 'request-1');
    await client.load(); await client.send('Hỏi tự do');
    assert.equal(client.turns().length,1); assert.equal(await client.retry('request-1'),false);
    now=7000; await client.retry('request-1');
    assert.equal(client.turns().length,1); assert.equal(posted[0].requestId,posted[1].requestId); assert.equal(posted[1].retry,true);
});
test('Không gửi đồng thời khi người dùng bấm liên tục',async()=>{
    let finish; let calls=0;
    const client=new AssistantClient(async(method,path,body)=>{
        if(method==='GET') return {status:200,body:state()}; calls++;
        return new Promise(resolve=>finish=()=>resolve({status:200,body:state([{requestId:body.requestId,question:body.question,status:'COMPLETE',answer:'OK'}])}));
    },null,Date.now,()=> 'request-1');
    await client.load(); const pending=client.send('Một câu');
    assert.equal(await client.send('Hai câu'),false); assert.equal(await client.retry('request-1'),false);
    finish(); await pending; assert.equal(calls,1);
});
test('Retry thay thế lượt FAILED bằng PROCESSING ngay lập tức, không tạo bản sao',async()=>{
    let finish;
    const saved={requestId:'r1',question:'Câu cần thử lại',status:'FAILED',error:{message:'Lỗi',retryAfterSeconds:0}};
    const client=new AssistantClient(async(method,path,body)=>method==='GET'?{status:200,body:state([saved])}:
        new Promise(resolve=>finish=()=>resolve({status:200,body:state([{requestId:body.requestId,question:body.question,status:'COMPLETE',answer:'OK'}])})),null,()=>10000);
    await client.load(); const waiting=client.retry('r1');
    assert.equal(client.turns().length,1); assert.equal(client.turns()[0].status,'PROCESSING');
    finish(); await waiting; assert.equal(client.turns().length,1); assert.equal(client.turns()[0].status,'COMPLETE');
});
test('Kiểm tra phiên vẫn giữ câu bị lỗi mạng chưa tới server, nhưng đổi hội thoại thì xóa',async()=>{
    let current=state();
    const client=new AssistantClient(async(method)=>{if(method==='GET') return {status:200,body:current};throw new Error('network');},null,()=>10000,()=> 'r1');
    await client.load(); await client.send('Chưa tới server'); await client.load();
    assert.equal(client.turns().length,1); assert.equal(client.turns()[0].question,'Chưa tới server');
    current={...state(),conversationId:'conversation-B'}; await client.load(); assert.equal(client.turns().length,0);
});
test('Không hiển thị lịch sử cũ khi kiểm tra lại phiên bị mất mạng',async()=>{
    let offline=false;
    const client=new AssistantClient(async()=>{if(offline) throw new Error('offline');return {status:200,body:state([{requestId:'r1',question:'Riêng tư',status:'COMPLETE',answer:'PRIVATE'}])};});
    await client.load(); assert.equal(client.sessionVerified,true);
    offline=true; await client.load(); assert.equal(client.sessionVerified,false); assert.equal(await client.send('Không gửi khi chưa kiểm tra phiên'),false);
    offline=false; await client.load(); assert.equal(client.sessionVerified,true); assert.equal(client.turns().length,1);
});
test('Lỗi mạng giữ nguyên câu hỏi, thử lại cùng mã kể cả chưa có ở server',async()=>{
    let now=0; let attempts=0; const ids=[];
    const client=new AssistantClient(async(method,path,body)=>{
        if(method==='GET') return {status:200,body:state()}; ids.push(body.requestId); attempts++;
        if(attempts===1) throw new Error('network');
        return {status:200,body:state([{requestId:body.requestId,question:body.question,status:'COMPLETE',answer:'OK'}])};
    },null,()=>now,()=> 'retry-network');
    await client.load(); await client.send('Xin giữ câu này'); assert.equal(client.turns()[0].question,'Xin giữ câu này');
    now=6000; await client.retry('retry-network'); assert.deepEqual(ids,['retry-network','retry-network']); assert.equal(client.turns().length,1);
});
test('Logout hoặc đổi tài khoản bỏ qua phản hồi cũ đến muộn',async()=>{
    let finish;
    const client=new AssistantClient(async(method,path,body)=> method==='GET' ? {status:200,body:state()} : new Promise(resolve=>finish=()=>resolve({status:200,body:state([{requestId:body.requestId,question:'private',answer:'PRIVATE',status:'COMPLETE'}])})),null,Date.now,()=> 'r1');
    await client.load(); const pending=client.send('private'); client.discard(); finish(); await pending;
    assert.equal(client.conversation,null); assert.equal(client.turns().length,0);
});
test('401 xóa hội thoại; không dùng lịch sử browser storage',async()=>{
    const client=new AssistantClient(async()=>({status:401,body:{}})); await client.load();
    assert.equal(client.conversation,null); assert.match(client.notice,/đăng nhập/);
});
test('Nguồn chỉ nhận đường dẫn nội bộ; nội dung HTML được giữ dưới dạng text',()=>{
    for(const url of ['javascript:alert(1)','//evil.invalid','/shipper/../../admin','/shipper/abc?x=<script>','https://evil.invalid']) assert.equal(safeSource(url),false);
    assert.equal(safeSource('/shipper/don/1'),true); assert.equal(safeSource('/uoc-tinh-cuoc'),true);
    const source=require('node:fs').readFileSync(require('node:path').join(__dirname,'../../main/resources/static/js/assistant-chat.js'),'utf8');
    assert.doesNotMatch(source,/\.innerHTML\s*=|insertAdjacentHTML|localStorage|sessionStorage/);
    assert.match(source,/node\.textContent=/);
});
