(() => {
    'use strict';

    const root = document.documentElement;
    const savedTheme = localStorage.getItem('workly-theme');
    if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        root.dataset.theme = 'dark';
    }

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const api = async (url, options = {}) => {
        const headers = new Headers(options.headers || {});
        if (csrfToken && csrfHeader) headers.set(csrfHeader, csrfToken);
        if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }
        const response = await fetch(url, {...options, headers});
        const type = response.headers.get('content-type') || '';
        const body = type.includes('application/json') ? await response.json() : null;
        if (!response.ok) {
            const fieldMessage = body?.fieldErrors ? Object.values(body.fieldErrors)[0] : null;
            throw new Error(fieldMessage || body?.message || '요청 처리에 실패했습니다.');
        }
        return body;
    };

    const toast = (message, error = false) => {
        const stack = document.getElementById('toastStack');
        if (!stack) return;
        const item = document.createElement('div');
        item.className = `toast${error ? ' error' : ''}`;
        item.innerHTML = `<strong>${error ? '처리하지 못했습니다' : '완료되었습니다'}</strong><small></small>`;
        item.querySelector('small').textContent = message;
        stack.append(item);
        window.setTimeout(() => item.remove(), 3800);
    };

    window.Workly = {api, toast};

    document.querySelector('[data-theme-toggle]')?.addEventListener('click', () => {
        const dark = root.dataset.theme === 'dark';
        root.dataset.theme = dark ? 'light' : 'dark';
        localStorage.setItem('workly-theme', dark ? 'light' : 'dark');
    });

    document.querySelector('[data-sidebar-open]')?.addEventListener('click', () => document.body.classList.add('sidebar-open'));
    document.querySelectorAll('[data-sidebar-close]').forEach(element =>
        element.addEventListener('click', () => document.body.classList.remove('sidebar-open')));

    const notificationPanel = document.querySelector('[data-notification-panel]');
    document.querySelector('[data-notification-toggle]')?.addEventListener('click', event => {
        event.stopPropagation();
        notificationPanel?.classList.toggle('open');
    });
    document.addEventListener('click', event => {
        if (notificationPanel && !notificationPanel.contains(event.target)) notificationPanel.classList.remove('open');
    });
    document.querySelectorAll('[data-notification-id]').forEach(link => {
        link.addEventListener('click', () => {
            api(`/api/v1/notifications/${link.dataset.notificationId}/read`, {method: 'POST'}).catch(() => {});
        });
    });

    document.querySelectorAll('[data-modal-open]').forEach(button => {
        button.addEventListener('click', () => document.getElementById(button.dataset.modalOpen)?.classList.add('open'));
    });
    document.querySelectorAll('[data-modal-close]').forEach(button => {
        button.addEventListener('click', () => button.closest('.modal')?.classList.remove('open'));
    });
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', event => {
            if (event.target === modal) modal.classList.remove('open');
        });
    });

    const setField = (form, name, value) => {
        const field = form?.elements.namedItem(name);
        if (field) field.value = value ?? '';
    };

    const employeeForm = document.getElementById('employeeForm');
    document.querySelectorAll('[data-new-employee]').forEach(button => button.addEventListener('click', () => {
        employeeForm?.reset();
        if (!employeeForm) return;
        employeeForm.action = '/api/v1/employees';
        employeeForm.dataset.method = 'POST';
        employeeForm.querySelectorAll('.account-field').forEach(field => field.hidden = false);
        document.querySelector('[data-employee-modal-title]').textContent = '새 사원 등록';
    }));
    document.querySelectorAll('[data-edit-employee]').forEach(button => button.addEventListener('click', () => {
        if (!employeeForm) return;
        employeeForm.reset();
        employeeForm.action = `/api/v1/employees/${button.dataset.id}`;
        employeeForm.dataset.method = 'PUT';
        setField(employeeForm, 'employeeNumber', button.dataset.number);
        setField(employeeForm, 'employeeName', button.dataset.name);
        setField(employeeForm, 'email', button.dataset.email);
        setField(employeeForm, 'phone', button.dataset.phone);
        setField(employeeForm, 'departmentId', button.dataset.department);
        setField(employeeForm, 'positionId', button.dataset.position);
        setField(employeeForm, 'hireDate', button.dataset.hire);
        setField(employeeForm, 'employmentStatus', button.dataset.status);
        setField(employeeForm, 'employmentType', button.dataset.type);
        employeeForm.querySelectorAll('.account-field').forEach(field => field.hidden = true);
        document.querySelector('[data-employee-modal-title]').textContent = '사원 정보 수정';
    }));

    const passwordResetForm = document.getElementById('passwordResetForm');
    document.querySelectorAll('[data-reset-password]').forEach(button => button.addEventListener('click', () => {
        if (!passwordResetForm) return;
        passwordResetForm.reset();
        passwordResetForm.action = `/api/v1/admin/employees/${button.dataset.id}/reset-password`;
        const name = document.querySelector('[data-password-reset-name]');
        if (name) name.textContent = button.dataset.name;
    }));

    const departmentForm = document.getElementById('departmentForm');
    document.querySelectorAll('[data-new-department]').forEach(button => button.addEventListener('click', () => {
        departmentForm?.reset();
        if (!departmentForm) return;
        departmentForm.action = '/api/v1/admin/departments';
        departmentForm.dataset.method = 'POST';
        document.querySelector('[data-department-modal-title]').textContent = '부서 추가';
    }));
    document.querySelectorAll('[data-edit-department]').forEach(button => button.addEventListener('click', () => {
        if (!departmentForm) return;
        departmentForm.reset();
        departmentForm.action = `/api/v1/admin/departments/${button.dataset.id}`;
        departmentForm.dataset.method = 'PUT';
        setField(departmentForm, 'departmentCode', button.dataset.code);
        setField(departmentForm, 'departmentName', button.dataset.name);
        setField(departmentForm, 'parentDepartmentId', button.dataset.parent);
        setField(departmentForm, 'leaderEmployeeId', button.dataset.leader);
        setField(departmentForm, 'description', button.dataset.description);
        setField(departmentForm, 'sortOrder', button.dataset.order);
        document.querySelector('[data-department-modal-title]').textContent = '부서 수정';
    }));

    const positionForm = document.getElementById('positionForm');
    document.querySelectorAll('[data-new-position]').forEach(button => button.addEventListener('click', () => {
        positionForm?.reset();
        if (!positionForm) return;
        positionForm.action = '/api/v1/admin/positions';
        positionForm.dataset.method = 'POST';
        document.querySelector('[data-position-modal-title]').textContent = '직급 추가';
    }));
    document.querySelectorAll('[data-edit-position]').forEach(button => button.addEventListener('click', () => {
        if (!positionForm) return;
        positionForm.reset();
        positionForm.action = `/api/v1/admin/positions/${button.dataset.id}`;
        positionForm.dataset.method = 'PUT';
        setField(positionForm, 'positionCode', button.dataset.code);
        setField(positionForm, 'positionName', button.dataset.name);
        setField(positionForm, 'positionLevel', button.dataset.level);
        setField(positionForm, 'sortOrder', button.dataset.order);
        document.querySelector('[data-position-modal-title]').textContent = '직급 수정';
    }));

    const formDataToObject = form => {
        const data = {};
        new FormData(form).forEach((value, key) => {
            const field = form.elements.namedItem(key);
            if (field?.type === 'number') data[key] = value === '' ? null : Number(value);
            else data[key] = value === '' ? null : value;
        });
        form.querySelectorAll('input[type="checkbox"][name]').forEach(input => data[input.name] = input.checked);
        return data;
    };

    document.querySelectorAll('form[data-json-form]').forEach(form => {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const submit = form.querySelector('[type="submit"]');
            if (submit) submit.disabled = true;
            try {
                const result = await api(form.action, {
                    method: form.dataset.method || form.method || 'POST',
                    body: JSON.stringify(formDataToObject(form))
                });
                toast(result?.message || '정상적으로 처리되었습니다.');
                if (form.dataset.successUrl) window.location.assign(form.dataset.successUrl);
                else window.setTimeout(() => window.location.reload(), 450);
            } catch (error) {
                toast(error.message, true);
                if (submit) submit.disabled = false;
            }
        });
    });

    document.querySelectorAll('form[data-multipart-form]').forEach(form => {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const submit = form.querySelector('[type="submit"]');
            if (submit) submit.disabled = true;
            try {
                const result = await api(form.action, {method: 'POST', body: new FormData(form)});
                const message = result?.message || (result?.success !== undefined
                    ? `${result.success}건 등록, ${result.failed ?? 0}건 실패` : '업로드가 완료되었습니다.');
                toast(message);
                window.setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                toast(error.message, true);
                if (submit) submit.disabled = false;
            }
        });
    });

    document.querySelectorAll('[data-api-action]').forEach(button => {
        button.addEventListener('click', async event => {
            event.preventDefault();
            if (button.dataset.confirm && !window.confirm(button.dataset.confirm)) return;
            let body;
            if (button.dataset.prompt) {
                const value = window.prompt(button.dataset.prompt);
                if (value === null) return;
                body = JSON.stringify({opinion: value});
            } else if (button.dataset.body) {
                body = button.dataset.body;
            }
            button.disabled = true;
            try {
                const result = await api(button.dataset.apiAction, {method: button.dataset.method || 'POST', body});
                toast(result?.message || '정상적으로 처리되었습니다.');
                window.setTimeout(() => window.location.assign(button.dataset.successUrl || window.location.href), 450);
            } catch (error) {
                toast(error.message, true);
                button.disabled = false;
            }
        });
    });

    document.querySelectorAll('[data-tab]').forEach(tab => {
        tab.addEventListener('click', () => {
            const group = tab.closest('[data-tabs-root]');
            group?.querySelectorAll('[data-tab]').forEach(item => item.classList.toggle('active', item === tab));
            group?.querySelectorAll('[data-tab-panel]').forEach(panel =>
                panel.classList.toggle('active', panel.dataset.tabPanel === tab.dataset.tab));
        });
    });

    const clock = document.querySelector('[data-live-clock]');
    if (clock) {
        const update = () => clock.textContent = new Intl.DateTimeFormat('ko-KR', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
        }).format(new Date());
        update();
        window.setInterval(update, 1000);
    }
})();
