package com.assistudy.webrtcservice.config;

import com.assistudy.shared.filter.InternalAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public InternalAuthFilter internalAuthFilter() {
        // /webrtc/internal/**은 common-service가 서비스 간 직접 호출(게이트웨이를 거치지 않음)로
        // 부르는 경로라 X-User-Id 헤더가 없음 - common-service의 /rooms/internal 처리와 동일한 패턴
        return new InternalAuthFilter(Set.of("/webrtc/internal"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(internalAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
