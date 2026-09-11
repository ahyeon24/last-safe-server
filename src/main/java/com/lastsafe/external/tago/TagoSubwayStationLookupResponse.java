package com.lastsafe.external.tago;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * TAGO(국토교통부) 지하철정보 서비스 {@code GetKwrdFndSubwaySttnList} 응답
 * (오픈API활용가이드_국토교통부(TAGO)_지하철정보v1.1, **2026-09-11 실제 호출로 검증됨** —
 * "종로3가"/"강남"/"시청" 키워드로 실제 조회, 필드명·응답 형태 확정. 키워드가 "역명에
 * 부분 일치"하는 전국 모든 역을 준다는 것도 실호출로 확인함(spec.md 3절 참고).
 */
record TagoSubwayStationLookupResponse(Response response) {

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

    record Item(String subwayStationId, String subwayStationName, String subwayRouteName) {
    }
}
