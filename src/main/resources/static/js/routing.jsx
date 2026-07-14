(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};

    function viewFromHash() {
    const raw = window.location.hash.replace(/^#\/?/, '');
    const parts = raw.split('/').filter(Boolean);
    if (parts[0] === 'login') return { name: 'login' };
    if (parts[0] === 'register') return { name: 'register' };
    if (parts[0] === 'share' && parts[1]) return { name: 'share', token: parts.slice(1).join('/') };
    if (parts[0] === 'dashboard') return { name: 'dashboard' };
    if (parts[0] === 'projects' && parts[1]) return { name: 'project', projectId: parts[1] };
    if (parts[0] === 'diagrams' && parts[1]) return { name: 'diagram', diagramId: parts[1], projectId: parts[2] || null };
    return { name: 'generate' };
}

function hashFor(view, params = {}) {
    if (view === 'login') return '#/login';
    if (view === 'register') return '#/register';
    if (view === 'share') return `#/share/${params.token}`;
    if (view === 'dashboard') return '#/dashboard';
    if (view === 'project') return `#/projects/${params.projectId}`;
    if (view === 'diagram') return `#/diagrams/${params.diagramId}${params.projectId ? '/' + params.projectId : ''}`;
    return '#/generate';
}

    namespace.modules.routing = {
        viewFromHash,
        hashFor
    };
})();
