(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState } = React;
    const AuthApi = namespace.modules.api;

    function LoginView({ onLogin, go }) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);

    async function submit(e) {
        e.preventDefault();
        setError(null);
        if (!email.trim() || !password) {
            setError('Email and password are required.');
            return;
        }
        setLoading(true);
        try {
            const user = await AuthApi.loginUser({ email: email.trim(), password });
            setPassword('');
            await AuthApi.refreshCsrfToken();
            onLogin(user, 'Welcome back.');
            const returnView = namespace.returnAfterAuth;
            namespace.returnAfterAuth = null;
            returnView ? go(returnView.name, returnView) : go('dashboard');
        } catch (e) {
            setError(e.status === 401 ? 'Invalid email or password.' : (e.message || 'Login failed.'));
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="auth-layout">
            <form className="auth-card form-grid" onSubmit={submit}>
                <div>
                    <h1>Log in</h1>
                    <p className="muted">Continue to your projects and saved diagrams.</p>
                </div>
                {error && <div className="notice error">{error}</div>}
                <div>
                    <label htmlFor="login-email">Email</label>
                    <input id="login-email" type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="email" required />
                </div>
                <div>
                    <label htmlFor="login-password">Password</label>
                    <input id="login-password" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required />
                </div>
                <button type="submit" disabled={loading}>{loading ? 'Logging in...' : 'Log in'}</button>
                <p className="muted">No account? <button type="button" className="link-button" onClick={() => go('register')}>Create one</button></p>
            </form>
        </div>
    );
}

function RegisterView({ onLogin, go }) {
    const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', confirmPassword: '' });
    const [errors, setErrors] = useState({});
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);

    function setField(field, value) {
        setForm(prev => ({ ...prev, [field]: value }));
        setErrors(prev => ({ ...prev, [field]: null }));
    }

    async function submit(e) {
        e.preventDefault();
        const next = {};
        if (!form.firstName.trim()) next.firstName = 'First name is required.';
        if (!form.lastName.trim()) next.lastName = 'Last name is required.';
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Use a valid email address.';
        if (form.password.length < 8) next.password = 'Password must be at least 8 characters.';
        if (form.password !== form.confirmPassword) next.confirmPassword = 'Passwords must match.';
        setErrors(next);
        setError(null);
        if (Object.keys(next).length) return;
        setLoading(true);
        try {
            const user = await AuthApi.registerUser({
                firstName: form.firstName.trim(),
                lastName: form.lastName.trim(),
                email: form.email.trim(),
                password: form.password,
            });
            setForm({ firstName: '', lastName: '', email: '', password: '', confirmPassword: '' });
            await AuthApi.refreshCsrfToken();
            onLogin(user, 'Account created.');
            const returnView = namespace.returnAfterAuth;
            namespace.returnAfterAuth = null;
            returnView ? go(returnView.name, returnView) : go('dashboard');
        } catch (e) {
            setError(e.code === 'DUPLICATE_EMAIL' ? 'That email is already registered.' : (e.message || 'Registration failed.'));
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="auth-layout">
            <form className="auth-card form-grid" onSubmit={submit}>
                <div>
                    <h1>Create account</h1>
                    <p className="muted">Save diagrams into projects and return to them later.</p>
                </div>
                {error && <div className="notice error">{error}</div>}
                <div>
                    <label htmlFor="first-name">First name</label>
                    <input id="first-name" type="text" value={form.firstName} onChange={e => setField('firstName', e.target.value)} required />
                    {errors.firstName && <div className="field-error">{errors.firstName}</div>}
                </div>
                <div>
                    <label htmlFor="last-name">Last name</label>
                    <input id="last-name" type="text" value={form.lastName} onChange={e => setField('lastName', e.target.value)} required />
                    {errors.lastName && <div className="field-error">{errors.lastName}</div>}
                </div>
                <div>
                    <label htmlFor="register-email">Email</label>
                    <input id="register-email" type="email" value={form.email} onChange={e => setField('email', e.target.value)} autoComplete="email" required />
                    {errors.email && <div className="field-error">{errors.email}</div>}
                </div>
                <div>
                    <label htmlFor="register-password">Password</label>
                    <input id="register-password" type="password" value={form.password} onChange={e => setField('password', e.target.value)} autoComplete="new-password" required />
                    {errors.password && <div className="field-error">{errors.password}</div>}
                </div>
                <div>
                    <label htmlFor="confirm-password">Confirm password</label>
                    <input id="confirm-password" type="password" value={form.confirmPassword} onChange={e => setField('confirmPassword', e.target.value)} autoComplete="new-password" required />
                    {errors.confirmPassword && <div className="field-error">{errors.confirmPassword}</div>}
                </div>
                <button type="submit" disabled={loading}>{loading ? 'Creating account...' : 'Create account'}</button>
                <p className="muted">Already have an account? <button type="button" className="link-button" onClick={() => go('login')}>Log in</button></p>
            </form>
        </div>
    );
}

    namespace.modules.auth = {
        LoginView,
        RegisterView
    };
})();
