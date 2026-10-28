document.querySelectorAll('[data-area-fields]').forEach(fields => {
    if (fields.dataset.initialized) return;
    fields.dataset.initialized = 'true';
    const province = fields.querySelector('[data-province-select]');
    const area = fields.querySelector('[data-area-select]');
    function refresh() {
        Array.from(area.options).forEach(option => {
            if (!option.value) return;
            option.hidden = option.disabled = option.dataset.provinceCode !== province.value;
            if (option.disabled) option.selected = false;
        });
    }
    province.addEventListener('change', refresh);
    refresh();
});
