package com.assistudy.commonservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방 생성 SAGA(RoomCommandServiceImpl.createRoom)에서 "방 DB 커밋"과 "webrtc-service 호출
 * 실패 시 보상 트랜잭션(방 삭제)"을 각각 독립적으로 즉시 커밋시키기 위한 빈.
 * REQUIRES_NEW로 설정해야 클래스 레벨 @Transactional로 열려있는 바깥 트랜잭션과 무관하게
 * DB에 바로 반영된다 - 그래야 이후 webrtc-service 호출이 실패했을 때 "이미 커밋된 걸
 * 되돌리는" 진짜 보상 트랜잭션이 된다(바깥 트랜잭션 롤백에 얹혀가는 게 아니라).
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate requiresNewTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
