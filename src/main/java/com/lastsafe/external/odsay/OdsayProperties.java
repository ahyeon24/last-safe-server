package com.lastsafe.external.odsay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ODsay API 접속 설정. 실제 키는 절대 코드/커밋에 하드코딩하지 않는다(CLAUDE.md 보안 원칙) —
 * 로컬은 {@code application-local.properties}(gitignore됨), 배포는 환경변수
 * {@code ODSAY_API_KEY}로 주입한다(application.properties의 {@code odsay.api-key=${ODSAY_API_KEY:}}
 * 참고).
 */
@ConfigurationProperties(prefix = "odsay")
public record OdsayProperties(String apiKey, String baseUrl) {

    private static final String DEFAULT_BASE_URL = "https://api.odsay.com/v1/api";

    public OdsayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }
}
