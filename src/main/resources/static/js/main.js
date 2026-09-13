/*
 * JS dung chung cua UTEExpress - nap tren moi trang qua template fragments/base.html.
 *
 * Hien tai lo dung mot viec: danh dau muc dang mo tren sidebar. Lam bang JS o phia trinh duyet
 * thay vi so sanh duong dan trong Thymeleaf, vi tu Thymeleaf 3.1 khong con truy cap
 * duoc #httpServletRequest - lam kieu nay khoi phai them code o phia server.
 *
 * TODO (TV2): them ket noi WebSocket "/ws-tracking" o day de nhan cap nhat trang thai don realtime.
 */
(function () {
    'use strict';

    /**
     * Gan class "active" cho muc sidebar khop voi trang dang mo.
     * Chon muc co duong dan TRUNG KHOP DAI NHAT de "/nguoi-dung/don-hang" khong lam sang
     * ca muc "/nguoi-dung" neu sau nay co them muc cha nhu vay.
     */
    function highlightCurrentNavItem() {
        var currentPath = window.location.pathname;
        var links = document.querySelectorAll('.ute-nav-link');
        var best = null;
        var bestLength = 0;

        Array.prototype.forEach.call(links, function (link) {
            var href = link.getAttribute('href');
            if (!href || href === '#') {
                return;
            }
            var isMatch = currentPath === href || currentPath.indexOf(href + '/') === 0;
            if (isMatch && href.length > bestLength) {
                best = link;
                bestLength = href.length;
            }
        });

        if (best) {
            // Cung mot menu duoc ve 2 lan (sidebar co dinh + menu truot tren dien thoai),
            // nen danh dau tat ca link co cung href chu khong chi cai tim duoc.
            var href = best.getAttribute('href');
            Array.prototype.forEach.call(links, function (link) {
                if (link.getAttribute('href') === href) {
                    link.classList.add('active');
                }
            });
        }
    }

    document.addEventListener('DOMContentLoaded', highlightCurrentNavItem);
})();
