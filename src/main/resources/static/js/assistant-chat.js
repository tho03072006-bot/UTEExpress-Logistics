(function () {
    'use strict';
    const GENERAL_ERROR = 'Trợ lý AI tạm thời chưa phản hồi. Vui lòng thử lại.';
    class AssistantClient {
        constructor(transport, notify, now, uuid) {
            this.transport=transport; this.notify=notify || function () {}; this.now=now || Date.now;
            this.uuid=uuid || function () { return crypto.randomUUID(); };
            this.conversation=null; this.pending=false; this.localTurn=null; this.notice=''; this.generation=0; this.sessionVerified=false;
            this.selectedOrderCode=null; this.orderPage=null; this.ordersPending=false; this.ordersNotice=''; this.orderGeneration=0;
        }
        emit() { this.notify(this); }
        discard() { this.generation++; this.orderGeneration++; this.conversation=null; this.localTurn=null; this.pending=false; this.notice=''; this.sessionVerified=false;
            this.selectedOrderCode=null; this.orderPage=null; this.ordersPending=false; this.ordersNotice=''; this.emit(); }
        apply(body) {
            if (!body || typeof body.conversationId!=='string' || !Array.isArray(body.turns)) throw new Error('INVALID_RESPONSE');
            if (!this.conversation || this.conversation.conversationId!==body.conversationId) { this.orderPage=null; this.orderGeneration++; this.ordersPending=false; }
            this.conversation=body; this.localTurn=null; this.sessionVerified=true; this.selectedOrderCode=body.selectedOrderCode || null;
            body.turns.forEach(turn => { if (turn.error) turn.retryAt=this.now()+Math.max(0,Number(turn.error.retryAfterSeconds)||0)*1000; });
        }
        expired(response) {
            if (response.status===401 || response.status===403) {
                this.discard(); this.notice='Phiên hoặc quyền truy cập đã thay đổi. Vui lòng đăng nhập lại.'; this.emit(); return true;
            }
            return false;
        }
        async load() {
            if (this.pending) return;
            const previousLocal=this.localTurn;
            this.orderGeneration++; this.ordersPending=false; this.orderPage=null;
            const generation=++this.generation; this.pending=true; this.sessionVerified=false; this.notice='Đang kiểm tra phiên đăng nhập…'; this.emit();
            try {
                const response=await this.transport('GET','/conversation');
                if (generation!==this.generation || this.expired(response)) return;
                if (response.status!==200) throw new Error('UNAVAILABLE');
                const sameConversation=this.conversation && this.conversation.conversationId===response.body.conversationId;
                this.apply(response.body);
                // GET kiểm tra phiên không làm mất câu hỏi bị lỗi mạng chưa tới server.
                if (sameConversation && previousLocal && !this.conversation.turns.some(turn => turn.requestId===previousLocal.requestId)) this.localTurn=previousLocal;
                this.notice=this.conversation.configured ? '' : 'Trợ lý AI chưa được cấu hình. Vui lòng liên hệ quản trị.';
            } catch (error) { if (generation===this.generation) this.notice=GENERAL_ERROR; }
            finally { if (generation===this.generation) { this.pending=false; this.emit(); } }
        }
        turns() {
            const saved=this.conversation ? this.conversation.turns : [];
            if (!this.localTurn) return saved;
            return saved.some(turn => turn.requestId===this.localTurn.requestId)
                ? saved.map(turn => turn.requestId===this.localTurn.requestId ? this.localTurn : turn) : saved.concat(this.localTurn);
        }
        async loadOrders(page) {
            if (this.pending || this.ordersPending || !this.sessionVerified || !this.conversation) return false;
            if (!Number.isInteger(page) || page<0 || page>1000) return false;
            const generation=this.generation, orderGeneration=++this.orderGeneration, conversationId=this.conversation.conversationId;
            this.ordersPending=true; this.ordersNotice='Đang tải đơn theo tài khoản…'; this.emit();
            try {
                const response=await this.transport('GET','/orders?page='+page);
                if(generation!==this.generation || orderGeneration!==this.orderGeneration || this.expired(response)) return false;
                const body=response.body;
                if(response.status!==200 || !body || body.conversationId!==conversationId || body.role!==this.conversation.role || !Array.isArray(body.orders)) {
                    this.orderPage=null; throw new Error('INVALID_RESPONSE');
                }
                this.orderPage=body; this.ordersNotice=body.total ? body.total+' đơn trong phạm vi tài khoản.' : 'Tài khoản hiện tại chưa có đơn được phép xem.';
                return true;
            } catch(error) { if(generation===this.generation && orderGeneration===this.orderGeneration) { this.orderPage=null; this.ordersNotice='Chưa tải được danh sách. Nhấn Tải lại; bạn vẫn có thể hỏi câu chung.'; } return false; }
            finally { if(generation===this.generation && orderGeneration===this.orderGeneration) { this.ordersPending=false; this.emit(); } }
        }
        async selectOrder(code) {
            if(this.pending || this.ordersPending || !this.sessionVerified) return false;
            if(code!==null && (!this.orderPage || this.orderPage.conversationId!==this.conversation.conversationId || !this.orderPage.orders.some(order=>order.trackingCode===code))) return false;
            this.selectedOrderCode=code; this.notice=code ? 'Đã chọn '+code+'. Có thể bấm gợi ý hoặc nhập câu hỏi.' : 'Đã bỏ chọn đơn. Câu hỏi chung không cần chọn đơn.'; this.emit();
            const turns=this.turns(), last=turns[turns.length-1];
            if(code && last && last.status==='NEEDS_ORDER') return this.post({requestId:last.requestId,question:last.question,retry:false,selectedOrderCode:code});
            return true;
        }
        async send(question) {
            if (this.pending || !this.conversation || !this.sessionVerified) return false;
            question=String(question || '').trim();
            if (!question || question.length>1200 || /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(question)) {
                this.notice='Nhập câu hỏi hợp lệ, tối đa 1200 ký tự.'; this.emit(); return false;
            }
            if(this.turns().some(turn=>turn.status==='PROCESSING')) return false;
            return this.post({requestId:this.uuid(),question:question,retry:false,selectedOrderCode:this.selectedOrderCode});
        }
        async retry(requestId) {
            if (this.pending || !this.conversation || !this.sessionVerified) return false;
            const turns=this.turns(); const turn=turns[turns.length-1];
            if (!turn || turn.requestId!==requestId || turn.status!=='FAILED' || (turn.retryAt || 0)>this.now()) return false;
            return this.post({requestId:turn.requestId,question:turn.question,retry:true,selectedOrderCode:turn.selectedOrderCode || null});
        }
        async post(request) {
            this.pending=true; this.notice='Đang trả lời…';
            const generation=this.generation;
            this.localTurn={requestId:request.requestId,question:request.question,selectedOrderCode:request.selectedOrderCode,status:'PROCESSING',links:[]}; this.emit();
            try {
                const response=await this.transport('POST','/messages',Object.assign({conversationId:this.conversation.conversationId},request));
                if (generation!==this.generation || this.expired(response)) return false;
                if (response.body && response.body.conversationId) { this.apply(response.body); this.notice=''; return response.status===200; }
                const failure=response.body || {};
                this.localTurn.status='FAILED'; this.localTurn.error={code:failure.code || 'UNAVAILABLE',message:failure.message || GENERAL_ERROR};
                this.localTurn.retryAt=this.now()+Math.max(3,Number(failure.retryAfterSeconds)||5)*1000;
                this.notice=''; return false;
            } catch (error) {
                if (generation===this.generation) {
                    this.localTurn.status='FAILED'; this.localTurn.error={code:'NETWORK',message:GENERAL_ERROR}; this.localTurn.retryAt=this.now()+5000; this.notice='';
                }
                return false;
            } finally { if (generation===this.generation) { this.pending=false; this.emit(); } }
        }
        async clear() {
            if (this.pending || !this.sessionVerified) return false;
            const generation=++this.generation; this.pending=true; this.emit();
            try {
                const response=await this.transport('DELETE','/conversation');
                if (generation!==this.generation || this.expired(response)) return false;
                if (response.status!==200) throw new Error('UNAVAILABLE');
                this.apply(response.body); this.notice='Đã xóa hội thoại.'; return true;
            } catch (error) { if (generation===this.generation) this.notice='Chưa xóa được hội thoại. Vui lòng thử lại.'; return false; }
            finally { if (generation===this.generation) { this.pending=false; this.emit(); } }
        }
    }
    function safeSource(path) {
        return typeof path==='string' && (/^\/(nguoi-dung|shipper)\/[a-z0-9/-]*$/.test(path) || path==='/uoc-tinh-cuoc');
    }
    if (typeof module!=='undefined' && module.exports) module.exports={AssistantClient:AssistantClient,safeSource:safeSource};
    if (typeof document==='undefined') return;
    const root=document.getElementById('uteAssistant');
    if (!root || !window.matchMedia('(min-width: 1280px)').matches) return;
    const panel=document.getElementById('uteAssistantPanel'), launcher=document.getElementById('uteAssistantLauncher');
    const messages=document.getElementById('uteAssistantMessages'), notice=document.getElementById('uteAssistantNotice');
    const form=document.getElementById('uteAssistantForm'), question=document.getElementById('uteAssistantQuestion');
    const send=document.getElementById('uteAssistantSend'), clear=document.getElementById('uteAssistantClear');
    const suggestions=document.getElementById('uteAssistantSuggestions'), count=document.getElementById('uteAssistantCount');
    const orderPicker=document.getElementById('uteAssistantOrderPicker'), orders=document.getElementById('uteAssistantOrders');
    const ordersNotice=document.getElementById('uteAssistantOrdersNotice'), selectedOrder=document.getElementById('uteAssistantSelectedOrder');
    const chooseOrder=document.getElementById('uteAssistantChooseOrder'), unselectOrder=document.getElementById('uteAssistantUnselectOrder');
    const previousOrders=document.getElementById('uteAssistantPreviousOrders'), nextOrders=document.getElementById('uteAssistantNextOrders');
    const refreshOrders=document.getElementById('uteAssistantRefreshOrders'), orderPageLabel=document.getElementById('uteAssistantOrderPage');
    let pollTimer=null, pollCount=0;
    let countdown=null;
    let selectionPromptId=null;
    async function transport(method,path,body) {
        const abort=new AbortController(); const timeout=window.setTimeout(function () { abort.abort(); },60000);
        try {
            const headers={Accept:'application/json'};
            if (method!=='GET') { headers['Content-Type']='application/json'; headers[root.dataset.csrfHeader]=root.dataset.csrfToken; }
            const response=await fetch(root.dataset.endpoint+path,{method:method,headers:headers,body:body ? JSON.stringify(body) : undefined,
                credentials:'same-origin',cache:'no-store',signal:abort.signal});
            const data=await response.json().catch(function () { return {}; });
            return {status:response.redirected ? 401 : response.status,body:data};
        } finally { window.clearTimeout(timeout); }
    }
    function element(tag,className,text) {
        const node=document.createElement(tag); if (className) node.className=className;
        if (text!==undefined) node.textContent=String(text); return node;
    }
    const client=new AssistantClient(transport,render);
    function render() {
        const suggestionScroll=suggestions.scrollLeft, ordersScroll=orders.scrollTop;
        messages.replaceChildren(); suggestions.replaceChildren();
        notice.textContent=client.notice;
        const turns=client.sessionVerified ? client.turns() : []; const last=turns[turns.length-1];
        const processing=turns.some(turn => turn.status==='PROCESSING');
        send.disabled=client.pending || processing || !client.sessionVerified || !client.conversation;
        clear.disabled=client.pending || processing || !client.sessionVerified; question.disabled=client.pending || processing || !client.sessionVerified;
        if (!client.sessionVerified) { question.value=''; count.textContent='0/1200'; }
        for (const turn of turns) {
            const wrapper=element('article','ute-ai-turn'); wrapper.append(element('p','ute-ai-question',turn.question));
            if (turn.status==='PROCESSING') wrapper.append(element('p','ute-ai-answer','Đang trả lời…'));
            if (turn.status==='NEEDS_ORDER') {
                wrapper.append(element('p','ute-ai-answer',turn.error ? turn.error.message : 'Chọn một đơn trên tài khoản để tiếp tục.'));
                if(turn===last) {
                    const choose=element('button','ute-ai-retry','Chọn đơn để tiếp tục'); choose.type='button'; choose.disabled=client.pending;
                    choose.addEventListener('click',openOrderPicker); wrapper.append(choose);
                    const selectionKey=turn.requestId+':'+(turn.error ? turn.error.code : '')+':'+(turn.selectedOrderCode || '');
                    if(selectionPromptId!==selectionKey) { selectionPromptId=selectionKey; orderPicker.hidden=false; chooseOrder.setAttribute('aria-expanded','true'); window.setTimeout(function(){client.loadOrders(0);},0); }
                }
            }
            if (turn.status==='COMPLETE') {
                wrapper.append(element('p','ute-ai-answer',turn.answer || ''));
                for (const source of (turn.links || [])) if (safeSource(source.path)) {
                    const anchor=element('a','ute-ai-source',source.label); anchor.href=root.dataset.context.replace(/\/$/,'')+source.path;
                    wrapper.append(anchor);
                }
                if (turn.checkedAt) {
                    const time=new Date(turn.checkedAt);
                    if (!Number.isNaN(time.getTime())) wrapper.append(element('small','ute-ai-time','Kiểm tra lúc '+time.toLocaleString('vi-VN')));
                }
            }
            if (turn.status==='FAILED') {
                const failure=element('div','ute-ai-error',turn.error ? turn.error.message : GENERAL_ERROR); failure.setAttribute('role','alert');
                if (turn===last) {
                    const retry=element('button','ute-ai-retry','Thử lại'); retry.type='button'; retry.dataset.retryAt=String(turn.retryAt || 0);
                    retry.disabled=client.pending || (turn.retryAt || 0)>Date.now(); retry.addEventListener('click',function () {
                        client.retry(turn.requestId).then(function (ok) { if (ok && question.value.trim()===turn.question) { question.value=''; count.textContent='0/1200'; } });
                    });
                    failure.append(document.createElement('br'),retry);
                }
                wrapper.append(failure);
            }
            messages.append(wrapper);
        }
        if (client.sessionVerified && client.conversation && !turns.length) {
            messages.append(element('p','ute-ai-answer','Bạn có thể hỏi tự nhiên về UTEExpress, giao nhận hoặc tra cứu đơn của mình.'));
        }
        if (client.sessionVerified && client.conversation) {
            for (const text of (client.conversation.suggestions || [])) {
                const button=element('button','',text); button.type='button'; button.disabled=client.pending || processing;
                button.addEventListener('click',function () { question.value=text; count.textContent=text.length+'/1200'; client.send(text).then(afterSend); }); suggestions.append(button);
            }
        }
        suggestions.scrollLeft=suggestionScroll;
        orders.replaceChildren(); ordersNotice.textContent=client.ordersNotice;
        selectedOrder.textContent=client.sessionVerified && client.selectedOrderCode ? 'Đang hỏi: '+client.selectedOrderCode : 'Chưa chọn đơn · Hỏi chung không cần chọn';
        unselectOrder.hidden=!client.selectedOrderCode || !client.sessionVerified;
        chooseOrder.disabled=unselectOrder.disabled=client.pending || processing || !client.sessionVerified;
        refreshOrders.disabled=client.pending || client.ordersPending || !client.sessionVerified;
        const page=client.sessionVerified ? client.orderPage : null;
        document.getElementById('uteAssistantOrderHeading').textContent=client.conversation && client.conversation.role==='SHIPPER' ? 'Đơn được phân công cho tôi' : 'Đơn do tôi tạo';
        for(const order of (page ? page.orders : [])) {
            const option=element('button',''); option.type='button'; option.setAttribute('aria-pressed',String(client.selectedOrderCode===order.trackingCode));
            option.append(element('span','',order.trackingCode),element('small','',order.status)); option.title=order.serviceType || '';
            option.disabled=client.pending || processing || client.ordersPending;
            option.addEventListener('click',function(){client.selectOrder(order.trackingCode).then(function(ok){if(ok){orderPicker.hidden=true;chooseOrder.setAttribute('aria-expanded','false');}});}); orders.append(option);
        }
        orders.scrollTop=ordersScroll;
        previousOrders.disabled=!page || page.page===0 || client.pending || client.ordersPending;
        nextOrders.disabled=!page || !page.hasNext || page.page>=1000 || client.pending || client.ordersPending;
        orderPageLabel.textContent=page ? 'Trang '+(page.page+1) : '';
        if(!client.sessionVerified) { orderPicker.hidden=true; chooseOrder.setAttribute('aria-expanded','false'); ordersNotice.textContent=''; selectionPromptId=null; }
        messages.scrollTop=messages.scrollHeight;
        if (processing && !client.pending && !panel.hidden && !pollTimer && pollCount<30) {
            pollTimer=window.setTimeout(function () { pollTimer=null; pollCount++; client.load(); },2000);
        }
        if (processing && !client.pending && pollCount>=30) {
            // Không gửi lại AI tự động khi server còn xử lý. Cho người dùng chủ động kiểm tra kết quả.
            notice.textContent='Yêu cầu đang xử lý lâu hơn dự kiến. Kiểm tra kết quả trước khi gửi tiếp.';
            const check=element('button','ute-ai-retry','Kiểm tra kết quả'); check.type='button';
            check.addEventListener('click',function () { pollCount=0; client.load(); }); notice.append(check);
            messages.querySelectorAll('.ute-ai-turn:last-child .ute-ai-answer').forEach(node => { node.textContent='Chưa nhận được kết quả từ server.'; });
        }
    }
    function afterSend(ok) { if(ok){question.value='';count.textContent='0/1200';} }
    function openOrderPicker() { orderPicker.hidden=false; chooseOrder.setAttribute('aria-expanded','true'); client.loadOrders(0); }
    chooseOrder.addEventListener('click',function(){if(orderPicker.hidden) openOrderPicker(); else {orderPicker.hidden=true;chooseOrder.setAttribute('aria-expanded','false');}});
    unselectOrder.addEventListener('click',function(){client.selectOrder(null);});
    refreshOrders.addEventListener('click',function(){client.loadOrders(client.orderPage ? client.orderPage.page : 0);});
    previousOrders.addEventListener('click',function(){if(client.orderPage) client.loadOrders(client.orderPage.page-1);});
    nextOrders.addEventListener('click',function(){if(client.orderPage) client.loadOrders(client.orderPage.page+1);});
    function closePanel() {
        if (pollTimer) window.clearTimeout(pollTimer); pollTimer=null;
        panel.hidden=true; launcher.hidden=false; launcher.setAttribute('aria-expanded','false'); document.body.classList.remove('ute-assistant-open'); launcher.focus();
    }
    launcher.addEventListener('click',function () {
        panel.hidden=false; launcher.hidden=true; launcher.setAttribute('aria-expanded','true'); document.body.classList.add('ute-assistant-open');
        pollCount=0; client.load().then(function(){if(client.sessionVerified) client.loadOrders(0);}); question.focus();
    });
    root.querySelector('[data-ai-minimize]').addEventListener('click',closePanel);
    root.querySelector('[data-ai-close]').addEventListener('click',closePanel);
    root.addEventListener('keydown',function (event) {
        if(event.key==='Escape') {
            if(!orderPicker.hidden) { orderPicker.hidden=true;chooseOrder.setAttribute('aria-expanded','false');chooseOrder.focus(); }
            else closePanel();
        }
    });
    question.addEventListener('input',function () { count.textContent=question.value.length+'/1200'; });
    form.addEventListener('submit',function (event) { event.preventDefault(); client.send(question.value).then(afterSend); });
    question.addEventListener('keydown',function (event) { if (event.key==='Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); form.requestSubmit(); } });
    clear.addEventListener('click',function () { if (window.confirm('Xóa hội thoại trợ lý AI của phiên hiện tại?')) client.clear(); });
    function startCountdown() { if (countdown) return; countdown=window.setInterval(function () {
        root.querySelectorAll('[data-retry-at]').forEach(button => {
            const seconds=Math.max(0,Math.ceil((Number(button.dataset.retryAt)-Date.now())/1000));
            button.textContent=seconds ? 'Thử lại sau '+seconds+'s' : 'Thử lại'; button.disabled=client.pending || seconds>0;
        });
    },1000); }
    startCountdown();
    window.addEventListener('pagehide',function () { if (pollTimer) window.clearTimeout(pollTimer); pollTimer=null; window.clearInterval(countdown); countdown=null; client.discard(); });
    window.addEventListener('pageshow',function (event) { startCountdown(); if (event.persisted) { client.discard(); closePanel(); } });
    document.addEventListener('visibilitychange',function () {
        // load ẩn ảnh chụp cũ cho đến khi xác thực lại; nếu offline cũng không lộ lịch sử trên tab vừa quay lại.
        if (!document.hidden && !panel.hidden && !client.pending) client.load();
    });
    document.addEventListener('submit',function (event) { if (event.target.action && new URL(event.target.action,location.href).pathname.endsWith('/logout')) { client.discard(); closePanel(); } },true);
})();
