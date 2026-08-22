package com.assistudy.webrtcservice.service;

import com.assistudy.webrtcservice.dto.request.CreateTokenRequest;
import com.assistudy.webrtcservice.dto.response.TokenResponse;

/**
 * WebRTC 서비스 인터페이스
 * 화상회의 관련 비즈니스 로직을 정의합니다.
 */
public interface WebRTCService {

    /**
     * 토큰을 생성합니다.
     * @param request 토큰 생성 요청 정보
     * @param userId 사용자 ID
     * @return 토큰 응답 정보
     */
    TokenResponse createToken(CreateTokenRequest request, Long userId);

    /**
     * LiveKit 서버에 실제 방 리소스를 생성한다(방 생성 SAGA에서 common-service가 호출).
     * @param roomId common-service의 room ID
     */
    void provisionRoom(Long roomId);
}
