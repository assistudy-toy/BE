package com.assistudy.commonservice.support;

import com.assistudy.commonservice.global.client.UserServiceClient;
import com.assistudy.commonservice.global.client.WebRtcServiceClient;
import com.assistudy.commonservice.global.dto.response.UserInfoResponse;
import com.assistudy.shared.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;

/**
 * room 통합테스트 공통 베이스. (homework/webrtc는 각자 서비스로 분리됨)
 * Testcontainers(MySQL+Redis)로 실제 DB/캐시를 띄우고, MockMvc로 컨트롤러까지 왕복 검증한다.
 * UserServiceClient는 user-service가 없는 테스트 환경이라 Mockito mock으로 대체한다
 * (room 컨트롤러는 @LoginUser만 쓰고 @VerifiedUser는 안 써서, 인증 경로엔 영향 없음).
 * 기본으로 "요청한 id 그대로 닉네임을 만들어 돌려주는" lenient stub을 깔아두므로,
 * 개별 테스트는 특정 실패/커스텀 응답이 필요할 때만 재정의하면 된다.
 *
 * MySQL/Redis 컨테이너는 이 추상 클래스를 상속하는 모든 테스트 클래스가 공유하는 "싱글턴 컨테이너"
 * 패턴(https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers)이다.
 * {@code @Testcontainers}+{@code @Container}(JUnit5 관리 라이프사이클)를 쓰면 static 필드라도
 * 그 필드를 상속한 "각" 서브클래스의 afterAll에서 컨테이너를 stop시켜버려서, 먼저 끝난 테스트
 * 클래스가 다른 클래스가 아직 쓰고 있는 컨테이너를 죽이는 문제가 생긴다. 그래서 static 블록으로
 * 직접 한 번만 start하고 아무도 stop하지 않는다(JVM 종료 시 Ryuk 컨테이너가 정리).
 *
 * DB가 테스트 전체에서 공유되는 싱글턴 컨테이너라, 테스트 간 데이터가 안 섞이도록 클래스 레벨
 * {@code @Transactional}로 각 테스트 메서드를 트랜잭션으로 감싸고 종료 시 롤백한다
 * (MOCK 웹 환경의 MockMvc는 별도 스레드 없이 동기로 요청을 처리하므로 트랜잭션이 정상 전파됨).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("assistudy_common_test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected UserServiceClient userServiceClient;

    @MockitoBean
    protected WebRtcServiceClient webRtcServiceClient;

    protected static UserInfoResponse stubUser(Long id) {
        return new UserInfoResponse(id, "user" + id + "@test.com", "user" + id, null);
    }

    @BeforeEach
    void setUpUserServiceClientMock() {
        reset(userServiceClient);
        lenient().when(userServiceClient.getUserInfo(anyLong()))
                .thenAnswer(inv -> ApiResponse.onSuccess(stubUser(inv.getArgument(0))));
        lenient().when(userServiceClient.getUsersInfo(anyList()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    return ApiResponse.onSuccess(ids.stream().map(IntegrationTestSupport::stubUser).toList());
                });

        // 기본값: LiveKit 방 프로비저닝 성공(아무 것도 안 함). 보상 트랜잭션 테스트는
        // 개별 테스트에서 doThrow(...)로 재정의한다.
        reset(webRtcServiceClient);
        lenient().doNothing().when(webRtcServiceClient).provisionRoom(anyLong());
    }
}
