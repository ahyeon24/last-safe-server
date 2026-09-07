package com.lastsafe.domain.bus;

import java.time.LocalDateTime;

/**
 * TAGO {@code getRouteAcctoBusLcList} 폴링 1회에서 관측된 차량 위치 1건을, 통과 시각 추정에
 * 필요한 최소 정보만 남긴 도메인 표현 — 외부 API 응답 형태를 몰라도 되게 감싼다(NFR-07과
 * 같은 취지).
 */
public record VehiclePositionObservation(String vehicleNo, int nodeOrd, LocalDateTime observedAt) {
}
