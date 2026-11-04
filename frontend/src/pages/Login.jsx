import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, auth } from '../api';

export default function Login() {
  const nav = useNavigate();
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ email: '', password: '' });
  const [err, setErr] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    try {
      const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const r = await api('/auth/' + (mode === 'login' ? 'login' : 'register'), { method: 'POST', body: { ...f, timezone: tz } });
      auth.set(r.token);
      nav('/');
    } catch (ex) { setErr(ex.message); }
  };

  return (
    <div className="wrap narrow">
      <form className="card" onSubmit={submit}>
        <h2>🔥 Consistency Tracker</h2>
        <input type="email" placeholder="Email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} required />
        <input type="password" placeholder="Password (min 8)" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required />
        {err && <p className="err">{err}</p>}
        <button>{mode === 'login' ? 'Log in' : 'Create account'}</button>
        <button type="button" className="ghost" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'Need an account? Register' : 'Have an account? Log in'}
        </button>
      </form>
    </div>
  );
}
