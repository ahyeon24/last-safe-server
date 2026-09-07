package com.lastsafe.domain.bus;

/**
 * TAGO 노선별경유정류소목록조회(spec.md 5.2절) 응답 한 건에 대응한다. {@code order}는
 * TAGO의 {@code nodeord}(기점=1부터 시작하는 순번)와 같은 의미다.
 */
public record RouteStop(int order, double latitude, double longitude) {
}
