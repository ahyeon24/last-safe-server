package com.lastsafe.external.seoulmetro;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서울교통공사_역외부코드로 지하철 막차 시간표 검색 API(data.go.kr) 접속 설정 (spec.md 3절,
 * 2026-09-07 ODsay에서 전환 채택). 실제 키는 절대 코드/커밋에 하드코딩하지 않는다(CLAUDE.md
 * 보안 원칙) — 로컬은 {@code application-local.properties}(gitignore됨), 배포는 환경변수
 * {@code SEOUL_METRO_SERVICE_KEY}로 주입.
 *
 * ⚠️ 아직 실제 클라이언트 구현체는 없음 — data.go.kr 상세 페이지는 파라미터 한글 라벨만
 * 공개하고 실제 쿼리 파라미터명·엔드포인트는 "활용신청" 후 받는 가이드 문서에만 있어서,
 * 검색만으로 ODsay 때처럼 "최선의 추정" 구현조차 정직하게 못 만드는 상태다(추정 근거가
 * UI 라벨뿐이라 실제 파라미터명일 가능성이 낮음). 아현님이 이 데이터셋 활용신청을 하고
 * 가이드 문서를 받으면, 그걸 보고 실제 클라이언트를 만들 것 — 그때까지는 시크릿 배관만
 * 미리 준비해둔다.
 */
@ConfigurationProperties(prefix = "seoul-metro")
public record SeoulMetroProperties(String serviceKey) {
}
