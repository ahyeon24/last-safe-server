package com.lastsafe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * spec.md 5.2절 버스 막차 1단계 추정 계산식에 쓰는 평균 속도. **정확한 값은 미확정**(10절 —
 * 심야 시간대 속도 괴리 이슈와 연결됨), 코드 배포 없이 조정할 수 있게 설정값으로 뺐다.
 * 기본값(초당 10m, 시속 36km)은 도심 버스 정차 포함 가정치 — 기존 테스트
 * ({@code BusStopLastDepartureEstimatorTest})에서 쓰던 것과 동일한 잠정치를 그대로 씀.
 */
@ConfigurationProperties(prefix = "bus-estimate")
public record BusEstimateProperties(Double averageSpeedMetersPerSecond) {

    private static final double DEFAULT_AVERAGE_SPEED_MPS = 10.0;

    public BusEstimateProperties {
        if (averageSpeedMetersPerSecond == null) {
            averageSpeedMetersPerSecond = DEFAULT_AVERAGE_SPEED_MPS;
        }
    }
}
