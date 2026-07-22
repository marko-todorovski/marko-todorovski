(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect, useRef, useCallback } = React;

    function showStartupError(error) {
        console.error(error);
        const startupError = document.getElementById('startup-error');
        if (startupError) {
            startupError.hidden = false;
        }
    }

    function requireModule(name) {
        const module = namespace.modules && namespace.modules[name];
        if (!module) {
            throw new Error(`Frontend module failed to load: ${name}`);
        }
        return module;
    }

    try {
        const AuthApi = requireModule('api');
        const { viewFromHash, hashFor } = requireModule('routing');
        const { Notice, userLabel } = requireModule('shared');
        const { LoginView, RegisterView } = requireModule('auth');
        const { DashboardView, ProjectDetailsView } = requireModule('projects');
        const { DiagramEditorView } = requireModule('editor');
        const { GeneratorView } = requireModule('generator');
        const { PublicShareView } = requireModule('sharing');
        const { InvitationLandingView } = requireModule('collaboration');
        const { RepositoriesView } = requireModule('repositories');

        function App() {
    const [currentUser, setCurrentUser] = useState(null);
    const [authStatus, setAuthStatus] = useState('loading');
    const [currentView, setCurrentView] = useState(viewFromHash());
    const [projects, setProjects] = useState([]);
    const [notice, setNotice] = useState(null);
    const navigationGuardRef = useRef(null);

    function notify(type, message) {
        setNotice({ type, message });
    }

    function hasPendingNavigationGuard() {
        const guardDirty = navigationGuardRef.current && navigationGuardRef.current();
        const visibleEditorDirty = Boolean(document.querySelector('h1 .dirty-badge'));
        return guardDirty || visibleEditorDirty;
    }

    function go(name, params = {}) {
        if (hasPendingNavigationGuard()) {
            if (!window.confirm('You have unsaved changes. Continue and discard them?')) return;
        }
        window.location.hash = hashFor(name, params);
        setCurrentView(viewFromHash());
    }

    const setNavigationGuard = useCallback((guard) => {
        navigationGuardRef.current = guard;
    }, []);

    function clearWorkspace() {
        setProjects([]);
    }

    async function handleSessionExpired(message = 'Your session expired. Please log in again.') {
        setCurrentUser(null);
        setAuthStatus('unauthenticated');
        clearWorkspace();
        notify('error', message);
        go('login');
        try { await AuthApi.refreshCsrfToken(); } catch (_) {}
    }

    async function loadProjects(force = false) {
        if (!currentUser) return [];
        if (!force && projects.length > 0) return projects;
        try {
            const list = await AuthApi.listProjects();
            setProjects(list || []);
            return list || [];
        } catch (e) {
            if (e.status === 401) await handleSessionExpired();
            throw e;
        }
    }

    useEffect(() => {
        function onHash() { setCurrentView(viewFromHash()); }
        window.addEventListener('hashchange', onHash);
        return () => window.removeEventListener('hashchange', onHash);
    }, []);

    useEffect(() => {
        async function restoreSession() {
            try {
                await AuthApi.fetchCsrfToken();
                const user = await AuthApi.getCurrentUser();
                setCurrentUser(user);
                setAuthStatus('authenticated');
                if (['login', 'register'].includes(viewFromHash().name)) go('dashboard');
            } catch (e) {
                setCurrentUser(null);
                setAuthStatus('unauthenticated');
                if (e.status !== 401) notify('error', e.message || 'Could not initialize session.');
            }
        }
        restoreSession();
    }, []);

    async function logout() {
        if (hasPendingNavigationGuard()) {
            if (!window.confirm('You have unsaved changes. Continue and discard them?')) return;
        }
        try {
            await AuthApi.logoutUser();
        } catch (e) {
            if (e.status !== 401 && e.status !== 403) notify('error', e.message || 'Logout failed.');
        } finally {
            setCurrentUser(null);
            setAuthStatus('unauthenticated');
            clearWorkspace();
            go('generate');
            try { await AuthApi.refreshCsrfToken(); } catch (_) {}
            notify('success', 'Logged out.');
        }
    }

    function requireAuth(view) {
        if (authStatus === 'loading') return <div className="loading-line">Restoring session...</div>;
        if (!currentUser) return <LoginView onLogin={(user, message) => { setCurrentUser(user); setAuthStatus('authenticated'); notify('success', message); }} go={go} />;
        return view;
    }

    let content;
    if (currentView.name === 'share') {
        content = <PublicShareView token={currentView.token} />;
    } else if (currentView.name === 'invitation') {
        content = <InvitationLandingView token={currentView.token} currentUser={currentUser} go={go} notify={notify} onAccepted={() => loadProjects(true)} />;
    } else if (currentView.name === 'login') {
        content = <LoginView onLogin={(user, message) => { setCurrentUser(user); setAuthStatus('authenticated'); notify('success', message); }} go={go} />;
    } else if (currentView.name === 'register') {
        content = <RegisterView onLogin={(user, message) => { setCurrentUser(user); setAuthStatus('authenticated'); notify('success', message); }} go={go} />;
    } else if (currentView.name === 'dashboard') {
        content = requireAuth(<DashboardView projects={projects} loadProjects={loadProjects} go={go} notify={notify} />);
    } else if (currentView.name === 'repositories') {
        content = requireAuth(<RepositoriesView notify={notify} />);
    } else if (currentView.name === 'project') {
        content = requireAuth(<ProjectDetailsView projectId={currentView.projectId} go={go} notify={notify} loadProjects={loadProjects} />);
    } else if (currentView.name === 'diagram') {
        content = requireAuth(<DiagramEditorView diagramId={currentView.diagramId} projectId={currentView.projectId} go={go} notify={notify} loadProjects={loadProjects} setNavigationGuard={setNavigationGuard} />);
    } else {
        content = <GeneratorView currentUser={currentUser} projects={projects} loadProjects={loadProjects} go={go} notify={notify} />;
    }

    return (
        <>
            <nav className="top-nav">
                <div className="top-nav-inner">
                    <div className="brand">AI Diagram Generator</div>
                    <div className="nav-links" aria-label="Primary navigation">
                        <button className={`nav-button ${currentView.name === 'generate' ? 'active' : ''}`} onClick={() => go('generate')}>Generate</button>
                        {currentUser ? (
                            <>
                                <button className={`nav-button ${currentView.name === 'dashboard' ? 'active' : ''}`} onClick={() => go('dashboard')}>Dashboard</button>
                                <button className={`nav-button ${currentView.name === 'repositories' ? 'active' : ''}`} onClick={() => go('repositories')}>Repositories</button>
                                <span className="nav-user">{userLabel(currentUser)}</span>
                                <button className="nav-button" onClick={logout}>Logout</button>
                            </>
                        ) : (
                            <>
                                <button className={`nav-button ${currentView.name === 'login' ? 'active' : ''}`} onClick={() => go('login')}>Login</button>
                                <button className={`nav-button ${currentView.name === 'register' ? 'active' : ''}`} onClick={() => go('register')}>Register</button>
                            </>
                        )}
                    </div>
                </div>
            </nav>
            <main className="app-shell">
                <Notice notice={notice} onClear={() => setNotice(null)} />
                {content}
            </main>
        </>
    );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
    } catch (error) {
        showStartupError(error);
    }
})();
