(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useEffect, useRef, useState } = React;
    const AuthApi = namespace.modules.api;
    const { formatDate, Modal } = namespace.modules.shared;

    function buildPublicUrl(token) {
        return `${window.location.origin}${window.location.pathname}#/share/${token}`;
    }

    function toDatetimeLocal(date) {
        const pad = value => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function defaultExpiresAt() {
        const date = new Date();
        date.setDate(date.getDate() + 7);
        return toDatetimeLocal(date);
    }

    function ShareDiagramModal({ diagram, versions, dirty, onClose, notify }) {
        const [versionNumber, setVersionNumber] = useState(String(diagram.currentVersionNumber || versions[0]?.versionNumber || ''));
        const [expirationMode, setExpirationMode] = useState('7d');
        const [customExpiresAt, setCustomExpiresAt] = useState(defaultExpiresAt());
        const [allowDownloads, setAllowDownloads] = useState(true);
        const [titleOverride, setTitleOverride] = useState('');
        const [descriptionOverride, setDescriptionOverride] = useState('');
        const [shares, setShares] = useState([]);
        const [createdShare, setCreatedShare] = useState(null);
        const [loading, setLoading] = useState(false);
        const [error, setError] = useState(null);
        const [copyStatus, setCopyStatus] = useState('');

        useEffect(() => { refreshShares(); }, [diagram.id]);

        async function refreshShares() {
            try {
                const result = await AuthApi.listShares(diagram.id);
                setShares(result?.shares || []);
            } catch (e) {
                setError(e.message || 'Share links could not be loaded.');
            }
        }

        function selectedExpirationIso() {
            if (expirationMode === 'never') return null;
            const date = new Date();
            if (expirationMode === '1d') date.setDate(date.getDate() + 1);
            if (expirationMode === '7d') date.setDate(date.getDate() + 7);
            if (expirationMode === '30d') date.setDate(date.getDate() + 30);
            if (expirationMode === 'custom') {
                const custom = new Date(customExpiresAt);
                return Number.isNaN(custom.getTime()) ? null : custom.toISOString();
            }
            return date.toISOString();
        }

        async function createShare(e) {
            e.preventDefault();
            setLoading(true);
            setError(null);
            setCopyStatus('');
            setCreatedShare(null);
            try {
                const created = await AuthApi.createShare(diagram.id, {
                    versionNumber: Number(versionNumber),
                    expiresAt: selectedExpirationIso(),
                    allowDownloads,
                    titleOverride: titleOverride.trim() || null,
                    descriptionOverride: descriptionOverride.trim() || null,
                });
                const publicUrl = buildPublicUrl(created.token);
                setCreatedShare({ ...created, publicUrl });
                await refreshShares();
                notify('success', 'Share link created.');
            } catch (e) {
                setError(e.message || 'Share link could not be created.');
            } finally {
                setLoading(false);
            }
        }

        async function copyLink() {
            if (!createdShare?.token) return;
            const url = buildPublicUrl(createdShare.token);
            try {
                await navigator.clipboard.writeText(url);
                setCopyStatus('Link copied.');
            } catch (_) {
                setCopyStatus('Copy failed. Select the link manually.');
            }
        }

        async function revokeShare(share) {
            setError(null);
            try {
                await AuthApi.revokeShare(diagram.id, share.shareId);
                await refreshShares();
                notify('success', 'Share link revoked.');
            } catch (e) {
                setError(e.message || 'Share link could not be revoked.');
            }
        }

        function closeAndClearToken() {
            setCreatedShare(null);
            onClose();
        }

        return (
            <Modal title="Share Diagram" onClose={closeAndClearToken}>
                <div className="stack share-modal">
                    {dirty && <div className="notice warning">Unsaved editor changes are not included. Save a version before sharing them.</div>}
                    <p className="muted">Share URLs are shown only when created. Create a replacement link if you lost it.</p>
                    {error && <div className="notice error">{error}</div>}
                    <form className="form-grid" onSubmit={createShare}>
                        <div>
                            <label htmlFor="share-version">Version to share</label>
                            <select id="share-version" value={versionNumber} onChange={e => setVersionNumber(e.target.value)}>
                                {versions.map(version => (
                                    <option key={version.versionNumber} value={version.versionNumber}>
                                        Version {version.versionNumber}{version.versionNumber === diagram.currentVersionNumber ? ' (current)' : ''}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label htmlFor="share-expiration">Expiration</label>
                            <select id="share-expiration" value={expirationMode} onChange={e => setExpirationMode(e.target.value)}>
                                <option value="7d">7 days</option>
                                <option value="1d">1 day</option>
                                <option value="30d">30 days</option>
                                <option value="never">Never</option>
                                <option value="custom">Custom date/time</option>
                            </select>
                        </div>
                        {expirationMode === 'custom' && (
                            <div>
                                <label htmlFor="share-custom-expiration">Custom expiration</label>
                                <input id="share-custom-expiration" type="datetime-local" value={customExpiresAt} onChange={e => setCustomExpiresAt(e.target.value)} />
                            </div>
                        )}
                        <label className="checkbox-row">
                            <input type="checkbox" checked={allowDownloads} onChange={e => setAllowDownloads(e.target.checked)} />
                            Allow PNG, SVG, and Draw.io downloads
                        </label>
                        <div>
                            <label htmlFor="share-title">Public title</label>
                            <input id="share-title" type="text" maxLength="150" value={titleOverride} onChange={e => setTitleOverride(e.target.value)} placeholder={diagram.name} />
                        </div>
                        <div>
                            <label htmlFor="share-description">Public description</label>
                            <textarea id="share-description" maxLength="1000" value={descriptionOverride} onChange={e => setDescriptionOverride(e.target.value)} placeholder={diagram.description || ''} />
                        </div>
                        <div className="toolbar-actions">
                            <button type="submit" disabled={loading || versions.length === 0}>{loading ? 'Creating...' : 'Create Share Link'}</button>
                            <button type="button" className="btn-secondary" onClick={closeAndClearToken}>Close</button>
                        </div>
                    </form>
                    {createdShare && (
                        <div className="share-created">
                            <div className="notice info">Save this link now. It cannot be displayed again.</div>
                            <label htmlFor="created-share-url">New public URL</label>
                            <input id="created-share-url" readOnly value={buildPublicUrl(createdShare.token)} onFocus={e => e.currentTarget.select()} />
                            <div className="toolbar-actions">
                                <button type="button" onClick={copyLink}>Copy Link</button>
                                <button type="button" className="btn-secondary" onClick={() => window.open(buildPublicUrl(createdShare.token), '_blank', 'noopener')}>Open Link</button>
                            </div>
                            {copyStatus && <div className="editor-status">{copyStatus}</div>}
                        </div>
                    )}
                    <section className="stack">
                        <div className="toolbar" style={{marginBottom: 0}}>
                            <h3>Existing Share Links</h3>
                            <button className="btn-secondary" type="button" onClick={refreshShares}>Refresh</button>
                        </div>
                        {shares.length === 0 ? <p className="muted">No share links yet.</p> : (
                            <div className="share-list">
                                {shares.map(share => (
                                    <article key={share.shareId} className="share-item">
                                        <div>
                                            <strong>Version {share.versionNumber}</strong>
                                            <span className={`badge ${share.status === 'ACTIVE' ? '' : 'error'}`}>{share.status}</span>
                                        </div>
                                        <div className="history-meta">
                                            <span>Created {formatDate(share.createdAt)}</span>
                                            <span>Expires {formatDate(share.expiresAt)}</span>
                                            <span>Accesses {share.accessCount}</span>
                                            <span>Last access {formatDate(share.lastAccessedAt)}</span>
                                            <span>{share.allowDownloads ? 'Downloads enabled' : 'Downloads disabled'}</span>
                                        </div>
                                        {share.status === 'ACTIVE' && <button className="btn-secondary" type="button" onClick={() => revokeShare(share)}>Revoke</button>}
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            </Modal>
        );
    }

    function PublicShareView({ token }) {
        const [share, setShare] = useState(null);
        const [previewUrl, setPreviewUrl] = useState(null);
        const [status, setStatus] = useState('loading');
        const [message, setMessage] = useState('');
        const previewUrlRef = useRef(null);

        function revokePreview() {
            if (previewUrlRef.current) {
                URL.revokeObjectURL(previewUrlRef.current);
                previewUrlRef.current = null;
            }
        }

        useEffect(() => {
            load();
            return () => revokePreview();
        }, [token]);

        async function load() {
            setStatus('loading');
            setMessage('');
            revokePreview();
            setPreviewUrl(null);
            try {
                const metadata = await AuthApi.getPublicShare(token);
                setShare(metadata);
                const blob = await AuthApi.previewPublicShare(token);
                const url = URL.createObjectURL(blob);
                previewUrlRef.current = url;
                setPreviewUrl(url);
                setStatus('ready');
            } catch (e) {
                setShare(null);
                setStatus('unavailable');
                setMessage('This shared diagram is unavailable or has expired.');
            }
        }

        async function download(format) {
            try {
                const blob = await AuthApi.downloadPublicShare(token, format);
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = `${(share?.title || 'shared-diagram').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-+|-+$)/g, '') || 'shared-diagram'}-v${share?.versionNumber || 'shared'}.${format === 'drawio' ? 'drawio' : format}`;
                document.body.appendChild(anchor);
                anchor.click();
                anchor.remove();
                window.setTimeout(() => URL.revokeObjectURL(url), 0);
            } catch (e) {
                setMessage(e.code === 'DOWNLOADS_DISABLED' ? 'Downloads are disabled for this shared diagram.' : (e.message || 'Download failed.'));
            }
        }

        if (status === 'loading') return <div className="loading-line">Loading shared diagram...</div>;
        if (status === 'unavailable') {
            return (
                <section className="public-share-shell">
                    <div className="notice error">{message || 'This shared diagram is unavailable or has expired.'}</div>
                </section>
            );
        }

        return (
            <section className="public-share-shell stack">
                <div>
                    <span className="eyebrow">Shared read-only diagram</span>
                    <h1>{share.title}</h1>
                    <p className="subtitle">{share.description || 'No public description provided.'}</p>
                </div>
                <div className="details-list">
                    <div className="detail-item"><span className="detail-label">Type</span><span className="detail-value">{share.diagramType}</span></div>
                    <div className="detail-item"><span className="detail-label">Format</span><span className="detail-value">{share.sourceFormat}</span></div>
                    <div className="detail-item"><span className="detail-label">Version</span><span className="detail-value">v{share.versionNumber}</span></div>
                    <div className="detail-item"><span className="detail-label">Shared</span><span className="detail-value">{formatDate(share.sharedAt)}</span></div>
                    <div className="detail-item"><span className="detail-label">Expires</span><span className="detail-value">{formatDate(share.expiresAt)}</span></div>
                </div>
                <div className="preview-panel public-preview">
                    {previewUrl ? <img src={previewUrl} alt="Shared diagram preview" /> : <div className="preview-empty">Preview unavailable.</div>}
                </div>
                {share.allowDownloads && (
                    <div className="toolbar-actions">
                        <button onClick={() => download('png')}>Download PNG</button>
                        <button className="btn-secondary" onClick={() => download('svg')}>Download SVG</button>
                        <button className="btn-secondary" onClick={() => download('drawio')}>Download Draw.io</button>
                    </div>
                )}
                {message && <div className="notice info">{message}</div>}
            </section>
        );
    }

    namespace.modules.sharing = {
        ShareDiagramModal,
        PublicShareView
    };
})();
