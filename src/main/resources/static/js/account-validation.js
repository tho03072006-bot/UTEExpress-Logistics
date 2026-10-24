/* Phản hồi sớm trên trình duyệt; backend vẫn là nơi quyết định validation cuối cùng. */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded',function () {
        document.querySelectorAll('input[data-vn-mobile]').forEach(function (input) {
            function validate() {
                var raw=input.value, normalized=raw.trim().replace(/[ .-]/g,'').replace(/^\+84/,'0');
                input.setCustomValidity(!raw || (raw.length<=20 && /^0[35789][0-9]{8}$/.test(normalized))?'':'Nhập số di động Việt Nam 10 số hoặc dạng +84, tối đa 20 ký tự.');
            }
            input.addEventListener('input',validate); validate();
            if(input.form) input.form.addEventListener('submit',function (event) {
                validate(); if(!input.checkValidity()) { event.preventDefault(); input.reportValidity(); }
            });
        });
        document.querySelectorAll('input[data-public-password]').forEach(function (input) {
            function validate() {
                var value=input.value;
                var bytes=new TextEncoder().encode(value).length;
                var valid=value.length>=8 && value.length<=64 && bytes<=72 && /\p{L}/u.test(value) && /[0-9]/.test(value) && !/[\s\p{Cc}]/u.test(value);
                input.setCustomValidity(!value || valid?'':'Mật khẩu từ 8–64 ký tự, có chữ và số, không khoảng trắng; tối đa 72 byte UTF-8.');
            }
            input.addEventListener('input',validate); validate();
        });
        document.querySelectorAll('input[data-password-confirm]').forEach(function (input) {
            var original=document.getElementById(input.dataset.passwordConfirm);
            if(!original) return;
            function validate() { input.setCustomValidity(!input.value || input.value===original.value?'':'Hai mật khẩu chưa khớp.'); }
            input.addEventListener('input',validate); original.addEventListener('input',validate);
        });
    });
}());
