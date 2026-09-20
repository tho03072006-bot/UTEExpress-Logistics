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

/*
 * Nut chuyen che do sang / toi.
 *
 * Viec GAN che do khong nam o day ma o doan script nho trong <head> cua
 * fragments/base.html - phai chay truoc khi trang duoc ve, neu khong nguoi dung se
 * thay mot nhay trang toat. File nay chi lo phan bam nut va ghi nho lua chon.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var button = document.getElementById('uteThemeToggle');
        if (!button) {
            return;
        }

        button.addEventListener('click', function () {
            var root = document.documentElement;
            var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            try {
                localStorage.setItem('ute-theme', next);
            } catch (e) {
                // Trinh duyet chan localStorage (che do rieng tu): van doi duoc che do
                // cho lan xem nay, chi la sang trang khac thi tro ve mac dinh.
            }
        });
    });
})();

/*
 * Dem so ky tu o o nhan xet cua form danh gia dich vu (TV1 - viec 4).
 * Chi la tro giup nhin thay ngay cho nguoi dung; rang buoc toi thieu 50 ky tu van duoc
 * kiem tra that o phia may chu (ServiceReviewRequest) - khong tin vao kiem tra phia trinh duyet.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var textarea = document.getElementById('reviewContent');
        var counter = document.getElementById('reviewCharCount');
        if (!textarea || !counter) {
            return;
        }

        var minLength = parseInt(textarea.getAttribute('data-min-length'), 10) || 0;

        function update() {
            var length = textarea.value.trim().length;
            counter.textContent = length;
            counter.classList.toggle('text-danger', length > 0 && length < minLength);
        }

        textarea.addEventListener('input', update);
        update();
    });
})();

/*
 * Dem tang dan cho cac the so lieu o trang tong quan.
 *
 * So that DA NAM SAN trong HTML do Thymeleaf render; doan nay chi doc so do ra roi
 * dem tu 0 len. Neu JS loi hoac bi chan thi so van hien binh thuong, chi la khong
 * co hieu ung - khong bao gio de trang trong.
 *
 * Ton trong cai dat "giam chuyen dong" cua he dieu hanh giong nhu ben CSS: nguoi da
 * bat cong tac do se thay so dung luon, khong nhay lien tuc.
 */
(function () {
    'use strict';

    var DURATION_MS = 600;

    function animateCount(element) {
        var target = parseInt(element.textContent.trim(), 10);
        // O nao khong phai so nguyen thuan (vd co dau cham, ky tu %) thi de nguyen.
        if (isNaN(target) || target <= 0) {
            return;
        }

        var start = null;
        element.textContent = '0';

        function step(timestamp) {
            if (start === null) {
                start = timestamp;
            }
            var progress = Math.min((timestamp - start) / DURATION_MS, 1);
            // Cham dan ve cuoi cho cam giac dung lai nhe nhang thay vi phanh gap.
            var eased = 1 - Math.pow(1 - progress, 3);
            element.textContent = Math.round(target * eased);

            if (progress < 1) {
                window.requestAnimationFrame(step);
            } else {
                element.textContent = target; // chot lai dung so that
            }
        }

        window.requestAnimationFrame(step);
    }

    document.addEventListener('DOMContentLoaded', function () {
        var reduceMotion = window.matchMedia
                && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduceMotion) {
            return;
        }
        Array.prototype.forEach.call(
            document.querySelectorAll('.ute-stat-value'), animateCount);
    });
})();

/*
 * Hoi lai truoc khi lam viec khong quay lai duoc (huy don, xoa...).
 *
 * Bat su kien submit cua moi form co thuoc tinh data-ute-confirm, chan lai roi mo hop
 * thoai Bootstrap. Bam dong y thi moi submit that.
 *
 * Vi sao dung hop thoai Bootstrap thay vi ham confirm() co san cua trinh duyet:
 * confirm() hien hop mac dinh cua he dieu hanh, khong theo bang mau va khong doi theo
 * che do sang/toi cua site; nhin lac long va khong ro dang hoi ve cai gi.
 *
 * Gan bang su kien submit chu khong phai click vao nut: nhu vay bam Enter trong form
 * cung duoc hoi lai, khong lot luoi.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var dialogElement = document.getElementById('uteConfirmDialog');
        var messageElement = document.getElementById('uteConfirmMessage');
        var acceptButton = document.getElementById('uteConfirmAccept');

        // Thieu hop thoai (trang khong di qua template chung) thi thoi, de form chay nhu cu.
        if (!dialogElement || !messageElement || !acceptButton || !window.bootstrap) {
            return;
        }

        var dialog = new window.bootstrap.Modal(dialogElement);
        var formDangCho = null;

        document.addEventListener('submit', function (event) {
            var form = event.target;
            if (!form || !form.getAttribute) {
                return;
            }
            var cauHoi = form.getAttribute('data-ute-confirm');
            if (!cauHoi) {
                return;
            }
            // Da xac nhan roi thi cho di tiep, khong hoi vong lap.
            if (form.dataset.uteConfirmed === 'true') {
                return;
            }

            event.preventDefault();
            formDangCho = form;
            messageElement.textContent = cauHoi;
            acceptButton.textContent = form.getAttribute('data-ute-confirm-nut') || 'Đồng ý';
            dialog.show();
        });

        acceptButton.addEventListener('click', function () {
            if (!formDangCho) {
                return;
            }
            var form = formDangCho;
            formDangCho = null;
            form.dataset.uteConfirmed = 'true';
            dialog.hide();
            form.submit();
        });

        // Dong hop thoai ma khong dong y thi quen form di, tranh lan sang lan bam sau.
        dialogElement.addEventListener('hidden.bs.modal', function () {
            formDangCho = null;
        });
    });
})();
