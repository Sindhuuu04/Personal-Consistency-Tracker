package com.tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

interface UserRepo extends JpaRepository<Model.User, Long> {
    Optional<Model.User> findByEmail(String email);
    Optional<Model.User> findByLinkCode(String code);
    Optional<Model.User> findByTelegramChatId(Long chatId);
    List<Model.User> findByTelegramChatIdNotNull();
}
interface ActivityRepo extends JpaRepository<Model.DailyActivity, Long> {
    List<Model.DailyActivity> findByUserIdAndActivityDate(Long userId, LocalDate d);
    Optional<Model.DailyActivity> findByUserIdAndActivityDateAndType(Long userId, LocalDate d, String type);
}
interface LeetRepo extends JpaRepository<Model.LeetCodeRecord, Long> {
    boolean existsByUserIdAndUrl(Long userId, String url);
    List<Model.LeetCodeRecord> findTop20ByUserIdOrderBySubmissionDateDescIdDesc(Long userId);
}
interface GoalRepo extends JpaRepository<Model.Goal, Long> {
    List<Model.Goal> findByUserIdOrderByIdDesc(Long userId);
}
interface ProgressRepo extends JpaRepository<Model.GoalProgress, Long> {
    Optional<Model.GoalProgress> findByGoalIdAndUpdateDate(Long goalId, LocalDate d);
    List<Model.GoalProgress> findByGoalIdInAndUpdateDate(Collection<Long> ids, LocalDate d);
}
interface EtaRepo extends JpaRepository<Model.GoalEtaHistory, Long> {
    List<Model.GoalEtaHistory> findByGoalIdOrderByChangedAtAsc(Long goalId);
}
interface FitnessRepo extends JpaRepository<Model.FitnessDaily, Long> {
    Optional<Model.FitnessDaily> findByUserIdAndActivityDate(Long userId, LocalDate d);
}
