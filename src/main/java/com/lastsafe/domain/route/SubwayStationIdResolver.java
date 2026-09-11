package com.lastsafe.domain.route;

import java.util.List;
import java.util.Optional;

/**
 * ODsay 경로 탐색 결과의 역명·노선명(ODsay 자체 ID 체계)을 TAGO {@code subwayStationId}로
 * 매핑한다 (spec.md 3절·10절, 2026-09-11 실제 데이터로 설계 확정).
 *
 * **실제 데이터로 확인된 함정들** (2026-09-11, TAGO {@code GetKwrdFndSubwaySttnList} 실호출):
 * 1. 같은 역명이라도 노선별로 ID가 다르다 — "강남" 검색 시 신분당선/2호선 두 건이 옴. 노선명도
 *    같이 대조해야 한다.
 * 2. **노선명 표기가 벤더마다 다르다** — ODsay는 "수도권 1호선", TAGO는 "1호선"만 준다.
 *    브랜드 노선(신분당선→"신분당", 에버라인→"에버라인", 수인분당선→"수인분당")은 "호선"
 *    접미사조차 없다 — 정확히 일치가 아니라 **서로 부분 문자열 포함 관계**로 판정해야 한다.
 * 3. **같은 역명이 다른 도시에도 있다** — "시청" 검색 시 대구 1호선과 서울 1호선·2호선이
 *    모두 나온다. TAGO 응답엔 도시/좌표 정보가 없어서, 역명+노선명까지 똑같이 겹치는
 *    경우(예: 대구 "시청" 1호선 vs 서울 "시청" 1호선)는 **이 정보만으론 구분 불가능** —
 *    이 경우 억지로 하나를 고르지 않고 빈 값을 반환한다(잘못된 도시 역으로 스냅하는 것보다
 *    안전).
 */
public final class SubwayStationIdResolver {

    private SubwayStationIdResolver() {
    }

    /**
     * @param candidates 역명으로 조회된 TAGO 후보 목록(호출자가 이미 역명 기준으로 걸러온 것)
     * @param odsayLineName ODsay가 준 노선명(예: "수도권 1호선") — 후보가 여럿일 때만 쓰임
     */
    public static Optional<String> resolve(List<SubwayStationCandidate> candidates, String odsayLineName) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0).subwayStationId());
        }
        if (odsayLineName == null) {
            return Optional.empty();
        }

        List<SubwayStationCandidate> lineMatches = candidates.stream()
                .filter(candidate -> lineNamesMatch(odsayLineName, candidate.lineName()))
                .toList();

        if (lineMatches.size() == 1) {
            return Optional.of(lineMatches.get(0).subwayStationId());
        }
        return Optional.empty(); // 여전히 모호함(예: 도시 간 동명 역+동일 노선명) — 추측하지 않음
    }

    private static boolean lineNamesMatch(String odsayLineName, String tagoLineName) {
        return odsayLineName.contains(tagoLineName) || tagoLineName.contains(odsayLineName);
    }
}
