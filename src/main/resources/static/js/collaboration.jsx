(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect } = React;
    const AuthApi = namespace.modules.api;
    const { formatDate, Modal } = namespace.modules.shared;

    function roleLabel(role) {
        if (role === 'OWNER') return 'Owner';
        if (role === 'EDITOR') return 'Editor';
        return 'Viewer';
    }

    function canEdit(role) {
        return role === 'OWNER' || role === 'EDITOR';
    }

    function canManageMembers(role) {
        return role === 'OWNER';
    }

    function ProjectCollaborationPanel({ project, notify, onChanged, go }) {
        const [members, setMembers] = useState([]);
        const [invitations, setInvitations] = useState([]);
        const [showInvite, setShowInvite] = useState(false);
        const [loading, setLoading] = useState(false);
        const role = project?.currentUserRole || 'VIEWER';
        const owner = canManageMembers(role);

        async function refresh() {
            if (!project) return;
            setLoading(true);
            try {
                const memberList = await AuthApi.listProjectMembers(project.id);
                setMembers(memberList || []);
                if (owner) {
                    const inviteList = await AuthApi.listProjectInvitations(project.id);
                    setInvitations(inviteList || []);
                }
            } catch (e) {
                notify('error', e.message || 'Collaboration details could not be loaded.');
            } finally {
                setLoading(false);
            }
        }

        useEffect(() => { refresh(); }, [project?.id, role]);

        async function changeRole(member, nextRole) {
            try {
                await AuthApi.updateProjectMemberRole(project.id, member.id, nextRole);
                await refresh();
                notify('success', 'Member role updated.');
            } catch (e) {
                notify('error', e.message || 'Member role could not be updated.');
            }
        }

        async function removeMember(member) {
            if (!window.confirm(`Remove ${member.displayName} from this project?`)) return;
            try {
                await AuthApi.removeProjectMember(project.id, member.id);
                await refresh();
                notify('success', 'Member removed.');
            } catch (e) {
                notify('error', e.message || 'Member could not be removed.');
            }
        }

        async function leaveProject() {
            if (!window.confirm('Leave this project? You will lose access immediately.')) return;
            try {
                await AuthApi.leaveProject(project.id);
                notify('success', 'You left the project.');
                if (onChanged) await onChanged();
                go('dashboard');
            } catch (e) {
                notify('error', e.message || 'Could not leave project.');
            }
        }

        async function revokeInvitation(invitation) {
            try {
                await AuthApi.revokeProjectInvitation(project.id, invitation.id);
                await refresh();
                notify('success', 'Invitation revoked.');
            } catch (e) {
                notify('error', e.message || 'Invitation could not be revoked.');
            }
        }

        return (
            <section className="panel stack collaboration-panel">
                <div className="toolbar">
                    <div>
                        <h2>Members</h2>
                        <p className="muted">Your role: <span className="role-badge">{roleLabel(role)}</span></p>
                    </div>
                    <div className="toolbar-actions">
                        {owner && <button type="button" onClick={() => setShowInvite(true)}>Invite Member</button>}
                        {role !== 'OWNER' && <button type="button" className="btn-secondary" onClick={leaveProject}>Leave Project</button>}
                        <button type="button" className="btn-secondary" onClick={refresh} disabled={loading}>Refresh</button>
                    </div>
                </div>
                <div className="member-list">
                    {members.map(member => (
                        <article key={member.id} className="member-item">
                            <div>
                                <strong>{member.displayName}</strong>
                                <div className="muted">Joined {formatDate(member.joinedAt)}</div>
                            </div>
                            <span className="role-badge">{roleLabel(member.role)}</span>
                            {owner && member.role !== 'OWNER' && (
                                <div className="toolbar-actions">
                                    <select value={member.role} onChange={e => changeRole(member, e.target.value)} aria-label={`Change role for ${member.displayName}`}>
                                        <option value="EDITOR">Editor</option>
                                        <option value="VIEWER">Viewer</option>
                                    </select>
                                    <button type="button" className="btn-danger" onClick={() => removeMember(member)}>Remove</button>
                                </div>
                            )}
                        </article>
                    ))}
                </div>
                {owner && (
                    <div className="stack">
                        <h3>Invitations</h3>
                        {invitations.length === 0 ? <p className="muted">No invitations yet.</p> : (
                            <div className="member-list">
                                {invitations.map(invitation => (
                                    <article key={invitation.id} className="member-item">
                                        <div>
                                            <strong>{invitation.email}</strong>
                                            <div className="muted">Expires {formatDate(invitation.expiresAt)}</div>
                                        </div>
                                        <span className="role-badge">{roleLabel(invitation.role)}</span>
                                        <span className={`badge ${invitation.status === 'PENDING' ? '' : 'error'}`}>{invitation.expired ? 'EXPIRED' : invitation.status}</span>
                                        {invitation.status === 'PENDING' && !invitation.expired && <button type="button" className="btn-secondary" onClick={() => revokeInvitation(invitation)}>Revoke</button>}
                                    </article>
                                ))}
                            </div>
                        )}
                    </div>
                )}
                {showInvite && <InviteMemberModal project={project} onClose={() => setShowInvite(false)} onCreated={refresh} notify={notify} />}
            </section>
        );
    }

    function InviteMemberModal({ project, onClose, onCreated, notify }) {
        const [email, setEmail] = useState('');
        const [role, setRole] = useState('EDITOR');
        const [expiresInDays, setExpiresInDays] = useState(7);
        const [created, setCreated] = useState(null);
        const [error, setError] = useState(null);
        const [loading, setLoading] = useState(false);

        async function submit(e) {
            e.preventDefault();
            setError(null);
            setLoading(true);
            try {
                const result = await AuthApi.createProjectInvitation(project.id, { email, role, expiresInDays: Number(expiresInDays) });
                setCreated(result);
                await onCreated();
                notify('success', 'Invitation created. Copy the link now.');
            } catch (e) {
                setError(e.message || 'Invitation could not be created.');
            } finally {
                setLoading(false);
            }
        }

        const absoluteUrl = created ? `${window.location.origin}${window.location.pathname}${created.invitationUrl}` : '';

        return (
            <Modal title="Invite member" onClose={onClose}>
                <form className="form-grid invite-modal" onSubmit={submit}>
                    {error && <div className="notice error">{error}</div>}
                    <div>
                        <label htmlFor="invite-email">Email</label>
                        <input id="invite-email" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
                    </div>
                    <div>
                        <label htmlFor="invite-role">Role</label>
                        <select id="invite-role" value={role} onChange={e => setRole(e.target.value)}>
                            <option value="EDITOR">Editor</option>
                            <option value="VIEWER">Viewer</option>
                        </select>
                    </div>
                    <div>
                        <label htmlFor="invite-expiry">Expires in days</label>
                        <input id="invite-expiry" type="number" min="1" max="30" value={expiresInDays} onChange={e => setExpiresInDays(e.target.value)} />
                    </div>
                    <div className="toolbar-actions">
                        <button type="submit" disabled={loading}>{loading ? 'Creating...' : 'Create Invitation'}</button>
                        <button type="button" className="btn-secondary" onClick={onClose}>Close</button>
                    </div>
                    {created && (
                        <div className="share-created">
                            <div className="notice warning">Copy this invitation link now. It cannot be displayed again.</div>
                            <label htmlFor="invitation-url">Invitation link</label>
                            <input id="invitation-url" readOnly value={absoluteUrl} onFocus={e => e.currentTarget.select()} />
                            <button type="button" className="btn-secondary" onClick={() => navigator.clipboard?.writeText(absoluteUrl)}>Copy Link</button>
                        </div>
                    )}
                </form>
            </Modal>
        );
    }

    function InvitationLandingView({ token, currentUser, go, notify, onAccepted }) {
        const [metadata, setMetadata] = useState(null);
        const [status, setStatus] = useState('loading');
        const [message, setMessage] = useState(null);

        async function load() {
            setStatus('loading');
            try {
                setMetadata(await AuthApi.getInvitationMetadata(token));
                setStatus('ready');
            } catch (e) {
                setStatus('error');
                setMessage('Invitation is unavailable.');
            }
        }

        useEffect(() => { load(); }, [token]);

        function authThen(view) {
            namespace.returnAfterAuth = { name: 'invitation', token };
            go(view);
        }

        async function decide(action) {
            try {
                const result = action === 'accept' ? await AuthApi.acceptInvitation(token) : await AuthApi.rejectInvitation(token);
                notify('success', action === 'accept' ? 'Invitation accepted.' : 'Invitation rejected.');
                if (onAccepted) await onAccepted();
                if (action === 'accept') go('project', { projectId: result.projectId });
                else go('dashboard');
            } catch (e) {
                notify('error', e.message || 'Invitation could not be updated.');
            }
        }

        if (status === 'loading') return <div className="loading-line">Loading invitation...</div>;
        if (status === 'error') return <div className="notice error">{message}</div>;

        return (
            <section className="auth-layout invitation-layout">
                <div className="auth-card stack">
                    <div>
                        <h1>Project invitation</h1>
                        <p className="muted">You were invited to collaborate on {metadata.projectName} as {roleLabel(metadata.role)}.</p>
                    </div>
                    <div className="details-list">
                        <div className="detail-item"><span className="detail-label">Project</span><span className="detail-value">{metadata.projectName}</span></div>
                        <div className="detail-item"><span className="detail-label">Role</span><span className="detail-value">{roleLabel(metadata.role)}</span></div>
                        <div className="detail-item"><span className="detail-label">Expires</span><span className="detail-value">{formatDate(metadata.expiresAt)}</span></div>
                    </div>
                    {!currentUser ? (
                        <div className="toolbar-actions">
                            <button type="button" onClick={() => authThen('login')}>Login</button>
                            <button type="button" className="btn-secondary" onClick={() => authThen('register')}>Register</button>
                        </div>
                    ) : (
                        <div className="toolbar-actions">
                            <button type="button" onClick={() => decide('accept')}>Accept Invitation</button>
                            <button type="button" className="btn-secondary" onClick={() => decide('reject')}>Reject</button>
                        </div>
                    )}
                </div>
            </section>
        );
    }

    namespace.modules.collaboration = {
        ProjectCollaborationPanel,
        InvitationLandingView,
        roleLabel,
        canEdit,
        canManageMembers
    };
})();
