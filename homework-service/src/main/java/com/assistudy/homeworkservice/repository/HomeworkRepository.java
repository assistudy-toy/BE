package com.assistudy.homeworkservice.repository;

import com.assistudy.homeworkservice.entity.Homework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    // 특정 방의 숙제 목록 조회 (soft-delete된 과제 제외)
    List<Homework> findByRoomIdAndIsDeletedFalseOrderByDateDesc(Long roomId);

    // 여러 방의 숙제 목록을 한 번에 조회 (방마다 반복 호출하는 N+1 방지용, soft-delete된 과제 제외)
    List<Homework> findByRoomIdInAndIsDeletedFalseOrderByDateDesc(List<Long> roomIds);

    // 특정 방과 날짜의 과제 조회 (한 방에 한 날짜에는 과제가 여러 개 존재할 수 있음, soft-delete된 과제 제외)
    @Query("SELECT h FROM Homework h WHERE h.roomId = :roomId AND DATE(h.date) = :date AND h.isDeleted = false ORDER BY h.id ASC")
    List<Homework> findAllByRoomIdAndDate(@Param("roomId") Long roomId, @Param("date") LocalDate date);

    // 방 삭제 이벤트(RoomDeletedEvent) 소비 시 해당 방의 과제 전체 soft-delete (조건부 UPDATE라 멱등)
    @Modifying
    @Query("UPDATE Homework h SET h.isDeleted = true WHERE h.roomId = :roomId AND h.isDeleted = false")
    void softDeleteAllByRoomId(@Param("roomId") Long roomId);
}
