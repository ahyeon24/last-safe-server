package com.lastsafe.external.tago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.bus.BusStopLastDepartureEstimator;
import com.lastsafe.domain.bus.HaversineDistance;
import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.DayType;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * ⚠️ 이 테스트는 "TAGO 활용가이드 문서에 적힌 형태를 우리 DTO가 올바르게 파싱하고,
 * BusStopLastDepartureEstimator에 올바르게 넘기는가"를 검증한다. 문서와 실제 응답이 다를
 * 가능성은 남아있음 — {@link TagoBusLastDepartureClient} 상단 주석 참고.
 */
class TagoBusLastDepartureClientTest {

    private static final double SPEED_MPS = 10.0; // BusEstimateProperties 기본값과 동일(잠정치)

    private static final String ROUTE_INFO_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                "body": {
                  "items": {"item": {
                    "routeid": "DJB30300004", "endvehicletime": "2230", "startvehicletime": "0600",
                    "intervaltime": "29", "intervalsattime": "23", "intervalsuntime": "29"
                  }}
                }
              }
            }
            """;

    private static final String OVERNIGHT_ROUTE_INFO_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                "body": {
                  "items": {"item": {
                    "routeid": "DJB30300004", "endvehicletime": "0010", "startvehicletime": "0530",
                    "intervaltime": "29", "intervalsattime": "23", "intervalsuntime": "29"
                  }}
                }
              }
            }
            """;

    private static final String STOPS_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                "body": {
                  "items": {"item": [
                    {"nodeid": "DJB001", "nodeord": 1, "gpslati": 37.00, "gpslong": 127.00},
                    {"nodeid": "DJB002", "nodeord": 2, "gpslati": 37.01, "gpslong": 127.00},
                    {"nodeid": "DJB003", "nodeord": 3, "gpslati": 37.02, "gpslong": 127.00}
                  ]},
                  "numOfRows": 200,
                  "pageNo": 1,
                  "totalCount": 3
                }
              }
            }
            """;

    private MockRestServiceServer server;
    private TagoBusLastDepartureClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DataGoKrProperties properties = new DataGoKrProperties("test-key");
        client = new TagoBusLastDepartureClient(builder, properties, new BusStopLastDepartureEstimator(SPEED_MPS));
    }

    @Test
    void 목표_정류소까지_하버사인_거리_기반으로_막차_시각을_추정한다() {
        setUp();
        server.expect(requestTo(containsString("/getRouteInfoIem")))
                .andExpect(queryParam("cityCode", "25"))
                .andExpect(queryParam("routeId", "DJB30300004"))
                .andRespond(withSuccess(ROUTE_INFO_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getRouteAcctoThrghSttnList")))
                .andExpect(queryParam("cityCode", "25"))
                .andExpect(queryParam("routeId", "DJB30300004"))
                .andRespond(withSuccess(STOPS_JSON, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "DJB003", "DJB30300004", "25", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        double hopMeters = HaversineDistance.meters(37.00, 127.00, 37.01, 127.00);
        long expectedSeconds = Math.round(2 * hopMeters / SPEED_MPS);
        LocalDateTime expected = LocalDateTime.of(2026, 9, 7, 22, 30).plusSeconds(expectedSeconds);

        assertThat(result).isEqualTo(LastDeparture.found(expected));
    }

    @Test
    void 기점_정류소는_노선_막차_시각_그대로다() {
        setUp();
        server.expect(requestTo(containsString("/getRouteInfoIem")))
                .andRespond(withSuccess(ROUTE_INFO_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getRouteAcctoThrghSttnList")))
                .andRespond(withSuccess(STOPS_JSON, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "DJB001", "DJB30300004", "25", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 7, 22, 30)));
    }

    @Test
    void 막차가_첫차보다_이르면_자정을_넘긴_것으로_계산한다() {
        setUp();
        server.expect(requestTo(containsString("/getRouteInfoIem")))
                .andRespond(withSuccess(OVERNIGHT_ROUTE_INFO_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getRouteAcctoThrghSttnList")))
                .andRespond(withSuccess(STOPS_JSON, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "DJB001", "DJB30300004", "25", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 8, 0, 10)));
    }

    @Test
    void 노선에_없는_정류소면_예외를_던진다() {
        setUp();
        server.expect(requestTo(containsString("/getRouteInfoIem")))
                .andRespond(withSuccess(ROUTE_INFO_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getRouteAcctoThrghSttnList")))
                .andRespond(withSuccess(STOPS_JSON, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findLastDeparture(
                "NOT_ON_ROUTE", "DJB30300004", "25", LocalDate.of(2026, 9, 7), DayType.WEEKDAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 추정_막차시각이_데드라인_이후면_빈값을_반환한다() {
        setUp();
        server.expect(requestTo(containsString("/getRouteInfoIem")))
                .andRespond(withSuccess(ROUTE_INFO_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getRouteAcctoThrghSttnList")))
                .andRespond(withSuccess(STOPS_JSON, MediaType.APPLICATION_JSON));

        Optional<LocalDateTime> result = client.findLatestDepartureAtOrBefore(
                "DJB001", "DJB30300004", "25", LocalDateTime.of(2026, 9, 7, 22, 0), DayType.WEEKDAY);

        assertThat(result).isEmpty();
    }
}
