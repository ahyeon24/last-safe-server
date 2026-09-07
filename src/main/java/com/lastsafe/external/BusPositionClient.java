package com.lastsafe.external;

import java.util.List;

import com.lastsafe.domain.bus.VehiclePositionObservation;

/**
 * 노선의 현재 운행 차량 위치 조회를 추상화한다 (TAGO {@code getRouteAcctoBusLcList} 연동,
 * spec.md 5.3절, NFR-07). 폴링 스케줄러가 주기적으로 호출해서
 * {@code com.lastsafe.entity.BusVehiclePosition}으로 원시 보존하고,
 * {@code com.lastsafe.domain.bus.BusNodePassageEstimator}가 그 기록들을 나중에 배치로 가공한다.
 */
public interface BusPositionClient {

    List<VehiclePositionObservation> findVehiclePositions(String cityCode, String routeId);
}
