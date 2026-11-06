import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api";

const PLATFORMS = [
  ["LinkedIn", "linkedinUrl"],
  ["Medium", "mediumUrl"],
  ["Substack", "substackUrl"],
  ["YouTube", "youtubeUrl"],
];
const NAMES = {
  LEETCODE: "LeetCode",
  GITHUB: "GitHub",
  PROFILE: "Profile",
  GOALS: "Goal updates",
  FITNESS: "Steps",
};

function Ring({ value }) {
  const v = value ?? 0,
    C = 2 * Math.PI * 40;
  const color = v >= 70 ? "#10b981" : v >= 40 ? "#f59e0b" : "#ef4444";
  return (
    <svg width="110" height="110" viewBox="0 0 100 100">
      <circle
        cx="50"
        cy="50"
        r="40"
        fill="none"
        stroke="var(--line)"
        strokeWidth="9"
      />
      <circle
        cx="50"
        cy="50"
        r="40"
        fill="none"
        stroke={color}
        strokeWidth="9"
        strokeLinecap="round"
        strokeDasharray={`${(C * v) / 100} ${C}`}
        transform="rotate(-90 50 50)"
      />
      <text
        x="50"
        y="57"
        textAnchor="middle"
        fontSize="22"
        fontWeight="700"
        fill="var(--text)"
      >
        {value == null ? "—" : Math.round(v)}
      </text>
    </svg>
  );
}

export default function Overview() {
  const [d, setD] = useState(null);
  const [trend, setTrend] = useState([]);
  const [me, setMe] = useState(null);
  const [msg, setMsg] = useState("");
  const [lc, setLc] = useState({ title: "", url: "", difficulty: "Easy" });
  const [gh, setGh] = useState("");
  const [pf, setPf] = useState({
    LinkedIn: false,
    Medium: false,
    Substack: false,
    YouTube: false,
  });
  const [cfg, setCfg] = useState({});
  const [syncMsg, setSyncMsg] = useState("");

  const load = () =>
    Promise.all([
      api("/dashboard/today"),
      api("/dashboard/trend?days=30"),
      api("/me"),
      api("/settings"),
    ]).then(([a, b, c, e]) => {
      setD(a);
      setTrend(b);
      setMe(c);
      setCfg(e);
    });
  useEffect(() => {
    load().catch((e) => setMsg(e.message));
  }, []);
  const run = async (fn) => {
    try {
      setMsg("");
      await fn();
      await load();
    } catch (e) {
      setMsg(e.message);
    }
  };

  if (!d) return <p className="mu">{msg || "Loading…"}</p>;
  const s = d.score;
  const chart = trend.map((x) => ({ date: x.date.slice(5), score: x.total }));

  return (
    <>
      {msg && <p className="err">{msg}</p>}
      <div className="grid">
        <div className="card center">
          <div className="mu">Today’s score</div>
          <Ring value={s.total} />
          <div className="mu">
            🔥 {d.streak}-day streak · 👟 {d.stepStreak}-day 10K streak
          </div>
        </div>
        <div className="card">
          <h3>Today</h3>
          {Object.entries(s.categories).map(([k, v]) => (
            <div className="line" key={k}>
              <span>
                {v == null ? "▫️" : v >= 100 ? "✅" : "⬜"} {NAMES[k]}
              </span>
              <span className="mu">{v == null ? "no data" : v + "%"}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>Last 30 days</h3>
        <div style={{ height: 220 }}>
          <ResponsiveContainer>
            <LineChart data={chart}>
              <CartesianGrid stroke="var(--line)" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} interval={4} />
              <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} width={30} />
              <Tooltip />
              <Line
                dataKey="score"
                stroke="#6c5ce7"
                strokeWidth={2}
                dot={false}
                connectNulls
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <p className="mu">
          Days with no data are left as gaps, never counted as zero.
        </p>
      </div>

      <div className="grid">
        <div className="card">
          <h3>🧩 LeetCode</h3>
          <input
            placeholder="Problem title"
            value={lc.title}
            onChange={(e) => setLc({ ...lc, title: e.target.value })}
          />
          <input
            placeholder="Problem URL (proof)"
            value={lc.url}
            onChange={(e) => setLc({ ...lc, url: e.target.value })}
          />
          <div className="row">
            <select
              value={lc.difficulty}
              onChange={(e) => setLc({ ...lc, difficulty: e.target.value })}
            >
              <option>Easy</option>
              <option>Medium</option>
              <option>Hard</option>
            </select>
            <button
              onClick={() =>
                run(async () => {
                  await api("/leetcode", { method: "POST", body: lc });
                  setLc({ title: "", url: "", difficulty: lc.difficulty });
                })
              }
            >
              Save
            </button>
          </div>
        </div>
        <div className="card">
          <h3>💻 GitHub</h3>
          <input
            placeholder="What meaningful work did you ship?"
            value={gh}
            onChange={(e) => setGh(e.target.value)}
          />
          <button
            disabled={!gh.trim()}
            onClick={() =>
              run(async () => {
                await api("/activities", {
                  method: "POST",
                  body: { type: "GITHUB", score: 100, notes: gh },
                });
                setGh("");
              })
            }
          >
            Mark done
          </button>
        </div>
        <div className="card">
          <h3>📣 Profile activity</h3>
          <p className="mu">
            Published or posted today? Any one platform counts as 100%.
          </p>
          {PLATFORMS.map(([k, key]) => (
            <label className="line" key={k}>
              <span>
                <input
                  type="checkbox"
                  style={{ width: "auto", marginRight: 8 }}
                  checked={pf[k]}
                  onChange={(e) => setPf({ ...pf, [k]: e.target.checked })}
                />
                {k}
              </span>
              {cfg[key] && (
                <a href={cfg[key]} target="_blank" rel="noreferrer">
                  open ↗
                </a>
              )}
            </label>
          ))}
          <button
            onClick={() =>
              run(() => {
                const on = PLATFORMS.map((x) => x[0]).filter((k) => pf[k]);
                return api("/activities", {
                  method: "POST",
                  body: {
                    type: "PROFILE",
                    score: on.length ? 100 : 0,
                    notes: on.join(", "),
                  },
                });
              })
            }
          >
            Save
          </button>
        </div>
      </div>
      <div className="card">
        <h3>🔄 Auto-sync</h3>
        <p className="mu">
          Checks your public GitHub pushes and accepted LeetCode submissions
          (also runs hourly). Set usernames in Settings.
        </p>
        <button
          onClick={() =>
            run(async () => {
              const r = await api("/sync", { method: "POST" });
              setSyncMsg(
                Object.entries(r)
                  .map(([k, v]) => k + ": " + v)
                  .join(" · "),
              );
            })
          }
        >
          Sync now
        </button>
        {syncMsg && <p className="mu">{syncMsg}</p>}
      </div>
      <div className="card">
        <h3>🤖 Telegram</h3>
        {me?.telegramLinked ? (
          <p className="mu">Linked ✅ — send /help to your bot.</p>
        ) : (
          <p className="mu">
            Open your bot and send <code>/link {me?.linkCode}</code>
          </p>
        )}
      </div>
    </>
  );
}
