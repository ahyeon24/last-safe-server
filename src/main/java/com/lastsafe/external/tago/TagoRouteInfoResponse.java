package com.lastsafe.external.tago;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * TAGO(국토교통부) 버스노선정보조회 서비스 {@code getRouteInfoIem} 응답
 * (오픈API활용가이드_국토교통부(TAGO)_버스노선정보v1.0, 2026-09-07 실제 문서로 확인 — 필드명은
 * 검증됨. 단, 실제 네트워크 호출로 이 DTO가 정확히 맞는지는 아직 확인 못 함, 키 받으면 확인).
 *
 * intervaltime/intervalsattime/intervalsuntime(배차간격)은 5.2절 계산식에서 아직 안 씀 —
 * 막차 시각(endvehicletime) 자체는 요일 무관 고정값으로 가정하기 때문(spec.md 5.2절 "미확정"
 * 항목 참고). DTO엔 남겨둔다, 나중에 배차간격을 실제로 쓸 일이 생기면 바로 쓸 수 있게.
 */
record TagoRouteInfoResponse(Response response) {

    record Response(Header header, Body body) {
    }

    record Header(String resultCode, String resultMsg) {
    }

    record Body(Items items) {
    }

    record Items(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Item> item
    ) {
    }

    /** endvehicletime/startvehicletime: "HHMM" 4자리(예: "2230" = 22:30). */
    record Item(String routeid, String endvehicletime, String startvehicletime,
                String intervaltime, String intervalsattime, String intervalsuntime) {
    }
}
