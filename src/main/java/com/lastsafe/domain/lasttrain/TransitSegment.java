package com.lastsafe.domain.lasttrain;

import com.lastsafe.entity.TransportType;

/**
 * bufferSec: 국토부 추정 기반 버스 구간(type=BUS && isEstimated=true)에만 의미가 있다.
 * 그 외 구간은 0으로 채워도 알고리즘 결과에 영향 없음(합산 시 필터링됨).
 *
 * stationId/routeOrDirectionId: 5.1절 스냅 조회(조립 계층의 {@code AssemblyTripSnapper})에
 * 필요한 역/정류장·노선 식별자. 순수 계산 로직({@link LastTrainCalculationService})은 이 값을
 * 전혀 쓰지 않으므로, 계산 로직만 검증하는 기존 테스트가 깨지지 않도록 5-인자 생성자를 남겨두고
 * 거기선 null로 채운다.
 */
public record TransitSegment(
        TransportType type,
        int durationSec,
        LastDeparture lastDeparture,
        boolean isEstimated,
        int bufferSec,
        String stationId,
        String routeOrDirectionId
) implements RouteSegment {

    public TransitSegment {
        if (type == TransportType.WALK) {
            throw new IllegalArgumentException("TransitSegment의 type은 SUBWAY 또는 BUS여야 합니다.");
        }
    }

    public TransitSegment(TransportType type, int durationSec, LastDeparture lastDeparture,
                           boolean isEstimated, int bufferSec) {
        this(type, durationSec, lastDeparture, isEstimated, bufferSec, null, null);
    }
}
