package com.lastsafe.external;

import java.util.List;

import com.lastsafe.domain.route.SubwayStationCandidate;

/**
 * 역명으로 지하철역 ID를 조회하는 것을 추상화한다 (TAGO {@code GetKwrdFndSubwaySttnList} 연동,
 * spec.md 3절, NFR-07). ODsay 경로 탐색 결과의 역명(ODsay 자체 ID 체계)을 TAGO
 * {@code subwayStationId}로 재매핑하는 데 쓰인다 — {@link com.lastsafe.domain.route.SubwayStationIdResolver}
 * 가 이 결과를 받아 노선명까지 대조해서 최종 ID 하나를 고른다.
 */
public interface SubwayStationLookupClient {

    /**
     * 정확히 이 역명과 일치하는 후보만 반환한다 — TAGO 원본 API는 키워드 부분일치 검색이라
     * "종로3가"를 검색하면 "종로3가(탑골공원)"까지 같이 나오는 등 다른 역까지 섞여 오는데,
     * 구현체가 그걸 걸러내고 정확히 일치하는 것만 준다(NFR-07, 벤더 특성을 인터페이스 밖으로
     * 새지 않게).
     */
    List<SubwayStationCandidate> findByExactStationName(String stationName);
}
