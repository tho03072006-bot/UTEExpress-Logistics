/*
 * Người gửi tự chọn ảnh nền cho tiêu đề trang. Lựa chọn chỉ lưu trong trình duyệt (localStorage, khóa
 * "ute-user-photo"), không gửi lên máy chủ. Script đầu trang trong base.html đọc lại khóa này trước khi vẽ
 * để không nháy ảnh mặc định. Ảnh chỉ hiện trên màn hình rộng (≥ 1200px) nên nút cũng chỉ hiện ở đó.
 */
(function (root) {
    'use strict';
    var KEY = 'ute-user-photo';
    var DEFAULT_ID = 'post-office';
    var mounts = new WeakMap();
    var PHOTOS = [
        { id: 'post-office', label: 'Bưu điện Sài Gòn', file: 'user-post-office.jpg' },
        { id: 'post-boxes', label: 'Hòm thư Bưu điện Hà Nội', file: 'user-post-boxes.jpg' },
        { id: 'doorstep', label: 'Kiện hàng trước cửa nhà', file: 'user-doorstep.jpg' },
        { id: 'locker', label: 'Tủ nhận bưu kiện', file: 'user-parcel-locker.jpg' },
        { id: 'parcel-center', label: 'Trung tâm chia chọn bưu kiện', file: 'parcel-center.jpg' }
    ];

    function known(id) {
        return PHOTOS.some(function (photo) { return photo.id === id; });
    }
    function current(storage) {
        try {
            var saved = storage.getItem(KEY);
            return known(saved) ? saved : DEFAULT_ID;
        } catch (e) {
            return DEFAULT_ID;
        }
    }
    function apply(doc, storage, id) {
        if (!known(id)) id = DEFAULT_ID;
        if (id === DEFAULT_ID) doc.documentElement.removeAttribute('data-user-photo');
        else doc.documentElement.setAttribute('data-user-photo', id);
        try { storage.setItem(KEY, id); } catch (e) { /* chế độ riêng tư: chỉ đổi cho lần xem này */ }
        return id;
    }

    function mount(doc, storage) {
        var head = doc.querySelector('.ute-page-head:not(.shipper-hero)');
        var css = doc.querySelector('link[href*="/css/ute-app.css"]');
        var previous = mounts.get(doc);
        if (previous && previous.head === head) return;
        if (previous) { previous.dispose(); mounts.delete(doc); }
        if (!head || !css) return;
        // Đường dẫn ảnh tính từ file CSS để đúng cả khi app chạy dưới context path khác "/".
        var folder = new URL('../images/photos/', css.href).href;

        var button = doc.createElement('button');
        button.type = 'button';
        button.className = 'sx-photo-btn';
        button.setAttribute('data-realtime-client', '');
        button.setAttribute('aria-expanded', 'false');
        button.innerHTML = '<i class="bi bi-image" aria-hidden="true"></i> Đổi ảnh nền';

        var menu = doc.createElement('div');
        menu.className = 'sx-photo-menu';
        menu.setAttribute('data-realtime-client', '');
        menu.hidden = true;
        menu.setAttribute('role', 'group');
        menu.setAttribute('aria-label', 'Chọn ảnh nền tiêu đề');
        PHOTOS.forEach(function (photo) {
            var option = doc.createElement('button');
            option.type = 'button';
            option.className = 'sx-photo-option';
            option.dataset.photo = photo.id;
            option.title = photo.label;
            option.setAttribute('aria-label', photo.label);
            var img = doc.createElement('img');
            img.src = folder + photo.file;
            img.alt = '';
            img.loading = 'lazy';
            option.appendChild(img);
            menu.appendChild(option);
        });

        function mark(id) {
            menu.querySelectorAll('.sx-photo-option').forEach(function (option) {
                option.setAttribute('aria-pressed', String(option.dataset.photo === id));
            });
        }
        function toggle(open) {
            menu.hidden = !open;
            button.setAttribute('aria-expanded', String(open));
        }
        mark(current(storage));
        button.addEventListener('click', function () { toggle(menu.hidden); });
        menu.addEventListener('click', function (event) {
            var option = event.target.closest('.sx-photo-option');
            if (!option) return;
            mark(apply(doc, storage, option.dataset.photo));
            toggle(false);
            button.focus();
        });
        function onKey(event) {
            if (event.key === 'Escape' && !menu.hidden) { toggle(false); button.focus(); }
        }
        function onClick(event) {
            if (!menu.hidden && !menu.contains(event.target) && event.target !== button && !button.contains(event.target)) toggle(false);
        }
        doc.addEventListener('keydown', onKey);
        doc.addEventListener('click', onClick);
        head.appendChild(button);
        head.appendChild(menu);
        mounts.set(doc, {head:head,dispose:function () {
            doc.removeEventListener('keydown', onKey); doc.removeEventListener('click', onClick);
            button.remove(); menu.remove();
        }});
    }

    if (root.document) {
        function initialize() {
            var storage;
            try { storage = root.localStorage; } catch (e) { storage = null; }
            mount(root.document, storage || { getItem: function () { return null; }, setItem: function () { } });
        }
        root.document.addEventListener('DOMContentLoaded', initialize);
        root.document.addEventListener('ute:regions-updated', initialize);
    }
    if (typeof module !== 'undefined') module.exports = { PHOTOS: PHOTOS, current: current, apply: apply };
})(typeof window !== 'undefined' ? window : globalThis);
