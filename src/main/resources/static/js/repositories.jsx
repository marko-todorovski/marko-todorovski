(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect } = React;
    const AuthApi = namespace.modules.api;
    const { formatDate, Modal } = namespace.modules.shared;

    function statusLabel(status) {
        if (status === 'READY') return 'Ready';
        if (status === 'SCANNING') return 'Scanning';
        if (status === 'FAILED') return 'Failed';
        return 'Pending';
    }

    function statusBadgeClass(status) {
        if (status === 'READY') return 'badge';
        if (status === 'FAILED') return 'badge error';
        return 'badge warning';
    }

    function RepositoriesView({ notify }) {
        const [repositories, setRepositories] = useState([]);
        const [loading, setLoading] = useState(false);
        const [error, setError] = useState(null);
        const [showAdd, setShowAdd] = useState(false);
        const [deleting, setDeleting] = useState(null);
        const [refreshingId, setRefreshingId] = useState(null);

        async function refresh() {
            setLoading(true);
            setError(null);
            try {
                const list = await AuthApi.listRepositories();
                setRepositories(list || []);
            } catch (e) {
                setError(e.message || 'Repositories could not be loaded.');
            } finally {
                setLoading(false);
            }
        }

        useEffect(() => { refresh(); }, []);

        async function deleteRepository(repository) {
            try {
                await AuthApi.deleteRepository(repository.id);
                setDeleting(null);
                await refresh();
                notify('success', 'Repository deleted.');
            } catch (e) {
                notify('error', e.message || 'Repository could not be deleted.');
            }
        }

        async function refreshScan(repository) {
            setRefreshingId(repository.id);
            try {
                await AuthApi.refreshRepositoryScan(repository.id);
                await refresh();
                notify('success', 'Scan refreshed.');
            } catch (e) {
                notify('error', e.message || 'Scan could not be refreshed.');
            } finally {
                setRefreshingId(null);
            }
        }

        return (
            <div className="stack">
                <div className="toolbar">
                    <div>
                        <h1>Repositories</h1>
                        <p className="subtitle">Import a repository to scan its structure.</p>
                    </div>
                    <button type="button" onClick={() => setShowAdd(true)}>Add Repository</button>
                </div>
                {loading && <div className="loading-line">Loading repositories...</div>}
                {error && <div className="notice error">{error} <button className="link-button" onClick={refresh}>Retry</button></div>}
                {!loading && !error && repositories.length === 0 && (
                    <div className="empty-state">
                        <h2>No repositories yet</h2>
                        <p className="muted">Import a public GitHub repository or upload a ZIP archive to get started.</p>
                        <button type="button" style={{marginTop: '1rem'}} onClick={() => setShowAdd(true)}>Add Repository</button>
                    </div>
                )}
                <div className="project-grid">
                    {repositories.map(repository => (
                        <article className="project-card" key={repository.id}>
                            <div>
                                <h3>{repository.name}</h3>
                                <p className="muted">{repository.sourceType === 'GITHUB_URL' ? repository.sourceUrl : repository.originalFilename}</p>
                            </div>
                            <div className="card-meta">
                                <span className={statusBadgeClass(repository.status)}>{statusLabel(repository.status)}</span>
                                <span>{repository.sourceType === 'GITHUB_URL' ? 'GitHub' : 'ZIP upload'}</span>
                                <span>Updated {formatDate(repository.updatedAt)}</span>
                            </div>
                            <div className="card-actions">
                                {repository.sourceType === 'GITHUB_URL' && (
                                    <button type="button" className="btn-secondary" disabled={refreshingId === repository.id} onClick={() => refreshScan(repository)}>
                                        {refreshingId === repository.id ? 'Refreshing...' : 'Refresh Scan'}
                                    </button>
                                )}
                                <button type="button" className="btn-danger" onClick={() => setDeleting(repository)}>Delete</button>
                            </div>
                        </article>
                    ))}
                </div>
                {showAdd && (
                    <AddRepositoryModal
                        onClose={() => setShowAdd(false)}
                        onAdded={async () => { setShowAdd(false); await refresh(); }}
                        notify={notify}
                    />
                )}
                {deleting && (
                    <Modal title="Delete repository" onClose={() => setDeleting(null)}>
                        <div className="stack">
                            <p className="muted">Delete "{deleting.name}" and its scan history?</p>
                            <div className="toolbar-actions">
                                <button className="btn-danger" onClick={() => deleteRepository(deleting)}>Delete Repository</button>
                                <button className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
                            </div>
                        </div>
                    </Modal>
                )}
            </div>
        );
    }

    function AddRepositoryModal({ onClose, onAdded, notify }) {
        const [mode, setMode] = useState('github');
        const [githubUrl, setGithubUrl] = useState('');
        const [file, setFile] = useState(null);
        const [name, setName] = useState('');
        const [loading, setLoading] = useState(false);
        const [error, setError] = useState(null);

        async function submitGithub(e) {
            e.preventDefault();
            if (!githubUrl.trim()) {
                setError('A GitHub repository URL is required.');
                return;
            }
            setLoading(true);
            setError(null);
            try {
                await AuthApi.importRepositoryFromGithub(githubUrl.trim());
                notify('success', 'Repository imported and scanned.');
                await onAdded();
            } catch (e) {
                setError(e.message || 'Repository could not be imported.');
            } finally {
                setLoading(false);
            }
        }

        async function submitZip(e) {
            e.preventDefault();
            if (!file) {
                setError('Choose a .zip file to upload.');
                return;
            }
            setLoading(true);
            setError(null);
            try {
                await AuthApi.importRepositoryFromZip(file, name.trim());
                notify('success', 'Repository uploaded and scanned.');
                await onAdded();
            } catch (e) {
                setError(e.message || 'Repository could not be uploaded.');
            } finally {
                setLoading(false);
            }
        }

        return (
            <Modal title="Add repository" onClose={onClose}>
                <div className="toolbar-actions" style={{marginBottom: '1rem'}}>
                    <button type="button" className={mode === 'github' ? '' : 'btn-secondary'} onClick={() => setMode('github')}>GitHub URL</button>
                    <button type="button" className={mode === 'zip' ? '' : 'btn-secondary'} onClick={() => setMode('zip')}>Upload ZIP</button>
                </div>
                {error && <div className="notice error">{error}</div>}
                {mode === 'github' ? (
                    <form className="form-grid" onSubmit={submitGithub}>
                        <div>
                            <label htmlFor="github-url">Public GitHub repository URL</label>
                            <input
                                id="github-url"
                                type="url"
                                placeholder="https://github.com/owner/repo"
                                value={githubUrl}
                                onChange={e => setGithubUrl(e.target.value)}
                                autoFocus
                            />
                        </div>
                        <div className="toolbar-actions">
                            <button type="submit" disabled={loading}>{loading ? 'Importing...' : 'Import Repository'}</button>
                            <button type="button" className="btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
                        </div>
                    </form>
                ) : (
                    <form className="form-grid" onSubmit={submitZip}>
                        <div>
                            <label htmlFor="zip-name">Name (optional)</label>
                            <input id="zip-name" type="text" value={name} onChange={e => setName(e.target.value)} />
                        </div>
                        <div>
                            <label htmlFor="zip-file">ZIP archive</label>
                            <input id="zip-file" type="file" accept=".zip" onChange={e => setFile(e.target.files[0] || null)} />
                        </div>
                        <div className="toolbar-actions">
                            <button type="submit" disabled={loading}>{loading ? 'Uploading...' : 'Upload Repository'}</button>
                            <button type="button" className="btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
                        </div>
                    </form>
                )}
            </Modal>
        );
    }

    namespace.modules.repositories = {
        RepositoriesView,
        statusLabel
    };
})();
