package com.assistudy.webrtcservice.service;

import com.assistudy.webrtcservice.converter.WebRTCConverter;
import com.assistudy.webrtcservice.dto.request.CreateTokenRequest;
import com.assistudy.webrtcservice.dto.response.TokenResponse;
import com.assistudy.webrtcservice.exception.WebRTCErrorCode;
import com.assistudy.webrtcservice.exception.WebRTCException;
import com.assistudy.webrtcservice.global.client.RoomServiceClient;
import com.assistudy.webrtcservice.global.client.UserServiceClient;
import com.assistudy.webrtcservice.global.dto.response.RoomSummaryResponse;
import com.assistudy.webrtcservice.global.dto.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;

/**
 * WebRTC 서비스 구현 클래스
 * 화상회의 관련 비즈니스 로직을 처리합니다.
 * OpenVidu 3.x LiveKit 기반으로 동작하며, room 데이터는 common-service를 RoomServiceClient로 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebRTCServiceImpl implements WebRTCService {

    private final OpenViduService openViduService;
    private final RoomServiceClient roomServiceClient;
    private final UserServiceClient userServiceClient;
    private final io.livekit.server.RoomServiceClient liveKitRoomServiceClient;

    @Override
    public TokenResponse createToken(CreateTokenRequest request, Long userId) {
        // 1. 방 존재 여부 확인
        RoomSummaryResponse room = getRoomById(request.getRoomId());

        // 2. 방 상태 확인
        validateRoomStatus(room);

        // 3. 사용자 정보 조회
        UserInfoResponse userInfo = getUserInfo(userId);

        // 4. 방 참가 권한 검증 (이미 roomParticipant인지 확인)
        validateParticipantAccess(room, userId);

        // 5. LiveKit 토큰 발급 (OpenVidu 3.x) - 고유 participant identity 사용
        String token = openViduService.createToken(room.id(), userInfo);

        // 6. 현재 참가자 수 조회 (common-service 기준)
        int currentParticipants = getParticipantCount(room.id());

        // 사용자 닉네임을 참가자 이름으로 사용
        String participantName = userInfo.getNickname();

        return WebRTCConverter.toTokenResponse(room, token, participantName, currentParticipants);
    }

    @Override
    public void provisionRoom(Long roomId) {
        String roomName = "room_" + roomId;
        try {
            Response<livekit.LivekitModels.Room> response = liveKitRoomServiceClient.createRoom(roomName).execute();
            if (!response.isSuccessful()) {
                log.error("[LiveKit] 방 생성 실패 - roomName={}, httpStatus={}", roomName, response.code());
                throw new WebRTCException(WebRTCErrorCode.ROOM_PROVISIONING_FAILED);
            }
            log.info("[LiveKit] 방 생성 성공 - roomName={}", roomName);
        } catch (IOException e) {
            log.error("[LiveKit] 방 생성 요청 실패(네트워크) - roomName={}, cause={}", roomName, e.getMessage());
            throw new WebRTCException(WebRTCErrorCode.ROOM_PROVISIONING_FAILED);
        }
    }

    // ================= 내부 유틸 메서드 =================

    private RoomSummaryResponse getRoomById(Long roomId) {
        RoomSummaryResponse room = roomServiceClient.getRoom(roomId).getResult();
        if (room == null) {
            throw new WebRTCException(WebRTCErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    private UserInfoResponse getUserInfo(Long userId) {
        UserInfoResponse userInfo = userServiceClient.getUserInfo(userId).getResult();
        if (userInfo == null) {
            throw new WebRTCException(WebRTCErrorCode.USER_NOT_FOUND);
        }
        return userInfo;
    }

    private void validateRoomStatus(RoomSummaryResponse room) {
        if (!room.isActive()) {
            throw new WebRTCException(WebRTCErrorCode.ROOM_INACTIVE);
        }

        if (room.isDeleted()) {
            throw new WebRTCException(WebRTCErrorCode.ROOM_DELETED);
        }
    }

    private void validateParticipantAccess(RoomSummaryResponse room, Long userId) {
        boolean isParticipant = Boolean.TRUE.equals(roomServiceClient.checkParticipant(room.id(), userId).getResult());
        if (!isParticipant) {
            throw new WebRTCException(WebRTCErrorCode.USER_NOT_PARTICIPANT);
        }
    }

    private int getParticipantCount(Long roomId) {
        Integer count = roomServiceClient.countParticipants(roomId).getResult();
        return count != null ? count : 0;
    }
}
