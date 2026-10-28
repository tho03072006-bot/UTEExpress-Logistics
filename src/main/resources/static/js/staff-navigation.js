(function () {
    'use strict';
    const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/đ/g, 'd');
    document.querySelectorAll('.staff-nav').forEach(nav => {
        const links = Array.from(nav.querySelectorAll('a[href]'));
        const groups = Array.from(nav.querySelectorAll('details'));
        // Chỉ lưu cách mở menu trong phiên trình duyệt, tách theo vai trò; không lưu dữ liệu tài khoản.
        const storageKey = 'ute.staff.menu.' + nav.dataset.staffRole;
        let preferences = {};
        try {
            const saved = JSON.parse(window.sessionStorage.getItem(storageKey));
            if (saved && typeof saved === 'object' && !Array.isArray(saved)) preferences = saved;
        } catch (_) { /* Trình duyệt chặn storage vẫn dùng menu bình thường. */ }
        groups.forEach(group => {
            const value = preferences[group.dataset.staffGroup];
            if (typeof value === 'boolean') group.open = value;
        });
        const path = window.location.pathname.replace(/\/$/, '');
        const best = links.filter(link => path === link.pathname || path.startsWith(link.pathname + '/'))
            .sort((a, b) => b.pathname.length - a.pathname.length)[0];
        if (best) {
            best.classList.add('active'); best.setAttribute('aria-current', 'page');
            const group = best.closest('details'); if (group) group.open = true;
            if (nav.clientHeight > 0 && nav.scrollHeight > nav.clientHeight)
                best.scrollIntoView({block: 'nearest', inline: 'nearest'});
        }
        const input = nav.querySelector('[data-staff-menu-search]');
        const openStates = new Map();
        const renderedStates = new Map(groups.map(group => [group, group.open]));
        groups.forEach(group => group.addEventListener('toggle', () => {
            // Việc mở nhóm để hiển thị kết quả tìm kiếm không thay đổi lựa chọn của người dùng.
            if (input.value.trim() || openStates.size || renderedStates.get(group) === group.open) return;
            renderedStates.set(group, group.open);
            preferences[group.dataset.staffGroup] = group.open;
            try { window.sessionStorage.setItem(storageKey, JSON.stringify(preferences)); }
            catch (_) { /* Không bắt buộc có storage. */ }
        }));
        function filterMenu() {
            const query = normalize(input.value.trim());
            if (query && !openStates.size) groups.forEach(group => openStates.set(group, group.open));
            links.forEach(link => {
                const group = link.closest('details');
                const groupTitle = group ? group.querySelector('summary').textContent : '';
                link.hidden = !!query && !normalize(link.textContent + ' ' + groupTitle).includes(query);
            });
            groups.forEach(group => {
                group.hidden = !!query && !Array.from(group.querySelectorAll('a')).some(link => !link.hidden);
                if (query) group.open = true;
                else if (openStates.has(group)) group.open = openStates.get(group);
            });
            nav.querySelector('[data-staff-menu-empty]').hidden = links.some(link => !link.hidden);
            if (!query) openStates.clear();
        }
        input.addEventListener('input', filterMenu);
        input.addEventListener('keydown', event => {
            if (event.key === 'Escape') { input.value = ''; filterMenu(); event.preventDefault(); }
        });
    });
}());
