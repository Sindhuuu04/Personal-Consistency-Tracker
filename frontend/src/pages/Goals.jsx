import { useEffect, useState } from 'react';
import { api } from '../api';

function GoalCard({ g, run }) {
  const [p, setP] = useState({ percent: g.progress, hours: '', notes: '', blocker: '' });
  const [e, setE] = useState({ eta: '', reason: '' });
  const active = g.status === 'ACTIVE';
  return (
    <div className="card">
      <div className="line">
        <b>{g.status === 'COMPLETED' ? '✅ ' : ''}{g.title}</b>
        <span className="mu">{g.category}</span>
      </div>
      <div className="bar">
        <span style={{ width: g.progress + '%' }} />
        <i style={{ left: g.expectedCurrent + '%' }} title="Expected progress" />
      </div>
      <div className="mu">
        {g.progress}% done · expected {g.expectedCurrent}% · ETA {g.currentEta}
        {g.delayDays > 0 && <> · original {g.originalEta} (+{g.delayDays}d, {g.etaExtensionPct}% extension)</>}
        {g.daysOverdue > 0 && <span className="err"> · {g.daysOverdue}d overdue</span>}
        {g.behind && <span className="err"> · behind plan</span>}
      </div>
      {active && (
        <>
          <div className="row">
            <input type="number" min="0" max="100" style={{ width: 80 }} value={p.percent} onChange={(ev) => setP({ ...p, percent: ev.target.value })} />
            <input type="number" step="0.5" placeholder="hrs" style={{ width: 70 }} value={p.hours} onChange={(ev) => setP({ ...p, hours: ev.target.value })} />
            <input placeholder="Today’s update" value={p.notes} onChange={(ev) => setP({ ...p, notes: ev.target.value })} />
            <input placeholder="Blocker (optional)" value={p.blocker} onChange={(ev) => setP({ ...p, blocker: ev.target.value })} />
            <button onClick={() => run(() => api(`/goals/${g.id}/progress`, { method: 'POST', body: { percent: +p.percent, hours: p.hours ? +p.hours : null, notes: p.notes, blocker: p.blocker } }))}>Log</button>
          </div>
          <div className="row">
            <input type="date" value={e.eta} onChange={(ev) => setE({ ...e, eta: ev.target.value })} />
            <input placeholder="Reason for new ETA" value={e.reason} onChange={(ev) => setE({ ...e, reason: ev.target.value })} />
            <button className="ghost" disabled={!e.eta || !e.reason} onClick={() => run(() => api(`/goals/${g.id}/eta`, { method: 'POST', body: e }))}>Revise ETA</button>
          </div>
        </>
      )}
      {g.history.length > 0 && (
        <details>
          <summary className="mu">ETA history ({g.history.length})</summary>
          {g.history.map((h) => (
            <div className="mu" key={h.id}>{h.previousEta} → {h.newEta} · {h.reason}</div>
          ))}
        </details>
      )}
    </div>
  );
}

export default function Goals() {
  const [gs, setGs] = useState([]);
  const [msg, setMsg] = useState('');
  const [f, setF] = useState({ title: '', category: 'Course', eta: '' });
  const load = () => api('/goals').then(setGs);
  useEffect(() => { load().catch((e) => setMsg(e.message)); }, []);
  const run = async (fn) => { try { setMsg(''); await fn(); await load(); } catch (e) { setMsg(e.message); } };

  return (
    <>
      <div className="card">
        <h3>New goal</h3>
        <div className="row">
          <input placeholder="e.g. Finish Spring Boot course" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
          <select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
            <option>Course</option><option>Book</option><option>Project</option><option>Other</option>
          </select>
          <input type="date" value={f.eta} onChange={(e) => setF({ ...f, eta: e.target.value })} />
          <button disabled={!f.title || !f.eta} onClick={() => run(async () => { await api('/goals', { method: 'POST', body: f }); setF({ ...f, title: '' }); })}>Add</button>
        </div>
        {msg && <p className="err">{msg}</p>}
      </div>
      {gs.map((g) => <GoalCard key={g.id + '-' + g.progress + '-' + g.currentEta} g={g} run={run} />)}
      {gs.length === 0 && <p className="mu">No goals yet.</p>}
    </>
  );
}
