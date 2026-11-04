package com.tracker;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** All JPA entities (one source of truth for dashboard + Telegram). */
public class Model {

    @Entity(name = "User") @Table(name = "users") @Getter @Setter @NoArgsConstructor
    public static class User {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        @Column(unique = true, nullable = false) private String email;
        private String passwordHash;
        private String timezone = "Asia/Kolkata";
        private Long telegramChatId;
        @Column(unique = true) private String linkCode;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    /** type = LEETCODE | GITHUB | PROFILE, score 0-100. One row per user/day/type => idempotent. */
    @Entity(name = "DailyActivity")
    @Table(name = "daily_activities", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_date", "type"}))
    @Getter @Setter @NoArgsConstructor
    public static class DailyActivity {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long userId;
        private LocalDate activityDate;
        @Column(length = 20) private String type;
        private Integer score;
        private String evidenceUrl;
        @Column(length = 1000) private String notes;
    }

    @Entity(name = "LeetCodeRecord") @Table(name = "leetcode_records") @Getter @Setter @NoArgsConstructor
    public static class LeetCodeRecord {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long userId;
        private String title;
        @Column(length = 500) private String url;
        private String difficulty;
        private String topics;
        private LocalDate submissionDate;
        private String source = "manual";
    }

    @Entity(name = "Goal") @Table(name = "goals") @Getter @Setter @NoArgsConstructor
    public static class Goal {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long userId;
        private String title;
        private String category;
        private LocalDate startDate;
        private LocalDate originalEta;   // immutable after creation
        private LocalDate currentEta;
        private String status = "ACTIVE";
        private Integer progressPercent = 0;
        private LocalDate completedDate;
    }

    @Entity(name = "GoalProgress")
    @Table(name = "goal_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"goal_id", "update_date"}))
    @Getter @Setter @NoArgsConstructor
    public static class GoalProgress {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long goalId;
        private LocalDate updateDate;
        private Integer progressPercent;
        private Double hoursSpent;
        @Column(length = 1000) private String notes;
        @Column(length = 500) private String blocker;
    }

    @Entity(name = "GoalEtaHistory") @Table(name = "goal_eta_history") @Getter @Setter @NoArgsConstructor
    public static class GoalEtaHistory {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long goalId;
        private LocalDate previousEta;
        private LocalDate newEta;
        private LocalDateTime changedAt = LocalDateTime.now();
        @Column(length = 500) private String reason;
    }

    @Entity(name = "FitnessDaily")
    @Table(name = "fitness_daily", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_date"}))
    @Getter @Setter @NoArgsConstructor
    public static class FitnessDaily {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private Long userId;
        private LocalDate activityDate;
        private Integer steps;
        private Integer targetSteps = 10000;
        private String source;
        private LocalDateTime syncedAt;
    }
}
