/* Chống bấm hai lần; tính đúng-một-lần vẫn được kiểm tra bằng token và khóa database ở backend. */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded',function () {
        document.querySelectorAll('form[data-order-form]').forEach(function (form) {
            form.addEventListener('submit',function (event) {
                if(event.defaultPrevented) return;
                if(form.dataset.orderSubmitting==='true') { event.preventDefault(); return; }
                form.dataset.orderSubmitting='true';
                // Để trình duyệt đọc formaction của nút "Thêm vào giỏ" trước khi khóa nút.
                window.setTimeout(function () { form.querySelectorAll('button[type=submit]').forEach(function (button) { button.disabled=true; }); },0);
            });
        });
    });
    /* Báo giá trực tiếp: đổi địa chỉ/khối lượng/dịch vụ thì hỏi server khoảng cách đường chim bay và cước.
       Chỉ để xem trước; server tính lại khi tạo đơn. Chờ người dùng ngừng gõ 700 ms, bỏ kết quả của lần hỏi cũ. */
    document.addEventListener('DOMContentLoaded',function () {
        var panel=document.querySelector('[data-fee-quote]'), form=panel && panel.closest('form');
        if(!form) return;
        var fields={sender:form.querySelector('#senderAddress'),receiver:form.querySelector('#receiverAddress'),
            weight:form.querySelector('#weightKg'),service:form.querySelector('#serviceType')};
        var csrf=form.querySelector('input[name="_csrf"]');
        var status=panel.querySelector('[data-quote-status]'), rows=panel.querySelector('[data-quote-rows]'), km=panel.querySelector('[data-quote-km]');
        var money=new Intl.NumberFormat('vi-VN');
        var timer=null, sequence=0, lastKey='';
        function show(state,message) {
            panel.dataset.state=state; status.textContent=message||''; status.hidden=!message;
            if(state!=='ok') { rows.hidden=true; km.textContent=''; }
        }
        function ask() {
            var sender=fields.sender.value.trim(), receiver=fields.receiver.value.trim(), weight=fields.weight.value;
            if(sender.length<5 || sender.length>255 || receiver.length<5 || receiver.length>255 || /[\x00-\x1f\x7f]/.test(sender+receiver)
                    || !Number.isFinite(Number(weight)) || Number(weight)<0.01 || Number(weight)>100) {
                lastKey=''; show('idle','Nhập hai địa chỉ hợp lệ và khối lượng từ 0,01–100 kg để xem cước.'); return;
            }
            var key=[sender,receiver,weight,fields.service.value].join('|');
            if(key===lastKey) return;
            lastKey=key; var run=++sequence;
            show('loading','Đang tính khoảng cách giữa hai địa chỉ…');
            var body=new URLSearchParams({senderAddress:sender,receiverAddress:receiver,weightKg:weight,serviceType:fields.service.value,_csrf:csrf?csrf.value:''});
            fetch(panel.dataset.quoteUrl,{method:'POST',credentials:'same-origin',cache:'no-store',body:body,
                headers:{'Accept':'application/json','X-Requested-With':'XMLHttpRequest'}})
                .then(function (response) { return response.json(); })
                .then(function (data) {
                    if(run!==sequence) return;
                    if(!data.ok) { lastKey=''; show('error',data.message||'Chưa tính được cước.'); return; }
                    show('ok','');
                    km.textContent='≈ '+Number(data.distanceKm).toLocaleString('vi-VN',{maximumFractionDigits:1})+' km đường chim bay';
                    panel.querySelector('[data-quote-base]').textContent=money.format(data.baseFee)+' đ';
                    panel.querySelector('[data-quote-distance-fee]').textContent=Number(data.distanceFee)>0?money.format(data.distanceFee)+' đ':'Miễn phí';
                    panel.querySelector('[data-quote-total]').textContent=money.format(data.total)+' đ';
                    rows.hidden=false;
                })
                .catch(function () { if(run===sequence) { lastKey=''; show('error','Chưa tính được cước. Kiểm tra mạng rồi thử lại.'); } });
        }
        function schedule() {
            // Đổi đầu vào là hết hiệu lực kết quả đang đợi, kể cả đầu vào mới không hợp lệ.
            ++sequence; lastKey=''; show('idle','Đang cập nhật dữ liệu báo giá…');
            window.clearTimeout(timer); timer=window.setTimeout(ask,700);
        }
        [fields.sender,fields.receiver,fields.weight].forEach(function (input) { input.addEventListener('input',schedule); });
        fields.service.addEventListener('change',schedule);
        // Chọn từ sổ địa chỉ: address-picker.js điền thẳng vào ô, không phát sự kiện input.
        form.querySelectorAll('select[data-fill-address]').forEach(function (select) { select.addEventListener('change',function () { window.setTimeout(schedule,0); }); });
        schedule();
    });
    window.addEventListener('pageshow',function (event) {
        if(!event.persisted) return;
        document.querySelectorAll('form[data-order-form]').forEach(function (form) {
            delete form.dataset.orderSubmitting;
            form.querySelectorAll('button[type=submit]').forEach(function (button) { button.disabled=false; });
        });
    });
}());
