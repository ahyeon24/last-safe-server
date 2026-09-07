package com.lastsafe.external.tago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.bus.VehiclePositionObservation;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * ⚠️ 이 테스트는 "TAGO 활용가이드 문서에 적힌 형태를 우리 DTO가 올바르게 파싱하는가"를
 * 검증한다. 문서와 실제 응답이 다를 가능성은 남아있음 — {@link TagoBusPositionClient} 상단
 * 주석 참고.
 */
class TagoBusPositionClientTest {

    private MockRestServiceServer server;
    private TagoBusPositionClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DataGoKrProperties properties = new DataGoKrProperties("test-key");
        client = new TagoBusPositionClient(builder, properties);
    }

    @Test
    void 여러_차량의_위치를_조회한다() {
        setUp();
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": [
                        {"routenm": "202", "gpslati": 36.333445, "gpslong": 127.438859, "nodeord": 3, "nodenm": "정류소A", "nodeid": "DJB8005621", "routetp": "간선버스", "vehicleno": "대전99가9999"},
                        {"routenm": "202", "gpslati": 36.34, "gpslong": 127.44, "nodeord": 5, "nodenm": "정류소B", "nodeid": "DJB8005622", "routetp": "간선버스", "vehicleno": "대전99가1111"}
                      ]},
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 2
                    }
                  }
                }
                """;
        server.expect(requestTo(containsString("/getRouteAcctoBusLcList")))
                .andExpect(queryParam("cityCode", "25"))
                .andExpect(queryParam("routeId", "DJB30300052"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<VehiclePositionObservation> result = client.findVehiclePositions("25", "DJB30300052");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vehicleNo()).isEqualTo("대전99가9999");
        assertThat(result.get(0).nodeOrd()).isEqualTo(3);
        assertThat(result.get(1).vehicleNo()).isEqualTo("대전99가1111");
        assertThat(result.get(1).nodeOrd()).isEqualTo(5);
        // 같은 폴링(한 번의 호출)에서 나온 관측들은 같은 시각을 공유해야 함
        assertThat(result.get(0).observedAt()).isEqualTo(result.get(1).observedAt());
    }

    @Test
    void 결과가_없으면_빈_리스트를_반환한다() {
        setUp();
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": null, "numOfRows": 100, "pageNo": 1, "totalCount": 0}
                  }
                }
                """;
        server.expect(requestTo(containsString("/getRouteAcctoBusLcList")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<VehiclePositionObservation> result = client.findVehiclePositions("25", "DJB30300052");

        assertThat(result).isEmpty();
    }

    @Test
    void 결과가_한건이면_배열이_아니라_단일_객체로_와도_처리한다() {
        setUp();
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": {"routenm": "202", "gpslati": 36.333445, "gpslong": 127.438859, "nodeord": 1, "nodenm": "정류소A", "nodeid": "DJB8005621", "routetp": "간선버스", "vehicleno": "대전99가9999"}},
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        server.expect(requestTo(containsString("/getRouteAcctoBusLcList")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<VehiclePositionObservation> result = client.findVehiclePositions("25", "DJB30300052");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vehicleNo()).isEqualTo("대전99가9999");
    }
}
