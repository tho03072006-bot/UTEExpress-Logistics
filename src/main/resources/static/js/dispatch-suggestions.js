document.addEventListener('change', event => {
    const select = event.target.closest('[data-dispatch-form] select[name="shipperId"]');
    if (!select) return;
    const note = select.form.querySelector('[name="note"]');
    note.value = select.selectedOptions[0]?.dataset.reason || '';
});
