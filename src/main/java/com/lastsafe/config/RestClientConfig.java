package com.lastsafe.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * {@code RestClient.Builder}가 이 프로젝트의 {@code spring-boot-starter-webmvc} 조합에서는
 * 자동 설정으로 등록되지 않아서(실제로 겪은 문제 — ODsay 클라이언트 빈 생성 시
 * NoSuchBeanDefinitionException), 여기서 직접 빈으로 등록한다. 외부 API 클라이언트(ODsay 등)마다
 * 이 빈을 주입받아 {@code .baseUrl(...)}로 커스터마이즈해서 쓴다.
 *
 * {@code prototype} 스코프인 이유: {@code Builder}는 가변 객체라 여러 클라이언트가 같은
 * 싱글턴 인스턴스를 공유하면 서로의 {@code baseUrl} 설정을 덮어쓸 수 있다 — 주입받을 때마다
 * 새 인스턴스를 받아야 안전하다(Spring Boot의 자동 설정 {@code restClientBuilder} 빈도 같은
 * 이유로 prototype이다).
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
