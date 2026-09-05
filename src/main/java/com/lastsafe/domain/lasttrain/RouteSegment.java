package com.lastsafe.domain.lasttrain;

/**
 * 막차 역산 알고리즘 입력 구간. WALK와 SUBWAY/BUS(교통수단)는 처리 방식이 완전히 달라
 * ({@link WalkSegment}는 pending_walk_sec 누적만, {@link TransitSegment}는 막차 시각 비교)
 * sealed interface로 분리해 타입 레벨에서 강제한다.
 */
public sealed interface RouteSegment permits WalkSegment, TransitSegment {
}
