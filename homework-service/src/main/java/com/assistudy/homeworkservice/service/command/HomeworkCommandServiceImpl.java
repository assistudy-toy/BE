package com.assistudy.homeworkservice.service.command;

import com.assistudy.homeworkservice.converter.HomeworkConverter;
import com.assistudy.homeworkservice.dto.request.CreateHomeworkRequest;
import com.assistudy.homeworkservice.dto.request.UpdateHomeworkRequest;
import com.assistudy.homeworkservice.dto.response.CreateHomeworkResponse;
import com.assistudy.homeworkservice.entity.Homework;
import com.assistudy.homeworkservice.exception.HomeworkErrorCode;
import com.assistudy.homeworkservice.exception.HomeworkException;
import com.assistudy.homeworkservice.global.client.RoomServiceClient;
import com.assistudy.homeworkservice.global.dto.response.RoomSummaryResponse;
import com.assistudy.homeworkservice.repository.HomeworkRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HomeworkCommandServiceImpl implements HomeworkCommandService {

    private final HomeworkRepository homeworkRepository;
    private final RoomServiceClient roomServiceClient;

    @Override
    public CreateHomeworkResponse createHomework(CreateHomeworkRequest request, Long userId) {
        // 과제 생성 (한 방에 한 날짜에 여러 과제가 있을 수 있음)
        RoomSummaryResponse room = getRoomById(request.getRoomId());
        validateHomeworkCreatePermission(room, userId);

        // 새로운 과제 생성
        Homework newHomework = Homework.builder()
                .roomId(room.id())
                .date(request.getDate())
                .comment(request.getComment())
                .build();

        Homework savedHomework = homeworkRepository.save(newHomework);

        return HomeworkConverter.toCreateHomeworkResponse(savedHomework, room.name());
    }

    @Override
    public CreateHomeworkResponse updateHomework(Long homeworkId, UpdateHomeworkRequest request, Long userId) {
        Homework homework = getHomeworkById(homeworkId);
        RoomSummaryResponse room = getRoomById(homework.getRoomId());
        validateHomeworkUpdatePermission(room, userId);

        Homework updatedHomework = Homework.builder()
                .id(homework.getId())
                .roomId(homework.getRoomId())
                .date(homework.getDate())  // 기존 날짜 유지
                .comment(request.getComment() != null ? request.getComment() : homework.getComment())
                .isDeleted(homework.getIsDeleted())  // builder로 재생성하면서 soft-delete 상태가 초기화되지 않도록 유지
                .build();

        Homework savedHomework = homeworkRepository.save(updatedHomework);

        return HomeworkConverter.toCreateHomeworkResponse(savedHomework, room.name());
    }

    // ================= 내부 유틸 메서드 =================

    private RoomSummaryResponse getRoomById(Long roomId) {
        RoomSummaryResponse room = roomServiceClient.getRoom(roomId).getResult();
        if (room == null) {
            throw new HomeworkException(HomeworkErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    private Homework getHomeworkById(Long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new HomeworkException(HomeworkErrorCode.HOMEWORK_NOT_FOUND));
    }

    private void validateHomeworkCreatePermission(RoomSummaryResponse room, Long userId) {
        if (!room.hostUserId().equals(userId)) {
            throw new HomeworkException(HomeworkErrorCode.HOMEWORK_CREATE_PERMISSION_DENIED);
        }
    }

    private void validateHomeworkUpdatePermission(RoomSummaryResponse room, Long userId) {
        if (!room.hostUserId().equals(userId)) {
            throw new HomeworkException(HomeworkErrorCode.HOMEWORK_UPDATE_PERMISSION_DENIED);
        }
    }
}
