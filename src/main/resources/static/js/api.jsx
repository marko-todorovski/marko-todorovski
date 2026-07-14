(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};

    const AuthApi = (() => {
    let csrf = null;
    const mutationMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

    async function parseResponse(res) {
        if (res.status === 204) return null;
        const contentType = res.headers.get('content-type') || '';
        if (contentType.includes('application/json')) return await res.json();
        const text = await res.text();
        return text ? { message: text } : null;
    }

    async function parseErrorResponse(res) {
        const contentType = res.headers.get('content-type') || '';
        if (contentType.includes('application/json')) return await res.json();
        const text = await res.text();
        return text ? { message: text } : null;
    }

    function buildError(res, body) {
        const err = new Error((body && body.message) || (res.status === 401 ? 'Authentication required' : `HTTP ${res.status}`));
        err.status = res.status;
        err.code = body && body.code;
        err.body = body;
        return err;
    }

    async function fetchCsrfToken() {
        const res = await fetch('/api/auth/csrf', {
            method: 'GET',
            credentials: 'same-origin',
            headers: { Accept: 'application/json' },
        });
        if (!res.ok) throw buildError(res, await parseResponse(res));
        csrf = await res.json();
        return csrf;
    }

    async function apiFetch(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');
        let body = options.body;
        const isJsonBody = body && !(body instanceof FormData) && typeof body !== 'string';
        if (isJsonBody) {
            headers.set('Content-Type', 'application/json');
            body = JSON.stringify(body);
        } else if (typeof body === 'string' && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }
        if (mutationMethods.has(method)) {
            if (!csrf || !csrf.headerName || !csrf.token) await fetchCsrfToken();
            headers.set(csrf.headerName, csrf.token);
        }
        const res = await fetch(url, { ...options, method, body, headers, credentials: 'same-origin' });
        const data = await parseResponse(res);
        if (!res.ok) throw buildError(res, data);
        return data;
    }

    async function apiFetchBlob(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set('Accept', options.accept || 'image/svg+xml');
        let body = options.body;
        const isJsonBody = body && !(body instanceof FormData) && typeof body !== 'string';
        if (isJsonBody) {
            headers.set('Content-Type', 'application/json');
            body = JSON.stringify(body);
        }
        if (mutationMethods.has(method)) {
            if (!csrf || !csrf.headerName || !csrf.token) await fetchCsrfToken();
            headers.set(csrf.headerName, csrf.token);
        }
        const res = await fetch(url, { ...options, method, body, headers, credentials: 'same-origin' });
        if (!res.ok) throw buildError(res, await parseErrorResponse(res));
        return await res.blob();
    }

    async function refreshCsrfToken() {
        csrf = null;
        return fetchCsrfToken();
    }

    return {
        fetchCsrfToken,
        refreshCsrfToken,
        apiFetch,
        apiFetchBlob,
        registerUser: data => apiFetch('/api/auth/register', { method: 'POST', body: data }),
        loginUser: data => apiFetch('/api/auth/login', { method: 'POST', body: data }),
        logoutUser: () => apiFetch('/api/auth/logout', { method: 'POST' }),
        getCurrentUser: () => apiFetch('/api/auth/me'),
        listProjects: () => apiFetch('/api/projects'),
        createProject: data => apiFetch('/api/projects', { method: 'POST', body: data }),
        getProject: id => apiFetch(`/api/projects/${id}`),
        updateProject: (id, data) => apiFetch(`/api/projects/${id}`, { method: 'PUT', body: data }),
        deleteProject: id => apiFetch(`/api/projects/${id}`, { method: 'DELETE' }),
        listProjectDiagrams: id => apiFetch(`/api/projects/${id}/diagrams`),
        saveDiagram: (projectId, data) => apiFetch(`/api/projects/${projectId}/diagrams`, { method: 'POST', body: data }),
        attachDiagram: (projectId, diagramId, data) => apiFetch(`/api/projects/${projectId}/diagrams/${diagramId}/attach`, { method: 'POST', body: data }),
        getDiagram: id => apiFetch(`/api/workspace/diagrams/${id}`),
        updateDiagramMetadata: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/metadata`, { method: 'PUT', body: data }),
        deleteDiagram: id => apiFetch(`/api/workspace/diagrams/${id}`, { method: 'DELETE' }),
        previewDiagram: sourceCode => apiFetchBlob('/api/workspace/preview', { method: 'POST', body: { sourceCode, outputFormat: 'SVG' }, accept: 'image/svg+xml' }),
        explainDiagram: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/ai/explain`, { method: 'POST', body: data }),
        suggestDiagramImprovements: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/ai/suggestions`, { method: 'POST', body: data }),
        modifyDiagramWithAi: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/ai/modify`, { method: 'POST', body: data }),
        createShare: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/shares`, { method: 'POST', body: data }),
        listShares: id => apiFetch(`/api/workspace/diagrams/${id}/shares`),
        revokeShare: (id, shareId) => apiFetch(`/api/workspace/diagrams/${id}/shares/${shareId}/revoke`, { method: 'POST' }),
        getPublicShare: token => apiFetch(`/api/public/shares/${encodeURIComponent(token)}`),
        previewPublicShare: token => apiFetchBlob(`/api/public/shares/${encodeURIComponent(token)}/preview`, { accept: 'image/svg+xml' }),
        downloadPublicShare: (token, format) => apiFetchBlob(`/api/public/shares/${encodeURIComponent(token)}/download?format=${encodeURIComponent(format)}`, { accept: '*/*' }),
        listVersions: id => apiFetch(`/api/workspace/diagrams/${id}/versions`),
        createVersion: (id, data) => apiFetch(`/api/workspace/diagrams/${id}/versions`, { method: 'POST', body: data }),
        getVersion: (id, versionNumber) => apiFetch(`/api/workspace/diagrams/${id}/versions/${versionNumber}`),
        restoreVersion: (id, versionNumber) => apiFetch(`/api/workspace/diagrams/${id}/versions/${versionNumber}/restore`, { method: 'POST' }),
    };
})();

    namespace.modules.api = AuthApi;
})();
