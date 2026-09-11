package com.lastsafe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 픽스처는 2026-09-11 TAGO {@code GetKwrdFndSubwaySttnList} 실제 호출 결과 그대로다
 * ({@link SubwayStationIdResolver} 상단 주석 참고).
 */
class SubwayStationIdResolverTest {

    @Test
    void 후보가_하나뿐이면_노선명_확인_없이_바로_반환한다() {
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRS11133", "서울역", "1호선"));

        Optional<String> result = SubwayStationIdResolver.resolve(candidates, "아무거나여도됨");

        assertThat(result).contains("MTRS11133");
    }

    @Test
    void 노선명이_부분_문자열로만_일치해도_찾는다() {
        // 종로3가: TAGO는 "1호선"/"3호선", ODsay는 "수도권 1호선"
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRS11153", "종로3가", "1호선"),
                new SubwayStationCandidate("MTRS13319", "종로3가", "3호선"));

        Optional<String> result = SubwayStationIdResolver.resolve(candidates, "수도권 1호선");

        assertThat(result).contains("MTRS11153");
    }

    @Test
    void 브랜드_노선명도_부분_문자열_포함으로_찾는다() {
        // 강남: 신분당선(TAGO "신분당") vs 2호선
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRDXD1D007", "강남", "신분당"),
                new SubwayStationCandidate("MTRS12222", "강남", "2호선"));

        Optional<String> result = SubwayStationIdResolver.resolve(candidates, "신분당선");

        assertThat(result).contains("MTRDXD1D007");
    }

    @Test
    void 도시가_달라도_역명_노선명이_모두_같으면_모호해서_빈값을_반환한다() {
        // "시청" 검색 시 실제로 대구 1호선과 서울 1호선이 둘 다 나옴 — 좌표 정보가 없어 구분 불가
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRDJ10011", "시청", "1호선"), // 대구
                new SubwayStationCandidate("MTRS11151", "시청", "1호선"),  // 서울
                new SubwayStationCandidate("MTRS12201", "시청", "2호선")); // 서울

        Optional<String> result = SubwayStationIdResolver.resolve(candidates, "수도권 1호선");

        assertThat(result).isEmpty();
    }

    @Test
    void 노선명이_아예_안_맞으면_빈값을_반환한다() {
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRDXD1D007", "강남", "신분당"),
                new SubwayStationCandidate("MTRS12222", "강남", "2호선"));

        Optional<String> result = SubwayStationIdResolver.resolve(candidates, "7호선");

        assertThat(result).isEmpty();
    }

    @Test
    void 후보가_없으면_빈값을_반환한다() {
        assertThat(SubwayStationIdResolver.resolve(List.of(), "1호선")).isEmpty();
    }

    @Test
    void 노선명_힌트가_없고_후보가_여럿이면_추측하지_않는다() {
        List<SubwayStationCandidate> candidates = List.of(
                new SubwayStationCandidate("MTRDXD1D007", "강남", "신분당"),
                new SubwayStationCandidate("MTRS12222", "강남", "2호선"));

        assertThat(SubwayStationIdResolver.resolve(candidates, null)).isEmpty();
    }
}
