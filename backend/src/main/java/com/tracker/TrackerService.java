package com.tracker;

import com.tracker.Model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** All business rules live here. REST controllers and the Telegram bot both call this class. */
@Service
@RequiredArgsConstructor
public class TrackerService {

    /** Configurable weights (sum = 1.0). Categories with no data are left out and weights re-normalised. */
    static final Map<String, Double> W = new LinkedHashMap<>();
    static {
        W.put("LEETCODE", .20); W.put("GITHUB", .20); W.put("PROFILE", .15); W.put("GOALS", .25); W.put("FITNESS", .20);
    }
    static final double DONE = 70;   // a day counts towards the streak when total >= 70%
    static final int TARGET = 10000; // daily step target

    final UserRepo users; final ActivityRepo acts; final LeetRepo leet; final GoalRepo goals;
    final ProgressRepo progress; final EtaRepo etas; final FitnessRepo fit;

    public record DayScore(LocalDate date, Map<String, Integer> categories, Double total, List<String> pending) {}

    static boolean blank(String s) { return s == null || s.isBlank(); }

    public LocalDate today(Long uid) {
        return LocalDate.now(ZoneId.of(users.findById(uid).orElseThrow().getTimezone()));
    }

    // ---------- scoring ----------
    public DayScore score(Long uid, LocalDate d) {
        Map<String, Integer> byType = new HashMap<>();
        acts.findByUserIdAndActivityDate(uid, d).forEach(a -> byType.put(a.getType(), a.getScore()));
        Map<String, Integer> c = new LinkedHashMap<>();
        for (String t : List.of("LEETCODE", "GITHUB", "PROFILE")) c.put(t, byType.getOrDefault(t, 0));
        c.put("GOALS", goalScore(uid, d));
        Optional<FitnessDaily> f = fit.findByUserIdAndActivityDate(uid, d);
        c.put("FITNESS", f.map(x -> (int) Math.round(Math.min(x.getSteps() / (double) x.getTargetSteps(), 1) * 100)).orElse(null));

        double sum = 0, w = 0;
        List<String> pending = new ArrayList<>();
        for (var e : c.entrySet()) {
            if (e.getValue() == null) { pending.add(e.getKey() + " (no data)"); continue; } // missing != zero
            sum += e.getValue() * W.get(e.getKey());
            w += W.get(e.getKey());
            if (e.getValue() < 100) pending.add(e.getKey());
        }
        Double total = w == 0 ? null : Math.round(sum / w * 10) / 10.0;
        return new DayScore(d, c, total, pending);
    }

    Integer goalScore(Long uid, LocalDate d) {
        List<Long> active = goals.findByUserIdOrderByIdDesc(uid).stream()
            .filter(g -> !g.getStartDate().isAfter(d) && (g.getCompletedDate() == null || !g.getCompletedDate().isBefore(d)))
            .map(Goal::getId).toList();
        if (active.isEmpty()) return null;
        int done = progress.findByGoalIdInAndUpdateDate(active, d).size();
        return (int) Math.round(done * 100.0 / active.size());
    }

    public List<DayScore> trend(Long uid, int days) {
        LocalDate t = today(uid);
        List<DayScore> out = new ArrayList<>();
        for (int i = Math.min(days, 120) - 1; i >= 0; i--) out.add(score(uid, t.minusDays(i)));
        return out;
    }

    public int streak(Long uid) {
        LocalDate d = today(uid);
        DayScore s = score(uid, d);
        if (s.total() == null || s.total() < DONE) d = d.minusDays(1); // today still in progress
        int n = 0;
        for (int i = 0; i < 120; i++, d = d.minusDays(1)) {
            s = score(uid, d);
            if (s.total() == null || s.total() < DONE) break;
            n++;
        }
        return n;
    }

    public int stepStreak(Long uid) {
        LocalDate d = today(uid);
        if (fit.findByUserIdAndActivityDate(uid, d).map(f -> f.getSteps() < TARGET).orElse(true)) d = d.minusDays(1);
        int n = 0;
        for (int i = 0; i < 365; i++, d = d.minusDays(1)) {
            if (fit.findByUserIdAndActivityDate(uid, d).map(f -> f.getSteps() >= TARGET).orElse(false)) n++; else break;
        }
        return n;
    }

    // ---------- activities ----------
    @Transactional
    public void setActivity(Long uid, LocalDate d, String type, int score, String url, String notes) {
        if (!List.of("LEETCODE", "GITHUB", "PROFILE").contains(type)) throw new IllegalArgumentException("Unknown activity type");
        DailyActivity a = acts.findByUserIdAndActivityDateAndType(uid, d, type).orElseGet(() -> {
            DailyActivity x = new DailyActivity(); x.setUserId(uid); x.setActivityDate(d); x.setType(type); return x;
        });
        a.setScore(Math.max(0, Math.min(100, score)));
        a.setEvidenceUrl(url); a.setNotes(notes);
        acts.save(a);
    }

    public List<DailyActivity> activities(Long uid, LocalDate d) { return acts.findByUserIdAndActivityDate(uid, d); }

    @Transactional
    public Map<String, Object> addLeet(Long uid, LocalDate d, String title, String url, String difficulty, String topics) {
        if (blank(title) && blank(url)) throw new IllegalArgumentException("Add a problem title or URL as proof of completion");
        boolean dup = !blank(url) && leet.existsByUserIdAndUrl(uid, url.trim());
        if (!dup) {
            LeetCodeRecord r = new LeetCodeRecord();
            r.setUserId(uid); r.setTitle(blank(title) ? url : title.trim()); r.setUrl(blank(url) ? null : url.trim());
            r.setDifficulty(difficulty); r.setTopics(topics); r.setSubmissionDate(d);
            leet.save(r);
        }
        setActivity(uid, d, "LEETCODE", 100, url, title);
        return Map.of("duplicate", dup);
    }

    public List<LeetCodeRecord> recentLeet(Long uid) { return leet.findTop20ByUserIdOrderBySubmissionDateDescIdDesc(uid); }

    // ---------- fitness ----------
    @Transactional
    public FitnessDaily setSteps(Long uid, LocalDate d, int steps, String source) {
        if (steps < 0 || steps > 200000) throw new IllegalArgumentException("Steps must be between 0 and 200000");
        FitnessDaily f = fit.findByUserIdAndActivityDate(uid, d).orElseGet(() -> {
            FitnessDaily x = new FitnessDaily(); x.setUserId(uid); x.setActivityDate(d); return x;
        });
        f.setSteps(steps); f.setTargetSteps(TARGET);
        f.setSource(blank(source) ? "manual" : source); f.setSyncedAt(LocalDateTime.now()); // re-sync replaces, never adds
        return fit.save(f);
    }

    public List<Map<String, Object>> stepsRange(Long uid, int days) {
        LocalDate t = today(uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = t.minusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("steps", fit.findByUserIdAndActivityDate(uid, d).map(FitnessDaily::getSteps).orElse(null));
            out.add(m);
        }
        return out;
    }

    // ---------- goals ----------
    @Transactional
    public Goal createGoal(Long uid, String title, String category, LocalDate start, LocalDate eta) {
        LocalDate s = start == null ? today(uid) : start;
        if (!eta.isAfter(s)) throw new IllegalArgumentException("ETA must be after the start date");
        Goal g = new Goal();
        g.setUserId(uid); g.setTitle(title.trim()); g.setCategory(blank(category) ? "General" : category);
        g.setStartDate(s); g.setOriginalEta(eta); g.setCurrentEta(eta);
        return goals.save(g);
    }

    Goal owned(Long uid, Long gid) {
        Goal g = goals.findById(gid).orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        if (!g.getUserId().equals(uid)) throw new IllegalArgumentException("Goal not found");
        return g;
    }

    @Transactional
    public void addProgress(Long uid, Long gid, LocalDate d, int pct, Double hours, String notes, String blocker) {
        Goal g = owned(uid, gid);
        int p = Math.max(0, Math.min(100, pct));
        GoalProgress gp = progress.findByGoalIdAndUpdateDate(gid, d).orElseGet(() -> {
            GoalProgress x = new GoalProgress(); x.setGoalId(gid); x.setUpdateDate(d); return x;
        });
        gp.setProgressPercent(p); gp.setHoursSpent(hours); gp.setNotes(notes); gp.setBlocker(blocker);
        progress.save(gp);
        g.setProgressPercent(p);
        if (p >= 100) { g.setStatus("COMPLETED"); g.setCompletedDate(d); } else { g.setStatus("ACTIVE"); g.setCompletedDate(null); }
        goals.save(g);
    }

    /** Original ETA is never touched; every revision is appended to history. */
    @Transactional
    public void reviseEta(Long uid, Long gid, LocalDate newEta, String reason) {
        Goal g = owned(uid, gid);
        if (!newEta.isAfter(g.getStartDate())) throw new IllegalArgumentException("ETA must be after the start date");
        if (newEta.equals(g.getCurrentEta())) throw new IllegalArgumentException("That is already the current ETA");
        GoalEtaHistory h = new GoalEtaHistory();
        h.setGoalId(gid); h.setPreviousEta(g.getCurrentEta()); h.setNewEta(newEta); h.setReason(reason);
        etas.save(h);
        g.setCurrentEta(newEta);
        goals.save(g);
    }

    static int pct(long elapsed, long total) {
        if (total <= 0) return 100;
        return (int) Math.max(0, Math.min(100, Math.round(elapsed * 100.0 / total)));
    }

    public List<Map<String, Object>> goalViews(Long uid) {
        LocalDate today = today(uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Goal g : goals.findByUserIdOrderByIdDesc(uid)) {
            long planned = ChronoUnit.DAYS.between(g.getStartDate(), g.getOriginalEta());
            long plannedNow = ChronoUnit.DAYS.between(g.getStartDate(), g.getCurrentEta());
            long elapsed = ChronoUnit.DAYS.between(g.getStartDate(), today);
            long remaining = ChronoUnit.DAYS.between(today, g.getCurrentEta());
            long delay = ChronoUnit.DAYS.between(g.getOriginalEta(), g.getCurrentEta());
            boolean active = "ACTIVE".equals(g.getStatus());
            int expectedNow = pct(elapsed, plannedNow);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId()); m.put("title", g.getTitle()); m.put("category", g.getCategory());
            m.put("startDate", g.getStartDate()); m.put("originalEta", g.getOriginalEta()); m.put("currentEta", g.getCurrentEta());
            m.put("status", g.getStatus()); m.put("progress", g.getProgressPercent());
            m.put("expectedOriginal", pct(elapsed, planned)); m.put("expectedCurrent", expectedNow);
            m.put("daysRemaining", Math.max(remaining, 0)); m.put("daysOverdue", active && remaining < 0 ? -remaining : 0);
            m.put("delayDays", Math.max(delay, 0));
            m.put("etaExtensionPct", planned > 0 ? Math.round(Math.max(delay, 0) * 1000.0 / planned) / 10.0 : 0);
            m.put("behind", active && g.getProgressPercent() < expectedNow - 10);
            m.put("history", etas.findByGoalIdOrderByChangedAtAsc(g.getId()));
            out.add(m);
        }
        return out;
    }

    // ---------- text for Telegram ----------
    public String summary(Long uid) {
        LocalDate d = today(uid);
        DayScore s = score(uid, d);
        StringBuilder b = new StringBuilder("📅 " + d + "\nScore: " + (s.total() == null ? "—" : s.total() + "%") + "\n");
        s.categories().forEach((k, v) -> b.append(v == null ? "▫️ " : v >= 100 ? "✅ " : "⬜ ").append(k)
            .append(v == null ? " (no data)" : " " + v + "%").append("\n"));
        fit.findByUserIdAndActivityDate(uid, d).ifPresent(x -> b.append("👟 ").append(x.getSteps()).append(" / ").append(TARGET).append("\n"));
        return b.toString();
    }

    public String goalsText(Long uid) {
        var list = goalViews(uid).stream().filter(m -> "ACTIVE".equals(m.get("status"))).toList();
        if (list.isEmpty()) return "No active goals. Create one in the dashboard.";
        StringBuilder b = new StringBuilder("🎯 Active goals\n");
        for (var m : list) {
            b.append("#").append(m.get("id")).append(" ").append(m.get("title")).append(" — ").append(m.get("progress"))
                .append("% (expected ").append(m.get("expectedCurrent")).append("%) ETA ").append(m.get("currentEta"));
            if (Boolean.TRUE.equals(m.get("behind"))) b.append(" ⚠️ behind");
            b.append("\n");
        }
        return b.append("Update: /update <id> <percent> <note>").toString();
    }

    public String streakText(Long uid) {
        return "🔥 Consistency streak: " + streak(uid) + " days (days scoring ≥ " + (int) DONE + "%)\n👟 10K-step streak: " + stepStreak(uid) + " days";
    }

    public String weekly(Long uid) {
        LocalDate t = today(uid);
        double sum = 0; int n = 0, good = 0, lc = 0, steps = 0, stepDays = 0, tenK = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = t.minusDays(i);
            DayScore s = score(uid, d);
            if (s.total() != null) { sum += s.total(); n++; if (s.total() >= DONE) good++; }
            if (Objects.equals(s.categories().get("LEETCODE"), 100)) lc++;
            Optional<FitnessDaily> f = fit.findByUserIdAndActivityDate(uid, d);
            if (f.isPresent()) { steps += f.get().getSteps(); stepDays++; if (f.get().getSteps() >= TARGET) tenK++; }
        }
        return "📊 Last 7 days\nAverage score: " + (n == 0 ? "—" : Math.round(sum / n * 10) / 10.0 + "%")
            + "\nStrong days: " + good + "/7\nLeetCode days: " + lc + "/7\nAvg steps: " + (stepDays == 0 ? "—" : steps / stepDays)
            + "\n10K days: " + tenK + "/7\n" + streakText(uid);
    }
}
