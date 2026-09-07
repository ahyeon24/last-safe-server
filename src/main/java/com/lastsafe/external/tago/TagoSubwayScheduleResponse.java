package com.lastsafe.external.tago;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * TAGO(국토교통부) 지하철정보 서비스 {@code GetSubwaySttnAcctoSchdulList} 응답
 * (오픈API활용가이드_국토교통부(TAGO)_지하철정보v1.1, 2026-09-07 실제 문서로 확인 — 필드명은
 * 검증됨. 단, 실제 네트워크 호출로 이 DTO가 정확히 맞는지는 아직 확인 못 함, 키 받으면 확인).
 *
 * items.item: 결과가 1건일 때 TAGO가 배열이 아니라 단일 객체로 JSON을 준다고 알려진 공공데이터
 * 포털 공통 특성이 있어서 {@code @JsonFormat(with = ACCEPT_SINGLE_VALUE_AS_ARRAY)}로 방어함
 * (서울시버스 API 등에서도 흔한 문제, spec.md 참고 사례와 같은 종류의 함정).
 */
record TagoSubwayScheduleResponse(Response response) {

    record Response(Header header, Body body) {
    }

    record Header(String resultCode, String resultMsg) {
    }

    record Body(Items items, int numOfRows, int pageNo, int totalCount) {
    }

    record Items(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Item> item
    ) {
    }

    /** depTime/arrTime: "HHMISS" 6자리(예: "051930" = 05:19:30). */
    record Item(String depTime, String arrTime, String dailyTypeCode, String upDownTypeCode,
                String subwayStationId, String subwayRouteId) {
    }
}
