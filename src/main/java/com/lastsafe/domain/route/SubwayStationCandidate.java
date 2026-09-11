package com.lastsafe.domain.route;

/**
 * TAGO {@code GetKwrdFndSubwaySttnList}(키워드기반 지하철역 목록 조회) 결과 1건을 벤더
 * 중립적으로 표현한 것. 같은 역명이라도 노선별로 별도 ID를 가지므로(예: "강남" → 신분당선
 * ID 하나 + 2호선 ID 하나) {@code lineName}까지 같이 담아야 {@link SubwayStationIdResolver}가
 * 올바른 하나를 고를 수 있다.
 */
public record SubwayStationCandidate(String subwayStationId, String stationName, String lineName) {
}
