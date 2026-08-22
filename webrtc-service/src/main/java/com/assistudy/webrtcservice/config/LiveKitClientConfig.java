package com.assistudy.webrtcservice.config;

import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LiveKit 서버의 Room Service RPC(방 생성/삭제 등 실제 리소스 관리)를 호출하기 위한 클라이언트 빈.
 * 기존 {@link OpenViduService}는 로컬에서 JWT만 생성할 뿐 LiveKit 서버에 실제 요청을 보내지
 * 않았는데, 방 생성 SAGA(#40 연장)에서 방 생성 시 LiveKit 방을 실제로 프로비저닝하기 위해 추가함.
 *
 * livekit.url은 클라이언트 시그널링용 ws(s):// 스킴으로 설정돼 있는데, Room Service RPC는
 * 같은 호스트의 http(s) 엔드포인트를 사용하므로 스킴만 변환해서 재사용한다.
 */
@Configuration
@RequiredArgsConstructor
public class LiveKitClientConfig {

    private final WebRTCConfig webRTCConfig;

    @Bean
    public RoomServiceClient liveKitRoomServiceClient() {
        String httpUrl = toHttpScheme(webRTCConfig.getUrl());
        return RoomServiceClient.create(httpUrl, webRTCConfig.getApi().getKey(), webRTCConfig.getApi().getSecret());
    }

    private String toHttpScheme(String url) {
        if (url.startsWith("wss://")) {
            return "https://" + url.substring("wss://".length());
        }
        if (url.startsWith("ws://")) {
            return "http://" + url.substring("ws://".length());
        }
        return url;
    }
}
