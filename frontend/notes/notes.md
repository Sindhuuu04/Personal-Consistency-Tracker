# Personal Consistency Tracker: Notes

Personal analytics app that tracks LeetCode, GitHub, profile activity, learning goals (with ETAs) and daily steps, with a Telegram bot that uses the same database.

**Stack:** React + Vite · Spring Boot 3 (Java 17+) · MySQL or H2 · Telegram Bot API

---

## 1. Run locally (Windows)

### One-time setup
- Java JDK 17 or newer, Maven, Node.js LTS
- Set `JAVA_HOME` (Maven needs it):
  ```
  set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  ```
  For a permanent setting, add `JAVA_HOME` under Environment Variables → User variables.

### Backend (terminal 1)
```
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
- `h2` profile = built-in file database in `backend/data/` (no MySQL or Docker needed).
- To use MySQL instead: start MySQL, drop the profile flag, and set `DB_USER` / `DB_PASSWORD`.
- Ready when the console shows `Started ConsistencyApp`.
- IntelliJ: run `ConsistencyApp` with Program arguments `--spring.profiles.active=h2`.

### Frontend (terminal 2)
```
cd frontend
npm install
npm run dev
```
Open http://localhost:5173, register, and start tracking.

---

## 2. Environment variables

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Long random string used to sign login tokens. Change it from the default. |
| `TELEGRAM_BOT_TOKEN` | Token from @BotFather. |
| `DB_USER`, `DB_PASSWORD` | MySQL credentials (not needed with H2). |

Never commit tokens or secrets.

---

## 3. Telegram bot

1. @BotFather → `/newbot` → copy the token.
2. `set TELEGRAM_BOT_TOKEN=your_token` and restart the backend.
3. Open the bot and send `/link <code>` (the code is on the Overview page).

| Command | Action |
|---|---|
| `/today` | Today's status and score |
| `/leetcode Title \| URL \| Difficulty` | Record a solved problem |
| `/github what you shipped` | Mark a meaningful contribution |
| `/profile 0-100 [note]` | Log profile activity |
| `/goal` | List active goals |
| `/update <goalId> <percent> [note]` | Daily goal update |
| `/steps [number]` | Show or save steps |
| `/streak`, `/report`, `/help` | Streaks, weekly report, help |

Reminders (your timezone): 08:00 briefing, 20:00 and 22:00 open-items check, Sunday 20:00 weekly report.

---

## 4. Scoring rules

```
Daily score = 0.20 LeetCode + 0.20 GitHub + 0.15 Profile + 0.25 Goal updates + 0.20 Steps
```
- Categories with no data (no steps logged, no active goals) are left out and the weights re-normalised, never counted as 0.
- A day counts toward the streak at 70% or more.
- Steps: `min(steps / 10000, 1) × 100`.
- ETA extension % = (current ETA − original ETA) ÷ original planned days × 100.
- The original ETA is immutable. Every revision is saved in `goal_eta_history` with a reason.

---

## 5. REST API (all except auth need `Authorization: Bearer <token>`)

| Method | Endpoint |
|---|---|
| POST | `/api/auth/register`, `/api/auth/login` |
| GET | `/api/me`, `/api/dashboard/today`, `/api/dashboard/trend?days=30` |
| GET/POST | `/api/activities` |
| GET/POST | `/api/leetcode` |
| GET/POST | `/api/goals` |
| POST | `/api/goals/{id}/progress`, `/api/goals/{id}/eta` |
| GET | `/api/fitness/range?days=14` |
| POST | `/api/fitness/steps`, `/api/me/device-token` |

---

## 6. iPhone steps (Shortcut)

Apple Health can't be read from a website. Use an iPhone Shortcut automation:
1. **Find Health Samples**: Steps, today, sum.
2. **Get Contents of URL**: POST to `https://YOUR-HOST/api/fitness/steps`
   - Header: `Authorization: Bearer <1-year token from the Fitness page>`
   - JSON body: `{"steps": <number>, "source": "iphone-shortcut"}`

Re-sending replaces that day's value, never adds to it. For automatic sync, build a native iOS companion app (Swift + HealthKit) later.

---

## 7. Git cheatsheet

```
git remote add origin https://github.com/Sindhuuu04/Personal-Consistency-Tracker.git
git branch -M main
git push -u origin main
```
Daily:
```
git add .
git commit -m "What I changed"
git push
```
Ignored: `node_modules/`, `target/`, `dist/`, `.env`, `backend/data/`.

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `docker` not recognized | Skip Docker and use the `h2` profile. |
| `JAVA_HOME ... not defined correctly` | Set `JAVA_HOME` as in section 1. |
| Red errors on `getX()` / `setX()` in IntelliJ | Enable Settings → Compiler → Annotation Processors, then Rebuild Project. |
| Login redirects back to `/login` | Backend isn't running on :8080. |
| `remote origin already exists` | `git remote set-url origin <url>` |
| Bot says "Link first" | Send `/link <code>` from the Overview page. |

---

## 9. Roadmap

- [ ] GitHub OAuth sync (auto-detect commits and PRs)
- [ ] LinkedIn / Medium / Substack metrics (manual or CSV import first)
- [ ] Rest / excused days
- [ ] Notification log table (persistent duplicate-send protection)
- [ ] Screen for changing score weights
- [ ] Native iOS app with HealthKit
- [ ] Docker + cloud deployment