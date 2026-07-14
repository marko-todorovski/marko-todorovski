(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect } = React;

    function formatDate(value) {
    if (!value) return 'Not available';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Not available';
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function userLabel(user) {
    return user?.firstName || user?.email || 'Account';
}

    function Notice({ notice, onClear }) {
    if (!notice) return null;
    return (
        <div className={`notice ${notice.type || 'info'}`} role="status" aria-live="polite">
            <span>{notice.message}</span>
            {onClear && <button className="link-button" style={{marginLeft: '0.75rem'}} onClick={onClear}>Dismiss</button>}
        </div>
    );
}

function Modal({ title, children, onClose }) {
    useEffect(() => {
        function onKey(e) {
            if (e.key === 'Escape') onClose();
        }
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);
    return (
        <div className="modal-backdrop" role="presentation">
            <div className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
                <div className="modal-header">
                    <h2 id="modal-title">{title}</h2>
                    <button className="modal-close" type="button" onClick={onClose} aria-label="Close dialog">Close</button>
                </div>
                {children}
            </div>
        </div>
    );
}

function ProjectForm({ initialProject, submitLabel, onSubmit, onCancel }) {
    const [name, setName] = useState(initialProject?.name || '');
    const [description, setDescription] = useState(initialProject?.description || '');
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState(null);

    async function submit(e) {
        e.preventDefault();
        const nextErrors = {};
        if (!name.trim()) nextErrors.name = 'Project name is required.';
        if (name.length > 120) nextErrors.name = 'Project name is too long.';
        if (description.length > 500) nextErrors.description = 'Description is too long.';
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length) return;
        setSubmitting(true);
        setFormError(null);
        try {
            await onSubmit({ name: name.trim(), description: description.trim() });
        } catch (e) {
            setFormError(e.message || 'Project could not be saved.');
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <form className="form-grid" onSubmit={submit}>
            {formError && <div className="notice error">{formError}</div>}
            <div>
                <label htmlFor="project-name">Project name</label>
                <input id="project-name" type="text" value={name} onChange={e => setName(e.target.value)} autoFocus />
                {errors.name && <div className="field-error">{errors.name}</div>}
            </div>
            <div>
                <label htmlFor="project-description">Description</label>
                <textarea id="project-description" value={description} onChange={e => setDescription(e.target.value)} style={{height: '90px'}} />
                {errors.description && <div className="field-error">{errors.description}</div>}
            </div>
            <div className="toolbar-actions">
                <button type="submit" disabled={submitting}>{submitting ? 'Saving...' : submitLabel}</button>
                <button type="button" className="btn-secondary" onClick={onCancel} disabled={submitting}>Cancel</button>
            </div>
        </form>
    );
}

    namespace.modules.shared = {
        formatDate,
        userLabel,
        Notice,
        Modal,
        ProjectForm
    };
})();
