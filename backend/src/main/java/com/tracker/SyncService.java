package com.tracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.tracker.Model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;

/** Reads PUBLIC GitHub pushes and LeetCode accepted submissions. No passwords or tokens needed. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    static final int DAYS = 7; // look-back window

    final UserRepo users; final ActivityRepo acts; final LeetRepo leet; final TrackerService t;
    final RestTemplate rest = mk();

    static RestTemplate mk() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000); f.setReadTimeout(15_000);
        return new RestTemplate(f);
    }

    public Map<String, String> sync(Long uid) {
        User u = users.findById(uid).orElseThrow();
        Map<String, String> r = new LinkedHashMap<>();
        r.put("github", TrackerService.blank(u.getGithubUsername()) ? "not linked (add username in Settings)" : github(u));
        r.put("leetcode", TrackerService.blank(u.getLeetcodeUsername()) ? "not linked (add username in Settings)" : leetcode(u));
        return r;
    }

    boolean done(Long uid, LocalDate d, String type) {
        return acts.findByUserIdAndActivityDateAndType(uid, d, type).map(a -> a.getScore() != null && a.getScore() >= 100).orElse(false);
    }

    String github(User u) {
        try {
            ZoneId z = ZoneId.of(u.getTimezone());
            LocalDate from = LocalDate.now(z).minusDays(DAYS);
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", "consistency-tracker"); h.set("Accept", "application/vnd.github+json");
            JsonNode ev = rest.exchange("https://api.github.com/users/" + u.getGithubUsername() + "/events/public?per_page=100",
                    HttpMethod.GET, new HttpEntity<>(h), JsonNode.class).getBody();
            Map<LocalDate, Integer> pushes = new TreeMap<>();
            Map<LocalDate, Set<String>> repos = new HashMap<>();
            if (ev != null) for (JsonNode e : ev) {
                if (!"PushEvent".equals(e.path("type").asText())) continue;
                LocalDate d = Instant.parse(e.path("created_at").asText()).atZone(z).toLocalDate();
                if (d.isBefore(from)) continue;
                pushes.merge(d, 1, Integer::sum);
                repos.computeIfAbsent(d, k -> new TreeSet<>()).add(e.path("repo").path("name").asText());
            }
            int created = 0;
            for (var en : pushes.entrySet()) {
                LocalDate d = en.getKey();
                if (done(u.getId(), d, "GITHUB")) continue; // never overwrite a manual entry
                t.setActivity(u.getId(), d, "GITHUB", 100, "https://github.com/" + u.getGithubUsername(),
                        "Auto-sync: " + en.getValue() + " push(es) to " + String.join(", ", repos.get(d)));
                created++;
            }
            return pushes.size() + " active day(s) in last " + DAYS + " (" + created + " newly marked)";
        } catch (HttpClientErrorException ex) {
            return "GitHub replied " + ex.getStatusCode().value() + " (check the username, or you hit the hourly rate limit)";
        } catch (Exception ex) { return "failed: " + ex.getMessage(); }
    }

    String leetcode(User u) {
        try {
            ZoneId z = ZoneId.of(u.getTimezone());
            LocalDate from = LocalDate.now(z).minusDays(DAYS);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON); h.set("Referer", "https://leetcode.com"); h.set("User-Agent", "Mozilla/5.0");
            Map<String, Object> body = Map.of(
                    "query", "query($u:String!,$l:Int){recentAcSubmissionList(username:$u,limit:$l){title titleSlug timestamp}}",
                    "variables", Map.of("u", u.getLeetcodeUsername(), "l", 30));
            JsonNode r = rest.exchange("https://leetcode.com/graphql", HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class).getBody();
            JsonNode list = r == null ? null : r.path("data").path("recentAcSubmissionList");
            if (list == null || !list.isArray()) return "no data (check the username, and that your profile is public)";
            List<JsonNode> items = new ArrayList<>();
            list.forEach(items::add);
            Collections.reverse(items); // oldest first so the first acceptance gets the credit
            int created = 0;
            for (JsonNode s : items) {
                LocalDate d = Instant.ofEpochSecond(s.path("timestamp").asLong()).atZone(z).toLocalDate();
                if (d.isBefore(from)) continue;
                String url = "https://leetcode.com/problems/" + s.path("titleSlug").asText() + "/";
                if (leet.existsByUserIdAndUrl(u.getId(), url)) continue; // retries are not new problems
                LeetCodeRecord rec = new LeetCodeRecord();
                rec.setUserId(u.getId()); rec.setTitle(s.path("title").asText()); rec.setUrl(url);
                rec.setSubmissionDate(d); rec.setSource("leetcode-sync");
                leet.save(rec);
                if (!done(u.getId(), d, "LEETCODE")) t.setActivity(u.getId(), d, "LEETCODE", 100, url, rec.getTitle());
                created++;
            }
            return created + " new problem(s) found in last " + DAYS + " days";
        } catch (Exception ex) { return "failed: " + ex.getMessage(); }
    }

    @Scheduled(cron = "0 5 * * * *")
    void hourly() {
        for (User u : users.findAll()) {
            if (TrackerService.blank(u.getGithubUsername()) && TrackerService.blank(u.getLeetcodeUsername())) continue;
            try { sync(u.getId()); } catch (Exception e) { log.warn("Sync failed for user {}: {}", u.getId(), e.getMessage()); }
        }
    }
}