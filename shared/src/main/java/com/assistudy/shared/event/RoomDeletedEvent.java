package com.assistudy.shared.event;

import java.time.LocalDateTime;

/**
 * common-service에서 room 삭제 커밋 이후 발행하는 Kafka 이벤트.
 * homework-service가 구독해서 관련 Homework/Feedback을 정리한다(choreography SAGA).
 */
public record RoomDeletedEvent(
        Long roomId,
        Long hostUserId,
        LocalDateTime deletedAt
) {
}
