package com.lastsafe.external.tago;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * TAGO(국토교통부) 버스위치정보조회 서비스 {@code getRouteAcctoBusLcList} 응답
 * (오픈API활용가이드_국토교통부(TAGO)_버스위치정보v1.0, 2026-09-07 실제 문서로 확인 — 필드명은
 * 검증됨. 단, 실제 네트워크 호출로 이 DTO가 정확히 맞는지는 아직 확인 못 함, 키 받으면 확인).
 *
 * items.item: 결과가 1건일 때 배열이 아니라 단일 객체로 오는 공공데이터포털 공통 특성 방어
 * (TagoSubwayScheduleResponse와 같은 이유).
 */
record TagoBusPositionResponse(Response response) {

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

    /**
     * gpslati/gpslong(좌표)은 5.3절 BUS_VEHICLE_POSITION 스키마에 없어서 이 DTO에도 안 담는다
     * — nodeOrd 기반 보간(BusNodePassageEstimator)에 필요한 필드만 유지.
     */
    record Item(String vehicleno, String nodeid, int nodeord) {
    }
}
