package com.lastsafe.domain.lasttrain;

import com.lastsafe.entity.TransportType;

/**
 * bufferSec: 국토부 추정 기반 버스 구간(type=BUS && isEstimated=true)에만 의미가 있다.
 * 그 외 구간은 0으로 채워도 알고리즘 결과에 영향 없음(합산 시 필터링됨).
 */
public record TransitSegment(
        TransportType type,
        int durationSec,
        LastDeparture lastDeparture,
        boolean isEstimated,
        int bufferSec
) implements RouteSegment {

    public TransitSegment {
        if (type == TransportType.WALK) {
            throw new IllegalArgumentException("TransitSegment의 type은 SUBWAY 또는 BUS여야 합니다.");
        }
    }
}
