(() => {
    const savedTheme = localStorage.getItem('workly-theme');
    if (savedTheme === 'dark') document.documentElement.dataset.theme = 'dark';
    document.querySelectorAll('[data-demo-login]').forEach(button => {
        button.addEventListener('click', () => {
            document.querySelector('[name="username"]').value = button.dataset.demoLogin;
            document.querySelector('[name="password"]').value = button.dataset.demoPassword;
            document.querySelector('[name="username"]').focus();
        });
    });
})();
