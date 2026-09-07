package com.lastsafe.domain.lasttrain;

import java.time.LocalDateTime;

/**
 * 교통 구간 하나에 대해 "데드라인 이하 중 가장 늦은 실제(또는 추정) 배차"를 찾는다
 * (spec.md 5.1절). 지하철이든 버스든, 서울이든 경기든 실제 조회(외부 API 호출)는
 * 조립 계층이 구현해서 주입한다 — {@link LastTrainCalculationService}는 이 인터페이스
 * 하나만 알면 되고, 어떤 벤더를 어떻게 부르는지는 몰라도 된다.
 */
public interface TripSnapper {

    SnapResult findLatestTripAtOrBefore(TransitSegment segment, LocalDateTime deadline);

    /** 스냅을 아예 안 하는 기본값 — 항상 ScheduleUnavailable을 반환해 계산값을 그대로 통과시킨다. */
    static TripSnapper none() {
        return (segment, deadline) -> new SnapResult.ScheduleUnavailable();
    }
}
