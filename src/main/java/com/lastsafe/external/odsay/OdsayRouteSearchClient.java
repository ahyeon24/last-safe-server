package com.lastsafe.external.odsay;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.route.PathCandidate;
import com.lastsafe.domain.route.PathLeg;
import com.lastsafe.entity.TransportType;
import com.lastsafe.external.RouteSearchClient;

/**
 * ODsay {@code searchPubTransPathT} 연동 — spec.md 3절, **2026-09-09 실제 호출로 검증됨**
 * (강남역→시청역 좌표로 실제 호출, 21개 경로 후보 정상 수신 확인). TAGO/서울시 API들과 달리
 * "미검증" 딱지 없이 바로 확정으로 올릴 수 있는 첫 외부 연동 — 문서 요약이 아니라 실제 응답을
 * 보고 만들었기 때문.
 *
 * ODsay는 실패 시 HTTP 200에 {@code {"error": [...]}}를 준다(문서 확인, 실제 실패는 아직
 * 재현 못 해봄) — {@link OdsayPathSearchResponse}가 그 필드를 모르니 {@code result}가
 * null로 남는다, 그걸로 감지한다.
 */
@Service
public class OdsayRouteSearchClient implements RouteSearchClient {

    private final RestClient restClient;
    private final OdsayProperties properties;

    public OdsayRouteSearchClient(RestClient.Builder restClientBuilder, OdsayProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public List<PathCandidate> searchPaths(double startLon, double startLat, double endLon, double endLat) {
        OdsayPathSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/searchPubTransPathT")
                        .queryParam("apiKey", properties.apiKey())
                        .queryParam("SX", startLon)
                        .queryParam("SY", startLat)
                        .queryParam("EX", endLon)
                        .queryParam("EY", endLat)
                        .build())
                .retrieve()
                .body(OdsayPathSearchResponse.class);

        if (response == null || response.result() == null || response.result().path() == null) {
            throw new IllegalStateException(
                    "ODsay searchPubTransPathT 호출 실패 또는 결과 없음 (SX=" + startLon + ", SY=" + startLat
                            + ", EX=" + endLon + ", EY=" + endLat + ")");
        }
        return response.result().path().stream().map(this::toPathCandidate).toList();
    }

    private PathCandidate toPathCandidate(OdsayPathSearchResponse.Path path) {
        List<PathLeg> legs = path.subPath().stream().map(this::toPathLeg).toList();
        return new PathCandidate(path.info().totalTime() * 60, path.info().payment(), legs);
    }

    private PathLeg toPathLeg(OdsayPathSearchResponse.SubPath subPath) {
        TransportType type = toTransportType(subPath.trafficType());
        String lineName = null;
        if (subPath.lane() != null && !subPath.lane().isEmpty()) {
            OdsayPathSearchResponse.Lane lane = subPath.lane().get(0);
            lineName = type == TransportType.BUS ? lane.busNo() : lane.name();
        }
        return new PathLeg(
                type, subPath.sectionTime() * 60,
                subPath.startName(), subPath.endName(),
                subPath.startY(), subPath.startX(),
                subPath.endY(), subPath.endX(),
                lineName, subPath.way(), subPath.startArsID());
    }

    private static TransportType toTransportType(int trafficType) {
        return switch (trafficType) {
            case 1 -> TransportType.SUBWAY;
            case 2 -> TransportType.BUS;
            case 3 -> TransportType.WALK;
            default -> throw new IllegalStateException("ODsay trafficType 미확인 값: " + trafficType);
        };
    }
}
