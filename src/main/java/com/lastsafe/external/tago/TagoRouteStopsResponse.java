package com.lastsafe.external.tago;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * TAGO(국토교통부) 버스노선정보조회 서비스 {@code getRouteAcctoThrghSttnList} 응답
 * (오픈API활용가이드_국토교통부(TAGO)_버스노선정보v1.0, 2026-09-07 실제 문서로 확인 — 필드명은
 * 검증됨. 단, 실제 네트워크 호출로 이 DTO가 정확히 맞는지는 아직 확인 못 함, 키 받으면 확인).
 */
record TagoRouteStopsResponse(Response response) {

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

    /** nodeord: 기점=1부터 시작하는 정류소 순번. gpslati/gpslong: WGS84 좌표. */
    record Item(String nodeid, int nodeord, double gpslati, double gpslong) {
    }
}
