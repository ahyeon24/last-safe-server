package com.lastsafe.domain.bus;

import java.time.LocalDateTime;

/**
 * {@link BusNodePassageEstimator}의 결과 — {@code com.lastsafe.entity.BusNodePassage}로
 * 저장되기 전의 순수 계산 결과(routeId/nodeId/serviceDate는 호출 쪽 문맥이라 여기 안 담음).
 *
 * isObserved=true면 그 정류소 순번(nodeOrd)에서 직접 관측된 것(lower==upper). false면
 * 직전 관측(아직 도착 전)과 직후 관측(이미 통과함) 사이로 보간한 것 — 실제 통과 시각은
 * lower와 upper 사이 어딘가다.
 */
public record NodePassageEstimate(
        String vehicleNo, LocalDateTime passageTimeLower, LocalDateTime passageTimeUpper, boolean isObserved) {
}
