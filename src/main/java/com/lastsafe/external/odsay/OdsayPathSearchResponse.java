package com.lastsafe.external.odsay;

import java.util.List;

/**
 * ODsay {@code searchPubTransPathT} 응답 — **2026-09-09 실제 호출로 검증됨**(강남역→시청역,
 * 21개 경로 후보 수신 확인). TAGO/서울시 API들과 달리 문서/가이드 요약이 아니라 실제 응답
 * 원본을 보고 만든 DTO라 필드명은 확실함.
 *
 * 실패 시 ODsay는 {@code result} 대신 {@code error} 배열을 준다 — 이 DTO는 {@code error}
 * 필드를 선언하지 않았지만, Jackson이 알 수 없는 필드를 무시하도록 기본 설정돼있어(Spring
 * Boot 기본값) 파싱은 실패하지 않고 {@code result}가 null로 남는다. 그걸로 실패를 감지한다.
 */
record OdsayPathSearchResponse(Result result) {

    record Result(List<Path> path) {
    }

    /** pathType: 1=지하철only, 2=버스only, 3=지하철+버스 조합 (실제 응답으로 확인). */
    record Path(int pathType, Info info, List<SubPath> subPath) {
    }

    /** totalTime: 분 단위(초 아님, 실제 확인). */
    record Info(int totalTime, int payment) {
    }

    /**
     * trafficType: 1=지하철, 2=버스, 3=도보 (실제 확인). **WALK(trafficType=3)는 lane/startName/
     * endName/좌표/way/startArsID가 전부 없다** — distance/sectionTime만 옴(실제 확인).
     * sectionTime: 분 단위.
     */
    record SubPath(int trafficType, int sectionTime, List<Lane> lane,
                    String startName, Double startX, Double startY,
                    String endName, Double endX, Double endY,
                    String way, String startArsID) {
    }

    /** 지하철 lane엔 name(노선명)만, 버스 lane엔 busNo(버스번호)만 채워짐(실제 확인). */
    record Lane(String name, String busNo) {
    }
}
