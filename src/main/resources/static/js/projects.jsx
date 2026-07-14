(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect } = React;
    const AuthApi = namespace.modules.api;
    const { formatDate, Modal, ProjectForm } = namespace.modules.shared;

    function DashboardView({ projects, loadProjects, go, notify }) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [showCreate, setShowCreate] = useState(false);
    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);

    async function refresh() {
        setLoading(true);
        setError(null);
        try {
            await loadProjects(true);
        } catch (e) {
            setError(e.message || 'Projects could not be loaded.');
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => { refresh(); }, []);

    async function createProject(data) {
        const project = await AuthApi.createProject(data);
        setShowCreate(false);
        await loadProjects(true);
        notify('success', 'Project created.');
        go('project', { projectId: project.id });
    }

    async function updateProject(data) {
        await AuthApi.updateProject(editing.id, data);
        setEditing(null);
        await loadProjects(true);
        notify('success', 'Project updated.');
    }

    async function deleteProject(project) {
        try {
            await AuthApi.deleteProject(project.id);
            setDeleting(null);
            await loadProjects(true);
            notify('success', 'Project deleted.');
        } catch (e) {
            notify('error', e.status === 409 ? 'Project contains diagrams. Delete its diagrams first.' : (e.message || 'Project could not be deleted.'));
        }
    }

    return (
        <div className="stack">
            <div className="toolbar">
                <div>
                    <h1>Dashboard</h1>
                    <p className="subtitle">Your projects and saved diagrams.</p>
                </div>
                <button type="button" onClick={() => setShowCreate(true)}>Create Project</button>
            </div>
            {loading && <div className="loading-line">Loading projects...</div>}
            {error && <div className="notice error">{error} <button className="link-button" onClick={refresh}>Retry</button></div>}
            {!loading && !error && projects.length === 0 && (
                <div className="empty-state">
                    <h2>No projects yet</h2>
                    <p className="muted">Create a project to organize saved diagrams.</p>
                    <button type="button" style={{marginTop: '1rem'}} onClick={() => setShowCreate(true)}>Create Project</button>
                </div>
            )}
            <div className="project-grid">
                {projects.map(project => (
                    <article className="project-card" key={project.id}>
                        <div>
                            <h3>{project.name}</h3>
                            <p className="muted">{project.description || 'No description'}</p>
                        </div>
                        <div className="card-meta">
                            <span>{project.diagramCount} diagrams</span>
                            <span>Updated {formatDate(project.updatedAt)}</span>
                        </div>
                        <div className="card-actions">
                            <button type="button" onClick={() => go('project', { projectId: project.id })}>Open</button>
                            <button type="button" className="btn-secondary" onClick={() => setEditing(project)}>Edit</button>
                            <button type="button" className="btn-danger" onClick={() => setDeleting(project)}>Delete</button>
                        </div>
                    </article>
                ))}
            </div>
            {showCreate && (
                <Modal title="Create project" onClose={() => setShowCreate(false)}>
                    <ProjectForm submitLabel="Create Project" onSubmit={createProject} onCancel={() => setShowCreate(false)} />
                </Modal>
            )}
            {editing && (
                <Modal title="Edit project" onClose={() => setEditing(null)}>
                    <ProjectForm initialProject={editing} submitLabel="Save Changes" onSubmit={updateProject} onCancel={() => setEditing(null)} />
                </Modal>
            )}
            {deleting && (
                <Modal title="Delete project" onClose={() => setDeleting(null)}>
                    <div className="stack">
                        <p className="muted">Only empty projects can be deleted. Projects with diagrams return a conflict and must be cleaned up first.</p>
                        <div className="toolbar-actions">
                            <button className="btn-danger" onClick={() => deleteProject(deleting)}>Delete Project</button>
                            <button className="btn-secondary" onClick={() => setDeleting(null)}>Cancel</button>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}

function ProjectDetailsView({ projectId, go, notify, loadProjects }) {
    const [project, setProject] = useState(null);
    const [diagrams, setDiagrams] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [editing, setEditing] = useState(false);
    const [deletingProject, setDeletingProject] = useState(false);
    const [deletingDiagram, setDeletingDiagram] = useState(null);

    async function refresh() {
        setLoading(true);
        setError(null);
        try {
            const [projectData, diagramData] = await Promise.all([
                AuthApi.getProject(projectId),
                AuthApi.listProjectDiagrams(projectId),
            ]);
            setProject(projectData);
            setDiagrams(diagramData || []);
        } catch (e) {
            setError(e.message || 'Project could not be loaded.');
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => { refresh(); }, [projectId]);

    async function updateProject(data) {
        const updated = await AuthApi.updateProject(projectId, data);
        setProject(updated);
        setEditing(false);
        await loadProjects(true);
        notify('success', 'Project updated.');
    }

    async function deleteProject() {
        try {
            await AuthApi.deleteProject(projectId);
            await loadProjects(true);
            notify('success', 'Project deleted.');
            go('dashboard');
        } catch (e) {
            notify('error', e.status === 409 ? 'Project contains diagrams. Delete its diagrams first.' : (e.message || 'Project could not be deleted.'));
        }
    }

    async function deleteDiagram(diagram) {
        try {
            await AuthApi.deleteDiagram(diagram.id);
            setDeletingDiagram(null);
            await refresh();
            await loadProjects(true);
            notify('success', 'Diagram deleted.');
        } catch (e) {
            notify('error', e.status === 404 ? 'Diagram was already unavailable.' : (e.message || 'Diagram could not be deleted.'));
            setDeletingDiagram(null);
        }
    }

    if (loading && !project) return <div className="loading-line">Loading project...</div>;
    if (error) return <div className="notice error">{error} <button className="link-button" onClick={refresh}>Retry</button></div>;
    if (!project) return null;

    return (
        <div className="stack">
            <button className="btn-secondary" type="button" onClick={() => go('dashboard')}>Back to dashboard</button>
            <div className="toolbar">
                <div>
                    <h1>{project.name}</h1>
                    <p className="subtitle">{project.description || 'No description'}</p>
                    <div className="card-meta">
                        <span>{project.diagramCount} diagrams</span>
                        <span>Updated {formatDate(project.updatedAt)}</span>
                    </div>
                </div>
                <div className="toolbar-actions">
                    <button className="btn-secondary" onClick={() => setEditing(true)}>Edit Project</button>
                    <button className="btn-danger" onClick={() => setDeletingProject(true)}>Delete Project</button>
                    <button onClick={() => go('generate')}>Generate Diagram</button>
                </div>
            </div>
            <section className="panel">
                <div className="toolbar">
                    <h2>Saved diagrams</h2>
                    <button className="btn-secondary" onClick={refresh}>Refresh</button>
                </div>
                {diagrams.length === 0 ? (
                    <div className="empty-state">
                        <h3>No diagrams saved in this project</h3>
                        <p className="muted">Generate a PlantUML diagram and save it here.</p>
                        <button style={{marginTop: '1rem'}} onClick={() => go('generate')}>Generate Diagram</button>
                    </div>
                ) : (
                    <div className="diagram-list">
                        {diagrams.map(diagram => (
                            <article className="diagram-card" key={diagram.id}>
                                <div>
                                    <h3>{diagram.name}</h3>
                                    <p className="muted">{diagram.description || 'No description'}</p>
                                </div>
                                <div className="card-meta">
                                    <span>{diagram.diagramType}</span>
                                    <span>{diagram.sourceFormat}</span>
                                    <span>v{diagram.currentVersionNumber}</span>
                                    <span>{diagram.modelUsed || 'No model'}</span>
                                    <span>Updated {formatDate(diagram.updatedAt)}</span>
                                </div>
                                <div className="card-actions">
                                    <button onClick={() => go('diagram', { diagramId: diagram.id, projectId })}>Open</button>
                                    <button className="btn-danger" onClick={() => setDeletingDiagram(diagram)}>Delete</button>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>
            {editing && (
                <Modal title="Edit project" onClose={() => setEditing(false)}>
                    <ProjectForm initialProject={project} submitLabel="Save Changes" onSubmit={updateProject} onCancel={() => setEditing(false)} />
                </Modal>
            )}
            {deletingProject && (
                <Modal title="Delete project" onClose={() => setDeletingProject(false)}>
                    <div className="stack">
                        <p className="muted">Only empty projects can be deleted. If this project contains diagrams, delete those diagrams first.</p>
                        <div className="toolbar-actions">
                            <button className="btn-danger" onClick={deleteProject}>Delete Project</button>
                            <button className="btn-secondary" onClick={() => setDeletingProject(false)}>Cancel</button>
                        </div>
                    </div>
                </Modal>
            )}
            {deletingDiagram && (
                <Modal title="Delete diagram" onClose={() => setDeletingDiagram(null)}>
                    <div className="stack">
                        <p className="muted">Delete "{deletingDiagram.name}" from this project?</p>
                        <div className="toolbar-actions">
                            <button className="btn-danger" onClick={() => deleteDiagram(deletingDiagram)}>Delete Diagram</button>
                            <button className="btn-secondary" onClick={() => setDeletingDiagram(null)}>Cancel</button>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}

    function SaveDiagramDialog({ result, originalPrompt, fallbackType, projects, loadProjects, onClose, onSaved, go, notify }) {
    const [projectId, setProjectId] = useState(projects[0]?.id || '');
    const [name, setName] = useState(result?.name || `${result?.diagramType || fallbackType || 'Generated'} diagram`);
    const [description, setDescription] = useState('');
    const [newProjectName, setNewProjectName] = useState('');
    const [newProjectDescription, setNewProjectDescription] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        loadProjects(false).then(list => {
            if (!projectId && list && list[0]) setProjectId(list[0].id);
        }).catch(e => setError(e.message || 'Projects could not be loaded.'));
    }, []);

    async function createProjectInline() {
        if (!newProjectName.trim()) {
            setError('New project name is required.');
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const project = await AuthApi.createProject({ name: newProjectName.trim(), description: newProjectDescription.trim() });
            const list = await loadProjects(true);
            setProjectId(project.id);
            setNewProjectName('');
            setNewProjectDescription('');
            notify('success', 'Project created.');
        } catch (e) {
            setError(e.message || 'Project could not be created.');
        } finally {
            setLoading(false);
        }
    }

    async function save(e) {
        e.preventDefault();
        if (!projectId) {
            setError('Choose or create a project.');
            return;
        }
        if (!name.trim()) {
            setError('Diagram name is required.');
            return;
        }
        if (result.mermaidCode && !result.plantUmlCode) {
            setError('Workspace saving currently supports PlantUML only.');
            return;
        }
        setLoading(true);
        setError(null);
        try {
            let saved;
            if (result.id) {
                saved = await AuthApi.attachDiagram(projectId, result.id, { name: name.trim(), description: description.trim() });
            } else {
                saved = await AuthApi.saveDiagram(projectId, {
                    name: name.trim(),
                    description: description.trim(),
                    originalPrompt: originalPrompt || '',
                    diagramType: mapTypeToEnum(result.diagramType || fallbackType || 'CLASS'),
                    sourceFormat: 'PLANTUML',
                    sourceCode: result.plantUmlCode,
                    modelUsed: result.modelUsed || result.generationMode || 'frontend',
                });
            }
            await loadProjects(true);
            onSaved(saved);
        } catch (e) {
            setError(e.message || 'Diagram could not be saved.');
        } finally {
            setLoading(false);
        }
    }

    return (
        <Modal title="Save diagram to project" onClose={onClose}>
            <form className="form-grid" onSubmit={save}>
                {error && <div className="notice error">{error}</div>}
                {result.mermaidCode && !result.plantUmlCode && (
                    <div className="notice info">Workspace saving currently supports PlantUML only.</div>
                )}
                <div>
                    <label htmlFor="save-project">Project</label>
                    <select id="save-project" value={projectId} onChange={e => setProjectId(e.target.value)}>
                        <option value="">Choose project</option>
                        {projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
                    </select>
                </div>
                <div>
                    <label htmlFor="diagram-name">Diagram name</label>
                    <input id="diagram-name" type="text" value={name} onChange={e => setName(e.target.value)} />
                </div>
                <div>
                    <label htmlFor="diagram-description">Description</label>
                    <textarea id="diagram-description" value={description} onChange={e => setDescription(e.target.value)} style={{height: '80px'}} />
                </div>
                <div className="toolbar-actions">
                    <button type="submit" disabled={loading || (result.mermaidCode && !result.plantUmlCode)}>{loading ? 'Saving...' : 'Save Diagram'}</button>
                    <button type="button" className="btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
                </div>
            </form>
            <div className="save-panel">
                <h3>Create project during save</h3>
                <div className="inline-form">
                    <div>
                        <label htmlFor="inline-project-name">Project name</label>
                        <input id="inline-project-name" type="text" value={newProjectName} onChange={e => setNewProjectName(e.target.value)} />
                    </div>
                    <div>
                        <label htmlFor="inline-project-description">Description</label>
                        <input id="inline-project-description" type="text" value={newProjectDescription} onChange={e => setNewProjectDescription(e.target.value)} />
                    </div>
                    <button type="button" className="btn-secondary" onClick={createProjectInline} disabled={loading}>Create and Select Project</button>
                </div>
            </div>
        </Modal>
    );
}

function cleanPdfText(raw) {
    if (!raw) return '';
    return raw
        .replace(/\r\n|\r/g, '\n')
        .replace(/[ \t]+/g, ' ')
        .replace(/\n{2,}/g, '\n\n')
        .replace(/([^\n])\n([^\n])/g, '$1 $2')
        .trim();
}

function mapTypeToEnum(displayType) {
    const mapping = {
        'Class Diagram': 'CLASS',
        'Sequence Diagram': 'SEQUENCE',
        'ER Diagram': 'ER',
        'Entity Relationship Diagram': 'ER',
        'Component Diagram': 'COMPONENT',
        'Deployment Diagram': 'DEPLOYMENT',
        'Use Case Diagram': 'USE_CASE',
        'Object Diagram': 'OBJECT',
        'Activity Diagram': 'ACTIVITY',
        'State Diagram': 'STATE',
        'Collaboration Diagram': 'COLLABORATION',
        'Microservices Architecture': 'MICROSERVICES_ARCHITECTURE',
        'Microservices Diagram': 'MICROSERVICES_ARCHITECTURE',
    };
    if (!displayType) return 'CLASS';
    return mapping[displayType] || String(displayType).toUpperCase().replace(/\s+/g, '_').replace('_DIAGRAM', '');
}

    namespace.modules.projects = {
        DashboardView,
        ProjectDetailsView,
        SaveDiagramDialog
    };
})();
