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
    window.addEventListener('pageshow',function (event) {
        if(!event.persisted) return;
        document.querySelectorAll('form[data-order-form]').forEach(function (form) {
            delete form.dataset.orderSubmitting;
            form.querySelectorAll('button[type=submit]').forEach(function (button) { button.disabled=false; });
        });
    });
}());
