// Chỉ xem HTML fixture đã render trên test H2; không kết nối Aiven, không xử lý thanh toán.
'use strict';
const http=require('node:http'), fs=require('node:fs'), path=require('node:path');
const root=path.resolve(__dirname,'../../..');
http.createServer((request,response)=>{
    const url=new URL(request.url,'http://127.0.0.1:18082');
    let file, type;
    if(request.method!=='GET') {response.writeHead(405);return response.end('UI preview only. No payment.');}
    if(url.pathname==='/mobile') {
        response.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
        return response.end('<!doctype html><meta charset="utf-8"><title>Mobile UI fixture</title><body style="margin:0;background:#e6e7ed;display:grid;place-items:center;min-height:100vh"><iframe title="Mobile 390px fixture only" src="/preview" style="background:white;border:1px solid #ccd0db;border-radius:24px;width:390px;height:820px"></iframe>');
    }
    if(url.pathname==='/preview') {file=path.join(root,'target/momo-checkout-preview.html'); type='text/html; charset=utf-8';}
    else if(['/css/momo-simulator.css','/js/momo-simulator.js'].includes(url.pathname)) {
        file=path.join(root,'src/main/resources/static',url.pathname.slice(1)); type=url.pathname.endsWith('.css')?'text/css; charset=utf-8':'text/javascript; charset=utf-8';
    } else {response.writeHead(404);return response.end('Not found');}
    if(!fs.existsSync(file)) {response.writeHead(404);return response.end('Run MomoSimulatorIntegrationTest with -Dmomo.export-preview=true first.');}
    response.writeHead(200,{'Content-Type':type,'Cache-Control':'no-store'});
    response.end(fs.readFileSync(file));
}).listen(18082,'127.0.0.1',()=>console.log('UI fixture only: http://127.0.0.1:18082/preview'));
