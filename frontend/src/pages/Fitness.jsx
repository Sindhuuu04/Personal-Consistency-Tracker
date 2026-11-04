import { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api';

export default function Fitness() {
  const [rows, setRows] = useState([]);
  const [steps, setSteps] = useState('');
  const [token, setToken] = useState('');
  const [msg, setMsg] = useState('');
  const load = () => api('/fitness/range?days=14').then(setRows);
  useEffect(() => { load().catch((e) => setMsg(e.message)); }, []);

  const vals = rows.filter((r) => r.steps != null).map((r) => r.steps);
  const avg = vals.length ? Math.round(vals.reduce((a, b) => a + b, 0) / vals.length) : 0;
  const hit = vals.filter((v) => v >= 10000).length;
  const today = rows.length ? rows[rows.length - 1].steps : null;

  return (
    <>
      <div className="grid">
        <div className="card center"><div className="mu">Today</div><h2>{today ?? '—'}</h2><div className="mu">{today != null ? Math.min(100, Math.round(today / 100)) + '% of 10,000' : 'not logged'}</div></div>
        <div className="card center"><div className="mu">14-day average</div><h2>{avg}</h2><div className="mu">{hit} days at 10K+</div></div>
      </div>
      <div className="card">
        <h3>Daily steps</h3>
        <div style={{ height: 240 }}>
          <ResponsiveContainer>
            <BarChart data={rows.map((r) => ({ date: r.date.slice(5), steps: r.steps }))}>
              <CartesianGrid stroke="var(--line)" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} width={40} />
              <Tooltip />
              <ReferenceLine y={10000} stroke="#10b981" strokeDasharray="4 4" />
              <Bar dataKey="steps" fill="#6c5ce7" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <div className="card">
        <h3>Log today’s steps</h3>
        <div className="row">
          <input type="number" placeholder="e.g. 10432" value={steps} onChange={(e) => setSteps(e.target.value)} />
          <button disabled={!steps} onClick={async () => { try { await api('/fitness/steps', { method: 'POST', body: { steps: +steps, source: 'manual' } }); setSteps(''); setMsg(''); load(); } catch (e) { setMsg(e.message); } }}>Save</button>
        </div>
        {msg && <p className="err">{msg}</p>}
      </div>
      <div className="card">
        <h3>iPhone Shortcut</h3>
        <p className="mu">Automation: <i>Get Health Sample (Steps, today, sum)</i> → <i>Get Contents of URL</i>: POST to <code>https://YOUR-HOST/api/fitness/steps</code>, header <code>Authorization: Bearer TOKEN</code>, JSON body <code>{'{"steps": <number>, "source": "iphone-shortcut"}'}</code>. Re-sending replaces the day’s value, never adds to it.</p>
        <button className="ghost" onClick={async () => setToken((await api('/me/device-token', { method: 'POST' })).token)}>Generate 1-year Shortcut token</button>
        {token && <textarea readOnly rows="3" value={token} onFocus={(e) => e.target.select()} />}
      </div>
    </>
  );
}
