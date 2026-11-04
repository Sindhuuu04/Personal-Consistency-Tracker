package com.tracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.tracker.Model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;

/** Long-polling Telegram bot. It calls the same TrackerService as the REST API => one source of truth. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {
    @Value("${telegram.token:}") String token;
    @Value("${telegram.morning-hour:8}") int morning;
    @Value("${telegram.evening-hour:20}") int evening;
    @Value("${telegram.final-hour:22}") int last;

    final TrackerService t; final UserRepo users;
    final RestTemplate rest = mk();
    long offset = 0;
    final Set<String> sent = new HashSet<>(); // duplicate-send protection (in-memory)

    static final String HELP = """
        Consistency Tracker bot
        /link <code>  link this chat (code is in the dashboard)
        /today  today's status and score
        /leetcode <title> | <url> | <difficulty>
        /github <what you shipped>
        /profile <0-100> [note]  profile activity
        /goal  list active goals
        /update <goalId> <percent> [note]
        /steps [number]
        /streak   /report   /help""";

    static RestTemplate mk() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000); f.setReadTimeout(35_000);
        return new RestTemplate(f);
    }

    String api(String m) { return "https://api.telegram.org/bot" + token + "/" + m; }
    String safe(Exception e) { return String.valueOf(e.getMessage()).replace(token, "***"); }

    @Scheduled(fixedDelay = 1000)
    void poll() {
        if (token.isBlank()) return;
        try {
            JsonNode r = rest.getForObject(api("getUpdates") + "?timeout=20&offset=" + offset, JsonNode.class);
            if (r == null) return;
            for (JsonNode u : r.path("result")) {
                offset = u.path("update_id").asLong() + 1;
                handle(u.path("message"));
            }
        } catch (Exception e) {
            log.warn("Telegram poll failed: {}", safe(e));
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    public void send(long chat, String text) {
        try { rest.postForObject(api("sendMessage"), Map.of("chat_id", chat, "text", text), String.class); }
        catch (Exception e) { log.warn("Telegram send failed: {}", safe(e)); }
    }

    void handle(JsonNode m) {
        String text = m.path("text").asText("").trim();
        long chat = m.path("chat").path("id").asLong();
        if (text.isEmpty() || chat == 0) return;
        String[] p = text.split("\\s+", 2);
        String cmd = p[0].toLowerCase().split("@")[0];
        String arg = p.length > 1 ? p[1].trim() : "";

        if (cmd.equals("/start") || cmd.equals("/help")) { send(chat, HELP); return; }
        if (cmd.equals("/link")) {
            users.findByLinkCode(arg).ifPresentOrElse(u -> {
                u.setTelegramChatId(chat); users.save(u);
                send(chat, "✅ Linked! Try /today");
            }, () -> send(chat, "Invalid code. Copy it from the dashboard."));
            return;
        }
        User u = users.findByTelegramChatId(chat).orElse(null);
        if (u == null) { send(chat, "Link first: /link <code> (shown on your dashboard)."); return; }
        try { send(chat, reply(u.getId(), cmd, arg)); }
        catch (IllegalArgumentException e) { send(chat, "⚠️ " + e.getMessage()); }
        catch (Exception e) { log.error("Command failed", e); send(chat, "Something went wrong."); }
    }

    String reply(Long id, String cmd, String arg) {
        LocalDate d = t.today(id);
        switch (cmd) {
            case "/today": return t.summary(id);
            case "/streak": return t.streakText(id);
            case "/report": return t.weekly(id);
            case "/goal": return t.goalsText(id);
            case "/leetcode": {
                if (arg.isEmpty()) return "Usage: /leetcode Two Sum | https://leetcode.com/problems/two-sum | Easy";
                String[] x = arg.split("\\|");
                var r = t.addLeet(id, d, x[0].trim(), x.length > 1 ? x[1].trim() : null, x.length > 2 ? x[2].trim() : null, null);
                return Boolean.TRUE.equals(r.get("duplicate")) ? "Already recorded ✔" : "🧩 Recorded: " + x[0].trim();
            }
            case "/github": {
                if (arg.isEmpty()) return "Usage: /github what you shipped (only meaningful work counts)";
                t.setActivity(id, d, "GITHUB", 100, null, arg);
                return "💻 GitHub contribution recorded";
            }
            case "/profile": {
                String[] x = arg.split("\\s+", 2);
                try { t.setActivity(id, d, "PROFILE", Integer.parseInt(x[0]), null, x.length > 1 ? x[1] : null); }
                catch (NumberFormatException e) { return "Usage: /profile 0-100 [note]"; }
                return "📣 Profile activity saved";
            }
            case "/update": {
                String[] x = arg.split("\\s+", 3);
                if (x.length < 2) return "Usage: /update <goalId> <percent> [note]";
                try { t.addProgress(id, Long.parseLong(x[0]), d, Integer.parseInt(x[1]), null, x.length > 2 ? x[2] : null, null); }
                catch (NumberFormatException e) { return "Goal id and percent must be numbers"; }
                return "🎯 Goal updated\n" + t.goalsText(id);
            }
            case "/steps": {
                if (arg.isEmpty()) return t.summary(id);
                try { t.setSteps(id, d, Integer.parseInt(arg.replace(",", "")), "telegram"); }
                catch (NumberFormatException e) { return "Usage: /steps 8500"; }
                return "👟 Steps saved\n" + t.summary(id);
            }
            default: return "Unknown command. /help";
        }
    }

    /** Hourly: morning briefing, evening + final reminders, Sunday weekly report. */
    @Scheduled(cron = "0 0 * * * *")
    void reminders() {
        if (token.isBlank()) return;
        if (sent.size() > 2000) sent.clear();
        for (User u : users.findByTelegramChatIdNotNull()) {
            try {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(u.getTimezone()));
                int h = now.getHour();
                if (!sent.add(u.getId() + "-" + now.toLocalDate() + "-" + h)) continue;
                long chat = u.getTelegramChatId();
                if (h == morning) send(chat, "☀️ Good morning!\n" + t.summary(u.getId()));
                if (h == evening || h == last) {
                    var s = t.score(u.getId(), now.toLocalDate());
                    if (!s.pending().isEmpty())
                        send(chat, (h == last ? "⏰ Last call: " : "🔔 Still open today: ") + String.join(", ", s.pending())
                            + "\n\nLog with /leetcode, /github, /update, /steps");
                    for (var g : t.goalViews(u.getId()))
                        if ("ACTIVE".equals(g.get("status")) && (Boolean.TRUE.equals(g.get("behind")) || ((Number) g.get("daysOverdue")).longValue() > 0)) {
                            send(chat, "⚠️ Goal at risk: " + g.get("title") + " (" + g.get("progress") + "% vs " + g.get("expectedCurrent") + "% expected)");
                            break;
                        }
                }
                if (h == evening && now.getDayOfWeek() == DayOfWeek.SUNDAY) send(chat, t.weekly(u.getId()));
            } catch (Exception e) { log.warn("Reminder failed for user {}: {}", u.getId(), safe(e)); }
        }
    }
}
