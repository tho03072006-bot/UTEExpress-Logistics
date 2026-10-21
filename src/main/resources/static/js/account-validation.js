/* Phản hồi sớm trên trình duyệt; backend vẫn là nơi quyết định validation cuối cùng. */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded',function () {
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
