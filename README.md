# Consistency Tracker (MVP)

React + Vite · Spring Boot 3 (Java 17) · MySQL · Telegram bot (long polling)

## Run locally
```bash
docker compose up -d db                       # MySQL on :3306
cd backend && mvn spring-boot:run             # API on :8080 (tables auto-created)
cd frontend && npm install && npm run dev     # http://localhost:5173
```

## Telegram bot
1. @BotFather -> /newbot -> copy the token.
2. `export TELEGRAM_BOT_TOKEN=...` then restart the backend.
3. Register in the dashboard, open your bot, send `/link <code>` (code shown on Overview).
4. Commands: /today /leetcode /github /profile /goal /update /steps /streak /report /help.
Reminders (user's timezone): 08:00 briefing, 20:00 + 22:00 open-items check, Sunday 20:00 weekly report.

## Environment variables
DB_USER, DB_PASSWORD, JWT_SECRET (set a long random value), TELEGRAM_BOT_TOKEN

## Scoring (backend/TrackerService.java)
Daily = 0.20 LeetCode + 0.20 GitHub + 0.15 Profile + 0.25 Goal updates + 0.20 Steps.
Categories with no data (no steps logged, no active goals) are excluded and weights re-normalised, never counted as 0.
A day counts for the streak at >= 70%.

## Rules implemented
- LeetCode needs a title or URL; the same URL is never counted twice.
- Original ETA is immutable; every revision is appended to goal_eta_history with a reason.
- Progress is clamped 0-100; steps are upserted per local date (re-sync replaces, never adds).
- Dashboard and Telegram both call TrackerService -> same MySQL rows.

## Next (blueprint phases 3, 4, 8)
GitHub OAuth sync, platform metrics + CSV import, rest/excused days, notification_logs table, configurable weights UI, native HealthKit app.

## No Docker / no MySQL? Use the built-in H2 file database
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
Data is saved in backend/data/. Switch to MySQL later by dropping the profile flag.
