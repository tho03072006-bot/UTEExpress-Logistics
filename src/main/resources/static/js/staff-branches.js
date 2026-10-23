document.querySelectorAll('[data-branch-fields]').forEach(fields => {
    if (fields.dataset.initialized) return;
    fields.dataset.initialized = 'true';
    const role = fields.closest('form').elements.namedItem(fields.dataset.roleName);
    const branch = fields.querySelector('[data-branch-select]');
    const primary = fields.querySelector('[data-primary-area]');
    const secondary = fields.querySelector('[data-secondary-areas]');
    function refresh() {
        const staff = role.value === 'SHIPPER' || role.value === 'MANAGER';
        const shipper = role.value === 'SHIPPER';
        branch.disabled = !staff;
        branch.required = staff;
        fields.querySelector('[data-shipper-zones]').hidden = !shipper;
        primary.disabled = secondary.disabled = !shipper;
        primary.required = shipper;
        [primary, secondary].forEach(select => Array.from(select.options).forEach(option => {
            if (!option.value) return;
            const unavailable = option.dataset.branchId !== branch.value
                || (select === secondary && option.value === primary.value);
            option.hidden = option.disabled = unavailable;
            if (unavailable) option.selected = false;
        }));
    }
    role.addEventListener('change', refresh);
    branch.addEventListener('change', refresh);
    primary.addEventListener('change', refresh);
    refresh();
});
