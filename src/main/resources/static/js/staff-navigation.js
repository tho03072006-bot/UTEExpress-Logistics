(function () {
    'use strict';
    const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/đ/g, 'd');
    document.querySelectorAll('.staff-nav').forEach(nav => {
        const links = Array.from(nav.querySelectorAll('a[href]'));
        const path = window.location.pathname.replace(/\/$/, '');
        const best = links.filter(link => path === link.pathname || path.startsWith(link.pathname + '/'))
            .sort((a, b) => b.pathname.length - a.pathname.length)[0];
        if (best) {
            best.classList.add('active'); best.setAttribute('aria-current', 'page');
            const group = best.closest('details'); if (group) group.open = true;
        }
        const input = nav.querySelector('[data-staff-menu-search]');
        const groups = Array.from(nav.querySelectorAll('details'));
        const openStates = new Map();
        input.addEventListener('input', () => {
            const query = normalize(input.value.trim());
            if (query && !openStates.size) groups.forEach(group => openStates.set(group, group.open));
            links.forEach(link => { link.hidden = !!query && !normalize(link.textContent).includes(query); });
            groups.forEach(group => {
                group.hidden = !!query && !Array.from(group.querySelectorAll('a')).some(link => !link.hidden);
                if (query) group.open = true;
                else if (openStates.has(group)) group.open = openStates.get(group);
            });
            nav.querySelector('[data-staff-menu-empty]').hidden = links.some(link => !link.hidden);
            if (!query) openStates.clear();
        });
    });
}());
