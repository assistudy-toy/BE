package com.assistudy.commonservice.global.client;

import com.assistudy.shared.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * webrtc-service 원격 호출 전용 Feign 인터페이스.
 * {@link UserServiceFeignClient}와 동일하게 {@link WebRtcServiceClientWrapper}만 직접 주입받아 사용한다.
 */
@FeignClient(name = "webrtc-service")
public interface WebRtcServiceFeignClient {

    @PostMapping("/webrtc/internal/rooms/{roomId}")
    ApiResponse<Void> provisionRoom(@PathVariable("roomId") Long roomId);
}
