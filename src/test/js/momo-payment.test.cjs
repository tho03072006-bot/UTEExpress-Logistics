const test = require('node:test');
const assert = require('node:assert/strict');
const createPoller = require('../../main/resources/static/js/momo-payment.js');

const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function harness(fetch) {
    let time = 0, id = 0;
    const timers = new Map(), updates = [];
    const options = {url:'/api/payments/momo/12/status', expiresAt:600, fetch, now:()=>time,
        onUpdate:data=>updates.push(data), setTimeout:(fn, delay)=>{ timers.set(++id,{fn,at:time+delay}); return id; },
        clearTimeout:key=>timers.delete(key)};
    const poller = createPoller(options);
    return {poller, updates, timers, options, advance:async ms=> {
        const end = time + ms;
        while (true) {
            const next = [...timers].sort((a,b)=>a[1].at-b[1].at).find(([,x])=>x.at<=end);
            if (!next) break;
            time=next[1].at; timers.delete(next[0]); next[1].fn(); await flush();
        }
        time=end; await flush();
    }};
}
const response = (state='UNPAID')=>({ok:true,status:200,json:async()=>({state,message:state,transactionRef:state==='PAID'?'SIM-1':null})});

test('healthy checks run every 3000ms and stop on PAID',async()=>{
    let calls=0;
    const h=harness(async()=>{calls++;return response(calls===3?'PAID':'UNPAID');});
    h.poller.start(); await flush(); assert.equal(calls,1);
    await h.advance(2999); assert.equal(calls,1); await h.advance(1); assert.equal(calls,2);
    await h.advance(3000); assert.equal(calls,3); assert.equal(h.updates.at(-1).state,'PAID');
    await h.advance(60000); assert.equal(calls,3); assert.equal(h.timers.size,0);
});
test('slow fetch never overlaps checks including manual clicks',async()=>{
    let resolve,calls=0;
    const h=harness(()=>{calls++;return new Promise(r=>{resolve=r;});});
    h.poller.start(); h.poller.check(); h.poller.resume(); await h.advance(7000); assert.equal(calls,1);
    resolve(response()); await flush(); await h.advance(2999); assert.equal(calls,1);
    await h.advance(1); assert.equal(calls,2); h.poller.stop(); resolve(response()); await flush();
});
test('pause prevents queries for hidden/offline tabs and resume queries once',async()=>{
    let calls=0; const h=harness(async()=>{calls++;return response();});
    h.poller.start(); await flush(); h.poller.pause(); await h.advance(10000); assert.equal(calls,1);
    h.poller.resume(); await flush(); assert.equal(calls,2); h.poller.stop();
});
test('pause during request does not restart background timer',async()=>{
    let resolve; const h=harness(()=>new Promise(r=>{resolve=r;}));
    h.poller.start(); h.poller.pause(); resolve(response()); await flush(); assert.equal(h.timers.size,0); h.poller.stop();
});
test('network failure never reports success and retries with bounded backoff',async()=>{
    let calls=0; const h=harness(async()=>{calls++;throw new Error('offline');});
    h.poller.start(); await flush(); assert.equal(h.updates.at(-1).state,'RETRY');
    await h.advance(5999); assert.equal(calls,1); await h.advance(1); assert.equal(calls,2);
    await h.advance(12000); assert.equal(calls,3); await h.advance(15000); assert.equal(calls,4);
    assert.ok(h.updates.every(x=>x.state!=='PAID')); h.poller.stop();
});
test('401,403,404 and login redirects stop polling without data exposure',async()=>{
    for (const status of [401,403,404,302]) {
        let calls=0; const h=harness(async()=>{calls++;return {status,redirected:status===302,ok:false};});
        h.poller.start(); await flush(); assert.equal(h.updates.at(-1).state,status===404?'NOT_FOUND':'UNAUTHORIZED');
        await h.advance(60000); assert.equal(calls,1);
    }
});
test('expired QR checks database once before showing EXPIRED, allows manual final check',async()=>{
    let calls=0; const h=harness(async()=>{calls++;return response();}); h.options.expiresAt=0;
    h.poller.start(); await flush(); assert.equal(h.updates.at(-1).state,'EXPIRED'); await h.advance(10000); assert.equal(calls,1);
    h.poller.check(); await flush(); assert.equal(calls,2); h.poller.stop();
});
test('PAID returned at QR expiry wins over client expiry timer',async()=>{
    const h=harness(async()=>response('PAID')); h.options.expiresAt=0; h.poller.start(); await flush();
    assert.equal(h.updates.at(-1).state,'PAID'); assert.equal(h.timers.size,0);
});
test('timeout aborts a hanging request and only retries after completion',async()=>{
    let aborted=false; const h=harness((url,options)=>new Promise((resolve,reject)=>{
        assert.equal(options.cache,'no-store'); assert.equal(options.credentials,'same-origin');
        options.signal.addEventListener('abort',()=>{aborted=true;reject(new Error('aborted'));});
    }));
    h.poller.start(); await h.advance(8000); assert.equal(aborted,true); assert.equal(h.updates.at(-1).state,'RETRY'); h.poller.stop();
});
test('closing page aborts request and ignores a late response',async()=>{
    let resolve; const h=harness(()=>new Promise(r=>{resolve=r;})); h.poller.start(); h.poller.stop(); resolve(response('PAID')); await flush();
    assert.equal(h.updates.length,0); assert.equal(h.timers.size,0);
});
test('malformed or unexpected state is not treated as paid',async()=>{
    const h=harness(async()=>({ok:true,status:200,json:async()=>({state:'success'})}));
    h.poller.start(); await flush(); assert.equal(h.updates.at(-1).state,'RETRY'); h.poller.stop();
});
test('bfcache restart while previous aborted request completes resumes without overlap',async()=>{
    let resolve,calls=0;
    const h=harness(()=>{calls++;return new Promise(r=>{resolve=r;});});
    h.poller.start(); h.poller.stop(); h.poller.start(); assert.equal(calls,1);
    resolve(response('PAID')); await flush(); assert.equal(h.updates.length,0);
    await h.advance(0); assert.equal(calls,2); h.poller.stop(); resolve(response()); await flush();
});
