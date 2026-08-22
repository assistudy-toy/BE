package com.assistudy.webrtcservice.controller;

import com.assistudy.shared.response.ApiResponse;
import com.assistudy.webrtcservice.service.WebRTCService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스(common-service)가 게이트웨이를 거치지 않고 직접 호출하는 내부 전용 API.
 * common-service의 {@code /rooms/internal/**} 컨벤션과 동일하게 별도 인증 없이
 * VPC 내부 서비스 간 호출만 신뢰한다(InternalAuthFilter에서 이 경로를 제외).
 */
@RestController
@RequestMapping("/webrtc/internal")
@RequiredArgsConstructor
@Tag(name = "WebRTC Internal", description = "서비스 간 내부 호출 전용 API")
public class WebRTCInternalController {

    private final WebRTCService webRTCService;

    @PostMapping("/rooms/{roomId}")
    @Operation(summary = "LiveKit 방 프로비저닝", description = "방 생성 SAGA에서 common-service가 호출 - LiveKit 서버에 실제 방 리소스를 생성한다.")
    public ApiResponse<Void> provisionRoom(@PathVariable("roomId") Long roomId) {
        webRTCService.provisionRoom(roomId);
        return ApiResponse.onSuccess(null);
    }
}
