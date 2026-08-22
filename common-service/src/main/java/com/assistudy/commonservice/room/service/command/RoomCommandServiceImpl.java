package com.assistudy.commonservice.room.service.command;

import com.assistudy.commonservice.global.client.WebRtcServiceClient;
import com.assistudy.commonservice.room.converter.RoomConverter;
import com.assistudy.commonservice.room.dto.request.CreateRoomRequest;
import com.assistudy.commonservice.room.dto.request.JoinRoomRequest;
import com.assistudy.commonservice.room.dto.request.UpdateRoomRequest;
import com.assistudy.commonservice.room.dto.response.CreateRoomResponse;
import com.assistudy.commonservice.room.entity.Room;
import com.assistudy.commonservice.room.entity.RoomParticipant;
import com.assistudy.commonservice.room.entity.enums.RoomType;
import com.assistudy.commonservice.room.exception.RoomErrorCode;
import com.assistudy.commonservice.room.exception.RoomException;
import com.assistudy.commonservice.room.repository.RoomParticipantRepository;
import com.assistudy.commonservice.room.repository.RoomRepository;
import com.assistudy.shared.event.RoomDeletedEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RoomCommandServiceImpl implements RoomCommandService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final WebRtcServiceClient webRtcServiceClient;
    private final TransactionTemplate requiresNewTransactionTemplate;

    @Override
    public CreateRoomResponse createRoom(CreateRoomRequest request, Long hostUserId) {
        // 비공개 방 조건 검증
        // 비공개 방은 비밀번호 필요
        if (!request.isValidPrivateRoom()) {
            throw new RoomException(RoomErrorCode.PRIVATE_ROOM_PASSWORD_REQUIRED);
        }
        // 방이름 중복 체크
        // 수업방 조건 확인
        if (!request.isValidClassRoom()) {
            throw new RoomException(RoomErrorCode.INVALID_ROOM_REQUEST);
        }

        String password = request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null;

        // REQUIRES_NEW로 별도 트랜잭션에 즉시 커밋 - 바깥 트랜잭션과 무관하게 DB에 반영되어야
        // 아래 webrtc-service 호출이 실패했을 때의 삭제가 "이미 커밋된 걸 되돌리는" 진짜
        // 보상 트랜잭션이 된다(단순 롤백이 아님).
        Room savedRoom = requiresNewTransactionTemplate.execute(status -> {
            Room room = request.toEntity(hostUserId, password);
            Room saved = roomRepository.save(room);
            registerHostAsParticipant(saved, hostUserId);
            return saved;
        });

        try {
            webRtcServiceClient.provisionRoom(savedRoom.getId());
        } catch (Exception e) {
            log.error("[SAGA] LiveKit 방 프로비저닝 실패 - roomId={}, 보상 트랜잭션(방 삭제) 수행: {}",
                    savedRoom.getId(), e.getMessage());
            compensateFailedRoomCreation(savedRoom.getId());
            throw new RoomException(RoomErrorCode.ROOM_PROVISIONING_FAILED);
        }

        return RoomConverter.toCreateRoomResponse(savedRoom);
    }

    /**
     * 방 생성 SAGA의 보상 트랜잭션 - webrtc-service의 LiveKit 방 생성이 실패하면
     * 이미 커밋된 Room/RoomParticipant를 별도 트랜잭션으로 되돌린다. 방이 실제로
     * 한 번도 쓰인 적 없는 상태(생성 직후 실패)라 deleteRoom()의 사용자 알림/권한
     * 검증 로직을 그대로 타지 않고 바로 soft-delete한다.
     */
    private void compensateFailedRoomCreation(Long roomId) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            roomParticipantRepository.softDeleteAllByRoomId(roomId);
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room != null) {
                room.softDelete();
                roomRepository.save(room);
            }
        });
    }

    @Override
    public void joinRoom(JoinRoomRequest request, Long userId) {
        Room room = getRoomById(request.getRoomId());

        // 방 참가 가능 여부 검증
        validateRoomNotDeleted(room);
        validatePrivateRoomPassword(room, request.getPassword());

        // 이미 참여 중인지 확인
        boolean isAlreadyJoined = roomParticipantRepository.findByRoomIdAndUserIdAndIsDeletedFalse(room.getId(), userId).isPresent();

        // 새로운 참가자인 경우에만 등록
        if (!isAlreadyJoined) {
            validateRoomCapacity(room);
            registerParticipant(room, userId);
        }
    }

    @Override
    public void leaveRoom(Long roomId, Long userId) {
        Room room = getRoomById(roomId);

        // CLASS 방에서 호스트가 나가는 경우, 방과 모든 참가자를 제거
        if (room.getType() == RoomType.CLASS && room.getHostUserId().equals(userId)) {
            roomParticipantRepository.softDeleteAllByRoomId(roomId);
            room.softDelete();
        } else {
            roomParticipantRepository.softDeleteByRoomIdAndUserId(roomId, userId);
        }
    }

    @Override
    public void updateRoom(Long roomId, UpdateRoomRequest request, Long userId) {
        Room room = getRoomById(roomId);
        validateRoomNotDeleted(room);
        validateRoomUpdatePermission(room, userId);

        // 최대 참여자 수 검증
        if (request.getMaxParticipants() != null) {
            int currentParticipants = roomParticipantRepository.countByRoomIdAndIsDeletedFalse(roomId);
            if (request.getMaxParticipants() < currentParticipants) {
                throw new RoomException(RoomErrorCode.ROOM_MAX_PARTICIPANTS_TOO_SMALL);
            }
        }

        // 비밀번호 암호화
        String encodedPassword = null;
        if (request.getPassword() != null) {
            encodedPassword = passwordEncoder.encode(request.getPassword());
        }

        // 공개 방은 비밀번호 설정 불가
        if (!room.getIsPrivate()) {
            encodedPassword = null;
        }

        // 방 정보 업데이트
        room.updateRoomInfo(
                request.getName(),
                request.getDescription(),
                request.getRules(),
                encodedPassword,
                request.getMaxParticipants()
        );
    }

    @Override
    public void deleteRoom(Long roomId, Long userId) {
        Room room = getRoomById(roomId);
        validateRoomNotDeleted(room);
        validateRoomDeletePermission(room, userId);

        // 방에 참여 중인 모든 사용자 제거 (soft delete)
        roomParticipantRepository.softDeleteAllByRoomId(roomId);

        // 방 soft delete
        room.softDelete();
        roomRepository.save(room);

        // homework-service에 room 삭제를 알림(choreography SAGA) - 트랜잭션 커밋 후에만 발행됨
        eventPublisher.publishEvent(new RoomDeletedEvent(roomId, room.getHostUserId(), LocalDateTime.now()));
    }

    // ================= 내부 유틸 메서드 =================

    private Room getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    private void validateRoomNotDeleted(Room room) {
        if (room.getIsDeleted()) {
            throw new RoomException(RoomErrorCode.ROOM_DELETED);
        }
    }

    private void validatePrivateRoomPassword(Room room, String rawPassword) {
        if (room.getIsPrivate()) {
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new RoomException(RoomErrorCode.PRIVATE_ROOM_PASSWORD_REQUIRED);
            }
            if (!passwordEncoder.matches(rawPassword, room.getPassword())) {
                throw new RoomException(RoomErrorCode.PASSWORD_NOT_MATCHED);
            }
        }
    }

    private void validateRoomCapacity(Room room) {
        int current = roomParticipantRepository.countByRoomIdAndIsDeletedFalse(room.getId());
        if (current >= room.getMaxParticipants()) {
            throw new RoomException(RoomErrorCode.ROOM_FULL);
        }
    }

    private void registerParticipant(Room room, Long userId) {
        RoomParticipant participant = RoomParticipant.builder()
                .room(room)
                .userId(userId)
                .isDeleted(false)
                .build();
        roomParticipantRepository.save(participant);
    }

    private void registerHostAsParticipant(Room savedRoom, Long hostUserId) {
        RoomParticipant host = RoomParticipant.builder()
                .room(savedRoom)
                .userId(hostUserId)
                .isDeleted(false)
                .build();
        roomParticipantRepository.save(host);
    }

    private void validateRoomUpdatePermission(Room room, Long userId) {
        if (!room.getHostUserId().equals(userId)) {
            throw new RoomException(RoomErrorCode.ROOM_UPDATE_PERMISSION_DENIED);
        }
    }

    private void validateRoomDeletePermission(Room room, Long userId) {
        if (!room.getHostUserId().equals(userId)) {
            throw new RoomException(RoomErrorCode.ROOM_DELETE_PERMISSION_DENIED);
        }
    }
}
