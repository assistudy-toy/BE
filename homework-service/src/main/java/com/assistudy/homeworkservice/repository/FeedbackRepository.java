package com.assistudy.homeworkservice.repository;

import com.assistudy.homeworkservice.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    // soft-delete된 피드백 제외
    List<Feedback> findByHomeworkIdAndIsDeletedFalseOrderByDateDesc(Long homeworkId);

    // 여러 숙제의 피드백을 한 번에 조회 (숙제마다 반복 호출하는 N+1 방지용, soft-delete된 피드백 제외)
    List<Feedback> findByHomeworkIdInAndIsDeletedFalseOrderByDateDesc(List<Long> homeworkIds);

    // 특정 방, 특정 날짜, 특정 사용자의 피드백 조회 (soft-delete된 피드백 제외)
    @Query("SELECT f FROM Feedback f JOIN FETCH f.homework h WHERE h.roomId = :roomId AND f.userId = :userId AND DATE(f.date) = :date AND f.isDeleted = false")
    List<Feedback> findByRoomIdAndDateAndUserId(@Param("roomId") Long roomId,
                                                @Param("date") LocalDate date,
                                                @Param("userId") Long userId);

    // 특정 방, 특정 날짜의 모든 피드백 조회 (soft-delete된 피드백 제외)
    @Query("SELECT f FROM Feedback f JOIN FETCH f.homework h WHERE h.roomId = :roomId AND DATE(f.date) = :date AND f.isDeleted = false")
    List<Feedback> findByRoomIdAndDate(@Param("roomId") Long roomId, @Param("date") LocalDate date);

    // 특정 방, 특정 사용자의 모든 피드백 조회 (soft-delete된 피드백 제외)
    @Query("SELECT f FROM Feedback f JOIN FETCH f.homework h WHERE h.roomId = :roomId AND f.userId = :userId AND f.isDeleted = false")
    List<Feedback> findByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);

    // 특정 숙제, 특정 사용자의 피드백 조회 (soft-delete된 피드백은 제외 - 재작성 가능해야 함)
    Optional<Feedback> findByHomeworkIdAndUserIdAndIsDeletedFalse(Long homeworkId, Long userId);

    // 방 삭제 이벤트(RoomDeletedEvent) 소비 시 해당 방의 피드백 전체 soft-delete
    // (Feedback은 roomId를 직접 안 갖고 homework를 통해서만 접근 - 조건부 UPDATE라 멱등)
    @Modifying
    @Query("UPDATE Feedback f SET f.isDeleted = true WHERE f.homework.roomId = :roomId AND f.isDeleted = false")
    void softDeleteAllByHomeworkRoomId(@Param("roomId") Long roomId);
}
