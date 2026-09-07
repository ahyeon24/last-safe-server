package com.lastsafe.external.datagokr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털(data.go.kr) 일반인증키 — TAGO(국토교통부) 계열 API들이 전부 이 하나의
 * {@code serviceKey}를 공유해서 쓴다(TAGO 지하철정보 활용가이드 "요청 메시지 명세" 확인,
 * 2026-09-07). 실제 값은 절대 코드/커밋에 하드코딩하지 않는다(CLAUDE.md 보안 원칙) — 로컬은
 * {@code application-local.properties}(gitignore됨), 배포는 환경변수
 * {@code DATAGOKR_SERVICE_KEY}로 주입.
 */
@ConfigurationProperties(prefix = "datagokr")
public record DataGoKrProperties(String serviceKey) {
}
