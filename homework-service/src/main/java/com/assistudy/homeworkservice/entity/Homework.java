package com.assistudy.homeworkservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "homework")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Homework {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // room-service(common-service) 소유의 room을 참조. 물리 FK 없음(다른 DB) -
    // 존재/권한 검증은 RoomServiceClient로 매번 확인한다.
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    private LocalDate date;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    // room 삭제 이벤트(RoomDeletedEvent) 소비 시 일괄 soft-delete됨 (choreography SAGA, #40)
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
