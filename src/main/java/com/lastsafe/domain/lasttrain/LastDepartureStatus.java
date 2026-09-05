package com.lastsafe.domain.lasttrain;

/**
 * 교통 구간의 막차 시각 조회 결과.
 * FOUND: 정상 조회됨 (해당 구간 자체 막차 시각 확보)
 * NO_SERVICE: 막차 데이터상 운행 없음 (정상 도메인 결과, 시스템 오류 아님)
 * FETCH_ERROR: 외부 API/캐시 조회 실패 (시스템 오류)
 * NO_SERVICE와 FETCH_ERROR를 혼동하면 안 됨 — spec.md 5절 참고.
 */
public enum LastDepartureStatus {
    FOUND, NO_SERVICE, FETCH_ERROR
}
