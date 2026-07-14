(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect, useRef } = React;
    const AuthApi = namespace.modules.api;
    const { formatDate, Modal, ProjectForm } = namespace.modules.shared;
    const { AiAssistantPanel } = namespace.modules.aiAssistant;

    function DiagramEditorView({ diagramId, projectId, go, notify, loadProjects, setNavigationGuard }) {
    const [diagram, setDiagram] = useState(null);
    const [versions, setVersions] = useState([]);
    const [editorSource, setEditorSource] = useState('');
    const [originalSource, setOriginalSource] = useState('');
    const [editorPrompt, setEditorPrompt] = useState('');
    const [originalPrompt, setOriginalPrompt] = useState('');
    const [previewUrl, setPreviewUrl] = useState(null);
    const [previewStatus, setPreviewStatus] = useState('idle');
    const [previewMessage, setPreviewMessage] = useState('');
    const [saveStatus, setSaveStatus] = useState('idle');
    const [saveMessage, setSaveMessage] = useState('');
    const [selectedVersion, setSelectedVersion] = useState(null);
    const [selectedVersionLoading, setSelectedVersionLoading] = useState(false);
    const [editingMetadata, setEditingMetadata] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [confirmDelete, setConfirmDelete] = useState(false);
    const [restoreTarget, setRestoreTarget] = useState(null);
    const [restoreLoading, setRestoreLoading] = useState(false);
    const previewUrlRef = useRef(null);
    const previewRequestRef = useRef(0);

    const isPlantUml = diagram?.sourceFormat === 'PLANTUML';
    const dirty = editorSource !== originalSource || editorPrompt !== originalPrompt;

    function discardMessage() {
        return 'You have unsaved changes. Continue and discard them?';
    }

    function confirmDiscardIfDirty() {
        return !dirty || window.confirm(discardMessage());
    }

    function revokePreviewUrl() {
        if (previewUrlRef.current) {
            URL.revokeObjectURL(previewUrlRef.current);
            previewUrlRef.current = null;
        }
    }

    async function refresh() {
        setLoading(true);
        setError(null);
        try {
            const loaded = await AuthApi.getDiagram(diagramId);
            setDiagram(loaded);
            const source = loaded.currentSourceCode || '';
            const prompt = loaded.originalPrompt || '';
            setEditorSource(source);
            setOriginalSource(source);
            setEditorPrompt(prompt);
            setOriginalPrompt(prompt);
            await refreshVersions();
        } catch (e) {
            setError(e.message || 'Diagram could not be loaded.');
        } finally {
            setLoading(false);
        }
    }

    async function refreshVersions() {
        setVersions(await AuthApi.listVersions(diagramId));
    }

    useEffect(() => { refresh(); }, [diagramId]);

    useEffect(() => {
        setNavigationGuard(() => dirty);
        return () => setNavigationGuard(null);
    }, [dirty, setNavigationGuard]);

    useEffect(() => {
        function beforeUnload(e) {
            if (!dirty) return;
            e.preventDefault();
            e.returnValue = '';
        }
        window.addEventListener('beforeunload', beforeUnload);
        return () => window.removeEventListener('beforeunload', beforeUnload);
    }, [dirty]);

    useEffect(() => {
        if (!isPlantUml) {
            setPreviewStatus('unsupported');
            setPreviewMessage('Live preview is available for PlantUML diagrams only.');
            return;
        }
        if (!editorSource.trim()) {
            revokePreviewUrl();
            setPreviewUrl(null);
            setPreviewStatus('idle');
            setPreviewMessage('Enter PlantUML source to preview.');
            return;
        }
        setPreviewStatus(previewUrl ? 'stale' : 'rendering');
        setPreviewMessage(previewUrl ? 'Source changed. Refreshing preview...' : 'Rendering preview...');
        const timer = window.setTimeout(() => renderPreview(editorSource), 650);
        return () => window.clearTimeout(timer);
    }, [editorSource, isPlantUml]);

    useEffect(() => () => revokePreviewUrl(), []);

    async function renderPreview(source = editorSource) {
        if (!isPlantUml || !source.trim()) return;
        const requestId = previewRequestRef.current + 1;
        previewRequestRef.current = requestId;
        setPreviewStatus('rendering');
        setPreviewMessage('Rendering preview...');
        try {
            const blob = await AuthApi.previewDiagram(source);
            if (requestId !== previewRequestRef.current) return;
            const nextUrl = URL.createObjectURL(blob);
            revokePreviewUrl();
            previewUrlRef.current = nextUrl;
            setPreviewUrl(nextUrl);
            setPreviewStatus('ready');
            setPreviewMessage('Preview updated.');
        } catch (e) {
            if (requestId !== previewRequestRef.current) return;
            setPreviewStatus('error');
            setPreviewMessage(e.status === 401 ? 'Your session expired. Log in again to preview.' : (e.message || 'Preview could not be rendered.'));
        }
    }

    async function deleteDiagram() {
        if (!confirmDiscardIfDirty()) return;
        try {
            await AuthApi.deleteDiagram(diagramId);
            revokePreviewUrl();
            await loadProjects(true);
            notify('success', 'Diagram deleted.');
            go(projectId ? 'project' : 'dashboard', projectId ? { projectId } : {});
        } catch (e) {
            notify('error', e.status === 404 ? 'Diagram was already unavailable.' : (e.message || 'Diagram could not be deleted.'));
        }
    }

    async function saveVersion() {
        if (!isPlantUml) return;
        if (!editorSource.trim()) {
            setSaveStatus('error');
            setSaveMessage('Source code is required.');
            return;
        }
        setSaveStatus('saving');
        setSaveMessage('');
        try {
            const saved = await AuthApi.createVersion(diagramId, {
                prompt: editorPrompt,
                sourceCode: editorSource,
                sourceFormat: 'PLANTUML',
                changeType: 'EDITED',
                modelUsed: 'manual-editor',
            });
            if (!saved) {
                setSaveStatus('idle');
                setSaveMessage('No changes to save.');
                return;
            }
            const updatedDiagram = await AuthApi.getDiagram(diagramId);
            setDiagram(updatedDiagram);
            setOriginalSource(editorSource);
            setOriginalPrompt(editorPrompt);
            await refreshVersions();
            setSaveStatus('saved');
            setSaveMessage(`Saved version ${saved.versionNumber}.`);
            notify('success', `Saved version ${saved.versionNumber}.`);
        } catch (e) {
            setSaveStatus('error');
            setSaveMessage(e.status === 409 ? 'Version could not be saved. Refresh and try again.' : (e.message || 'Version could not be saved.'));
        }
    }

    async function saveAiVersion(proposal, instruction) {
        if (!proposal?.sourceCode) return;
        setSaveStatus('saving');
        setSaveMessage('');
        try {
            const saved = await AuthApi.createVersion(diagramId, {
                prompt: instruction ? `AI instruction: ${instruction}` : proposal.summary,
                sourceCode: proposal.sourceCode,
                sourceFormat: 'PLANTUML',
                changeType: 'AI_MODIFIED',
                modelUsed: proposal.modelUsed || 'ai-assistant',
            });
            if (!saved) {
                setSaveStatus('idle');
                setSaveMessage('No changes to save.');
                return;
            }
            const updatedDiagram = await AuthApi.getDiagram(diagramId);
            const source = updatedDiagram.currentSourceCode || '';
            const prompt = updatedDiagram.originalPrompt || '';
            setDiagram(updatedDiagram);
            setEditorSource(source);
            setOriginalSource(source);
            setEditorPrompt(prompt);
            setOriginalPrompt(prompt);
            await refreshVersions();
            await renderPreview(source);
            setSaveStatus('saved');
            setSaveMessage(`Saved AI version ${saved.versionNumber}.`);
            notify('success', `Saved AI version ${saved.versionNumber}.`);
        } catch (e) {
            setSaveStatus('error');
            setSaveMessage(e.message || 'AI version could not be saved.');
        }
    }

    function resetChanges() {
        if (!dirty || window.confirm('Reset unsaved source and prompt changes?')) {
            setEditorSource(originalSource);
            setEditorPrompt(originalPrompt);
            setSaveMessage('');
        }
    }

    function handleSourceKeyDown(e) {
        if (e.key !== 'Tab') return;
        e.preventDefault();
        const el = e.currentTarget;
        const start = el.selectionStart;
        const end = el.selectionEnd;
        const next = editorSource.slice(0, start) + '    ' + editorSource.slice(end);
        setEditorSource(next);
        window.requestAnimationFrame(() => {
            el.selectionStart = el.selectionEnd = start + 4;
        });
    }

    async function openVersion(versionNumber) {
        setSelectedVersionLoading(true);
        try {
            setSelectedVersion(await AuthApi.getVersion(diagramId, versionNumber));
        } catch (e) {
            notify('error', e.message || 'Version could not be loaded.');
        } finally {
            setSelectedVersionLoading(false);
        }
    }

    function loadVersionIntoEditor(version) {
        if (!confirmDiscardIfDirty()) return;
        setEditorSource(version.sourceCode || '');
        setEditorPrompt(version.prompt || '');
        setSelectedVersion(null);
        notify('info', 'Version loaded into editor. Save to create new version.');
    }

    async function restoreVersion(versionNumber) {
        if (!confirmDiscardIfDirty()) return;
        setRestoreLoading(true);
        try {
            const restored = await AuthApi.restoreVersion(diagramId, versionNumber);
            const updatedDiagram = await AuthApi.getDiagram(diagramId);
            const source = updatedDiagram.currentSourceCode || '';
            const prompt = updatedDiagram.originalPrompt || '';
            setDiagram(updatedDiagram);
            setEditorSource(source);
            setOriginalSource(source);
            setEditorPrompt(prompt);
            setOriginalPrompt(prompt);
            await refreshVersions();
            setSelectedVersion(null);
            setRestoreTarget(null);
            notify('success', `Restored as version ${restored.versionNumber}.`);
        } catch (e) {
            notify('error', e.message || 'Version could not be restored.');
        } finally {
            setRestoreLoading(false);
        }
    }

    async function updateMetadata(data) {
        try {
            const updated = await AuthApi.updateDiagramMetadata(diagramId, data);
            setDiagram(updated);
            setEditingMetadata(false);
            await loadProjects(true);
            notify('success', 'Diagram metadata updated.');
        } catch (e) {
            notify('error', e.message || 'Metadata could not be updated.');
        }
    }

    function backToProject() {
        go(diagram?.projectId ? 'project' : 'dashboard', diagram?.projectId ? { projectId: diagram.projectId } : {});
    }

    if (loading && !diagram) return <div className="loading-line">Loading diagram...</div>;
    if (error) return <div className="notice error">{error} <button className="link-button" onClick={refresh}>Retry</button></div>;
    if (!diagram) return null;

    return (
        <div className="stack">
            <button className="btn-secondary" onClick={backToProject}>
                Back to project
            </button>
            <div className="toolbar">
                <div>
                    <h1>{diagram.name} {dirty && <span className="dirty-badge">Unsaved</span>}</h1>
                    <p className="subtitle">{diagram.description || 'No description'}</p>
                </div>
                <div className="toolbar-actions">
                    <button className="btn-secondary" onClick={() => setEditingMetadata(true)}>Edit Metadata</button>
                    <button className="btn-danger" onClick={() => setConfirmDelete(true)}>Delete Diagram</button>
                </div>
            </div>
            <section className="panel stack">
                <div className="details-list">
                    <div className="detail-item"><span className="detail-label">Type</span><span className="detail-value">{diagram.diagramType}</span></div>
                    <div className="detail-item"><span className="detail-label">Format</span><span className="detail-value">{diagram.sourceFormat}</span></div>
                    <div className="detail-item"><span className="detail-label">Version</span><span className="detail-value">v{diagram.currentVersionNumber}</span></div>
                    <div className="detail-item"><span className="detail-label">Model</span><span className="detail-value">{diagram.modelUsed || 'No model'}</span></div>
                    <div className="detail-item"><span className="detail-label">Created</span><span className="detail-value">{formatDate(diagram.createdAt)}</span></div>
                    <div className="detail-item"><span className="detail-label">Updated</span><span className="detail-value">{formatDate(diagram.updatedAt)}</span></div>
                </div>
            </section>
            {!isPlantUml && (
                <div className="notice info">Editing and live preview are currently available for PlantUML diagrams only. This diagram remains read-only.</div>
            )}
            {isPlantUml ? (
                <div className="editor-layout">
                    <section className="panel stack">
                        <div>
                            <label htmlFor="version-prompt">Version prompt</label>
                            <textarea id="version-prompt" className="prompt-textarea" value={editorPrompt} onChange={e => setEditorPrompt(e.target.value)} spellCheck="false" />
                        </div>
                        <div>
                            <label htmlFor="source-editor">PlantUML source</label>
                            <textarea id="source-editor" className="editor-textarea" value={editorSource} onChange={e => setEditorSource(e.target.value)} onKeyDown={handleSourceKeyDown} spellCheck="false" />
                        </div>
                        <div className="toolbar-actions">
                            <button className="btn-success" onClick={saveVersion} disabled={saveStatus === 'saving' || !editorSource.trim()}>{saveStatus === 'saving' ? 'Saving...' : 'Save Version'}</button>
                            <button className="btn-secondary" onClick={resetChanges} disabled={!dirty || saveStatus === 'saving'}>Reset Changes</button>
                            <button className="btn-secondary" onClick={() => renderPreview(editorSource)} disabled={previewStatus === 'rendering'}>{previewStatus === 'rendering' ? 'Rendering...' : 'Refresh Preview'}</button>
                        </div>
                        <div className="editor-status" aria-live="polite">{saveMessage}</div>
                    </section>
                    <section className="panel stack">
                        <div className="toolbar" style={{marginBottom: 0}}>
                            <h2>Live preview</h2>
                            <span className={`badge ${previewStatus === 'error' ? 'error' : ''}`}>{previewStatus}</span>
                        </div>
                        <div className="preview-panel" aria-live="polite">
                            {previewUrl ? <img src={previewUrl} alt="Rendered PlantUML preview" /> : <div className="preview-empty">Preview will appear here.</div>}
                        </div>
                        {previewMessage && <div className={previewStatus === 'error' ? 'notice error' : 'editor-status'}>{previewMessage}</div>}
                    </section>
                </div>
            ) : (
                <section className="panel stack">
                    <h2>Current source</h2>
                    <div className="source-panel">
                        <pre>{diagram.currentSourceCode || 'No source code stored.'}</pre>
                    </div>
                </section>
            )}
            <AiAssistantPanel
                diagram={diagram}
                diagramId={diagramId}
                editorSource={editorSource}
                editorPrompt={editorPrompt}
                setEditorSource={setEditorSource}
                setEditorPrompt={setEditorPrompt}
                renderPreview={renderPreview}
                saveAiVersion={saveAiVersion}
                notify={notify}
            />
            <section className="panel stack">
                <div className="toolbar" style={{marginBottom: 0}}>
                    <h2>Version History</h2>
                    <button className="btn-secondary" onClick={refreshVersions}>Refresh</button>
                </div>
                {versions.length === 0 ? <p className="muted">No versions saved yet.</p> : (
                    <div className="history-list">
                        {versions.map(version => (
                            <article key={version.versionNumber} className={`history-item ${version.versionNumber === diagram.currentVersionNumber ? 'current' : ''}`}>
                                <div className="history-topline">
                                    <span className="history-version">Version {version.versionNumber}</span>
                                    {version.versionNumber === diagram.currentVersionNumber && <span className="dirty-badge">Current</span>}
                                </div>
                                <div className="history-meta">
                                    <span>{version.changeType}</span>
                                    <span>{version.sourceFormat}</span>
                                    <span>{version.modelUsed || 'No model'}</span>
                                    <span>{formatDate(version.createdAt)}</span>
                                </div>
                                <div className="toolbar-actions">
                                    <button className="btn-secondary" onClick={() => openVersion(version.versionNumber)} disabled={selectedVersionLoading}>View</button>
                                    {version.versionNumber !== diagram.currentVersionNumber && <button onClick={() => setRestoreTarget(version)}>Restore</button>}
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>
            {editingMetadata && (
                <Modal title="Edit diagram metadata" onClose={() => setEditingMetadata(false)}>
                    <ProjectForm initialProject={diagram} submitLabel="Save Metadata" onSubmit={updateMetadata} onCancel={() => setEditingMetadata(false)} />
                </Modal>
            )}
            {selectedVersion && (
                <Modal title={`Version ${selectedVersion.versionNumber}`} onClose={() => setSelectedVersion(null)}>
                    <div className="stack">
                        <div className="details-list">
                            <div className="detail-item"><span className="detail-label">Change</span><span className="detail-value">{selectedVersion.changeType}</span></div>
                            <div className="detail-item"><span className="detail-label">Format</span><span className="detail-value">{selectedVersion.sourceFormat}</span></div>
                            <div className="detail-item"><span className="detail-label">Model</span><span className="detail-value">{selectedVersion.modelUsed || 'No model'}</span></div>
                            <div className="detail-item"><span className="detail-label">Created</span><span className="detail-value">{formatDate(selectedVersion.createdAt)}</span></div>
                        </div>
                        <div>
                            <span className="detail-label">Prompt</span>
                            <p className="muted">{selectedVersion.prompt || 'No prompt stored.'}</p>
                        </div>
                        <div>
                            <span className="detail-label">Source code</span>
                            <pre className="version-code">{selectedVersion.sourceCode}</pre>
                        </div>
                        <div className="toolbar-actions">
                            {selectedVersion.sourceFormat === 'PLANTUML' && <button onClick={() => loadVersionIntoEditor(selectedVersion)}>Load into Editor</button>}
                            {selectedVersion.versionNumber !== diagram.currentVersionNumber && <button className="btn-secondary" onClick={() => setRestoreTarget(selectedVersion)}>Restore This Version</button>}
                            <button className="btn-secondary" onClick={() => setSelectedVersion(null)}>Close</button>
                        </div>
                    </div>
                </Modal>
            )}
            {restoreTarget && (
                <Modal title={`Restore version ${restoreTarget.versionNumber}`} onClose={() => setRestoreTarget(null)}>
                    <div className="stack">
                        <p className="muted">Restore does not delete newer versions. It creates a new version from this historical source.</p>
                        <div className="toolbar-actions">
                            <button onClick={() => restoreVersion(restoreTarget.versionNumber)} disabled={restoreLoading}>{restoreLoading ? 'Restoring...' : 'Restore Version'}</button>
                            <button className="btn-secondary" onClick={() => setRestoreTarget(null)} disabled={restoreLoading}>Cancel</button>
                        </div>
                    </div>
                </Modal>
            )}
            {confirmDelete && (
                <Modal title="Delete diagram" onClose={() => setConfirmDelete(false)}>
                    <div className="stack">
                        <p className="muted">Delete "{diagram.name}"?</p>
                        <div className="toolbar-actions">
                            <button className="btn-danger" onClick={deleteDiagram}>Delete Diagram</button>
                            <button className="btn-secondary" onClick={() => setConfirmDelete(false)}>Cancel</button>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}

    namespace.modules.editor = {
        DiagramEditorView
    };
})();
