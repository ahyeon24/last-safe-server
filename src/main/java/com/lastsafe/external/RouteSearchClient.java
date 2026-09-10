package com.lastsafe.external;

import java.util.List;

import com.lastsafe.domain.route.PathCandidate;

/**
 * 대중교통 경로 탐색을 추상화한다 (ODsay {@code searchPubTransPathT} 연동, spec.md 3절,
 * NFR-07). 도메인/서비스 계층이 ODsay API 형식에 직접 의존하지 않도록 이 인터페이스 뒤로
 * 감춘다 — 경로 탐색은 대체 수단이 마땅치 않아 ODsay 하나만 구현체가 있을 예정이지만,
 * 인터페이스로 감싸두는 원칙은 다른 클라이언트들과 동일하게 유지한다.
 */
public interface RouteSearchClient {

    List<PathCandidate> searchPaths(double startLon, double startLat, double endLon, double endLat);
}
