package com.lastsafe.domain.route;

import com.lastsafe.entity.TransportType;

/**
 * ODsay {@code searchPubTransPathT} 경로의 구간 1개를 벤더 중립적으로 표현한 것 (spec.md
 * 3절, 2026-09-09 실제 호출로 확인). WALK 구간은 실제 응답에 지명·좌표·노선 정보가 아예
 * 없다(2026-09-09 실호출로 확인) — startName/endName/좌표/lineName/direction/busStopArsId가
 * 전부 null이라는 뜻.
 *
 * ⚠️ [2026-09-09 발견] 여기 담긴 정류소/역 식별자는 전부 **ODsay 자체 ID 체계**다.
 * TAGO {@code subwayStationId}(예: "MTRS11133")나 서울시 버스 API가 요구하는 역/정류소
 * 식별자와 다른 번호 체계라, 이 값을 곧바로 {@code TransitSegment.stationId}로 쓸 수 없다
 * — 지하철은 역명 기반, 버스는 {@code busStopArsId}(서울시 API의 {@code arsId}와 동일
 * 개념으로 확인됨)를 매개로 다시 매핑하는 조립 계층 로직이 별도로 필요하다(spec.md 10절
 * 미해결로 추가).
 */
public record PathLeg(
        TransportType type,
        int durationSec,
        String startName,
        String endName,
        Double startLat,
        Double startLon,
        Double endLat,
        Double endLon,
        String lineName,
        String direction,
        String busStopArsId
) {
}
