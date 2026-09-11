package com.lastsafe.external.tago;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.route.SubwayStationCandidate;
import com.lastsafe.external.SubwayStationLookupClient;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * TAGO(국토교통부) 지하철정보 서비스 {@code GetKwrdFndSubwaySttnList} 연동 — **2026-09-11
 * 실제 호출로 검증됨**(spec.md 3절). ODsay 경로 탐색 결과의 역명을 TAGO {@code subwayStationId}로
 * 매핑하는 데 쓰인다({@link com.lastsafe.domain.route.SubwayStationIdResolver} 참고).
 *
 * TAGO 원본은 키워드 부분일치라 "종로3가" 검색 시 "종로3가(탑골공원)"(5호선, 다른 표기)까지
 * 같이 오고, "시청" 검색 시 전국의 "OO시청"류 역까지 섞여 온다(실제 확인) — 이 클라이언트가
 * 정확히 일치하는 역명만 걸러서 돌려준다.
 */
@Service
public class TagoSubwayStationLookupClient implements SubwayStationLookupClient {

    private static final String PATH = "/GetKwrdFndSubwaySttnList";
    private static final int PAGE_SIZE = 100; // 실제 확인된 "시청" 검색 totalCount=15보다 넉넉하게 잡음

    private final RestClient restClient;
    private final DataGoKrProperties properties;

    public TagoSubwayStationLookupClient(RestClient.Builder restClientBuilder, DataGoKrProperties properties) {
        this.restClient = restClientBuilder.baseUrl("http://apis.data.go.kr/1613000/SubwayInfo").build();
        this.properties = properties;
    }

    @Override
    public List<SubwayStationCandidate> findByExactStationName(String stationName) {
        return fetchAllPages(stationName).stream()
                .filter(item -> item.subwayStationName().equals(stationName))
                .map(item -> new SubwayStationCandidate(
                        item.subwayStationId(), item.subwayStationName(), item.subwayRouteName()))
                .toList();
    }

    private List<TagoSubwayStationLookupResponse.Item> fetchAllPages(String stationName) {
        List<TagoSubwayStationLookupResponse.Item> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            TagoSubwayStationLookupResponse.Body body = requestPage(stationName, pageNo);
            List<TagoSubwayStationLookupResponse.Item> items =
                    (body.items() == null || body.items().item() == null) ? List.of() : body.items().item();
            all.addAll(items);
            if (items.isEmpty() || all.size() >= body.totalCount()) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private TagoSubwayStationLookupResponse.Body requestPage(String stationName, int pageNo) {
        TagoSubwayStationLookupResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(PATH)
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("_type", "json")
                        .queryParam("subwayStationName", stationName)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .body(TagoSubwayStationLookupResponse.class);

        if (response == null || response.response() == null || response.response().header() == null
                || !"00".equals(response.response().header().resultCode())) {
            throw new IllegalStateException("TAGO GetKwrdFndSubwaySttnList 호출 실패 (stationName=" + stationName + ")");
        }
        return response.response().body();
    }
}
