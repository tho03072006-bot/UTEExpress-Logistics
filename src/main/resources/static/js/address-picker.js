/*
 * Chon nhanh dia chi da luu tren form "Tao don gui hang" (TV1 - viec 1B).
 *
 * Cach dung trong template: dat thuoc tinh data-address-picker len the <select>, kem
 * data-fill-name / data-fill-phone / data-fill-address la ID cua cac o input can dien.
 * O nao form khong co (vd form khong co so dien thoai nguoi gui) thi bo trong thuoc tinh do.
 *
 * Du lieu cua tung dia chi duoc gan san vao <option> bang data-contact-name,
 * data-contact-phone, data-address-line - nen khong can goi API, chon la dien duoc ngay.
 */
(function () {
    'use strict';

    /** Dien gia tri vao o input theo ID, bo qua neu template khong khai bao o do. */
    function fillInput(inputId, value) {
        if (!inputId || value === null) {
            return;
        }
        var input = document.getElementById(inputId);
        if (input) {
            input.value = value;
        }
    }

    function applySelectedAddress(picker) {
        var option = picker.options[picker.selectedIndex];
        // Chon lai muc "Nhap dia chi moi" thi giu nguyen nhung gi nguoi dung dang go do,
        // khong xoa trang - tranh mat du lieu vi lo tay chon nham.
        if (!option || !option.value) {
            return;
        }
        fillInput(picker.getAttribute('data-fill-name'), option.getAttribute('data-contact-name'));
        fillInput(picker.getAttribute('data-fill-phone'), option.getAttribute('data-contact-phone'));
        fillInput(picker.getAttribute('data-fill-address'), option.getAttribute('data-address-line'));
    }

    document.addEventListener('DOMContentLoaded', function () {
        var pickers = document.querySelectorAll('[data-address-picker]');
        Array.prototype.forEach.call(pickers, function (picker) {
            picker.addEventListener('change', function () {
                applySelectedAddress(picker);
            });
        });
    });
})();
