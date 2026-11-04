package com.tracker;

import com.tracker.Model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    final TrackerService t; final UserRepo users; final PasswordEncoder enc; final JwtService jwt;

    record Cred(@Email @NotBlank String email, @Size(min = 8, message = "min 8 characters") String password, String timezone) {}
    record Act(LocalDate date, @NotBlank String type, @NotNull Integer score, String evidenceUrl, String notes) {}
    record Leet(LocalDate date, String title, String url, String difficulty, String topics) {}
    record GoalIn(@NotBlank String title, String category, LocalDate startDate, @NotNull LocalDate eta) {}
    record Prog(LocalDate date, @NotNull Integer percent, Double hours, String notes, String blocker) {}
    record EtaIn(@NotNull LocalDate eta, @NotBlank String reason) {}
    record StepsIn(LocalDate date, @NotNull Integer steps, String source) {}

    LocalDate day(Long uid, LocalDate d) { return d == null ? t.today(uid) : d; }

    // ----- auth -----
    @PostMapping("/auth/register")
    public Map<String, Object> register(@Valid @RequestBody Cred c) {
        String email = c.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) throw new IllegalArgumentException("Email already registered");
        User u = new User();
        u.setEmail(email); u.setPasswordHash(enc.encode(c.password()));
        if (!TrackerService.blank(c.timezone())) {
            try { java.time.ZoneId.of(c.timezone()); u.setTimezone(c.timezone()); } catch (Exception ignored) {}
        }
        u.setLinkCode(UUID.randomUUID().toString().substring(0, 8));
        users.save(u);
        return Map.of("token", jwt.make(u.getId(), 7));
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Cred c) {
        User u = users.findByEmail(c.email().trim().toLowerCase()).orElse(null);
        if (u == null || !enc.matches(c.password(), u.getPasswordHash())) throw new IllegalArgumentException("Invalid email or password");
        return Map.of("token", jwt.make(u.getId(), 7));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Long uid) {
        User u = users.findById(uid).orElseThrow();
        return Map.of("email", u.getEmail(), "timezone", u.getTimezone(), "linkCode", u.getLinkCode(), "telegramLinked", u.getTelegramChatId() != null);
    }

    /** Long-lived token for the iPhone Shortcut that posts daily steps. */
    @PostMapping("/me/device-token")
    public Map<String, Object> deviceToken(@AuthenticationPrincipal Long uid) { return Map.of("token", jwt.make(uid, 365)); }

    // ----- dashboard -----
    @GetMapping("/dashboard/today")
    public Map<String, Object> today(@AuthenticationPrincipal Long uid) {
        LocalDate d = t.today(uid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", d); m.put("score", t.score(uid, d));
        m.put("streak", t.streak(uid)); m.put("stepStreak", t.stepStreak(uid));
        m.put("goals", t.goalViews(uid).stream().filter(g -> "ACTIVE".equals(g.get("status"))).toList());
        return m;
    }

    @GetMapping("/dashboard/trend")
    public List<TrackerService.DayScore> trend(@AuthenticationPrincipal Long uid, @RequestParam(defaultValue = "30") int days) {
        return t.trend(uid, days);
    }

    // ----- activities -----
    @GetMapping("/activities")
    public List<DailyActivity> activities(@AuthenticationPrincipal Long uid, @RequestParam(required = false) LocalDate date) {
        return t.activities(uid, day(uid, date));
    }

    @PostMapping("/activities")
    public Map<String, Object> activity(@AuthenticationPrincipal Long uid, @Valid @RequestBody Act a) {
        t.setActivity(uid, day(uid, a.date()), a.type().toUpperCase(), a.score(), a.evidenceUrl(), a.notes());
        return Map.of("ok", true);
    }

    @PostMapping("/leetcode")
    public Map<String, Object> leetcode(@AuthenticationPrincipal Long uid, @RequestBody Leet l) {
        return t.addLeet(uid, day(uid, l.date()), l.title(), l.url(), l.difficulty(), l.topics());
    }

    @GetMapping("/leetcode")
    public List<LeetCodeRecord> leetcodeList(@AuthenticationPrincipal Long uid) { return t.recentLeet(uid); }

    // ----- goals -----
    @GetMapping("/goals")
    public List<Map<String, Object>> goals(@AuthenticationPrincipal Long uid) { return t.goalViews(uid); }

    @PostMapping("/goals")
    public Goal createGoal(@AuthenticationPrincipal Long uid, @Valid @RequestBody GoalIn g) {
        return t.createGoal(uid, g.title(), g.category(), g.startDate(), g.eta());
    }

    @PostMapping("/goals/{id}/progress")
    public Map<String, Object> progress(@AuthenticationPrincipal Long uid, @PathVariable Long id, @Valid @RequestBody Prog p) {
        t.addProgress(uid, id, day(uid, p.date()), p.percent(), p.hours(), p.notes(), p.blocker());
        return Map.of("ok", true);
    }

    @PostMapping("/goals/{id}/eta")
    public Map<String, Object> eta(@AuthenticationPrincipal Long uid, @PathVariable Long id, @Valid @RequestBody EtaIn e) {
        t.reviseEta(uid, id, e.eta(), e.reason());
        return Map.of("ok", true);
    }

    // ----- fitness -----
    @GetMapping("/fitness/range")
    public List<Map<String, Object>> range(@AuthenticationPrincipal Long uid, @RequestParam(defaultValue = "14") int days) {
        return t.stepsRange(uid, Math.min(days, 90));
    }

    @PostMapping("/fitness/steps")
    public Map<String, Object> steps(@AuthenticationPrincipal Long uid, @Valid @RequestBody StepsIn s) {
        t.setSteps(uid, day(uid, s.date()), s.steps(), s.source());
        return Map.of("ok", true);
    }
}
