package com.lastsafe.external.odsay;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.DayType;

/**
 * ⚠️ 이 테스트가 검증하는 건 "우리가 추정한 응답 형태를 우리가 만든 DTO가 올바르게 파싱하는가"뿐이다.
 * ODsay의 실제 응답이 이 형태와 같은지는 미검증 — {@link OdsaySubwayScheduleClient} 상단 주석 참고.
 */
class OdsaySubwayScheduleClientTest {

    private static final String WEEKDAY_RESPONSE_JSON = """
            {
              "result": {
                "weekdaySchedule": [
                  {"time": "05:30", "firstLastFlag": 1},
                  {"time": "23:50", "firstLastFlag": 0},
                  {"time": "00:10", "firstLastFlag": 2}
                ]
              }
            }
            """;

    private MockRestServiceServer server;
    private OdsaySubwayScheduleClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OdsayProperties properties = new OdsayProperties("test-key", "https://api.odsay.com/v1/api");
        client = new OdsaySubwayScheduleClient(builder, properties);
    }

    @Test
    void 자정을_넘긴_시각표에서_막차를_찾는다() {
        setUp();
        server.expect(requestTo(containsString("/searchSubwaySchedule")))
                .andExpect(queryParam("stationID", "150"))
                .andExpect(queryParam("wayCode", "1"))
                .andRespond(withSuccess(WEEKDAY_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "150", "1", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        // 00:10이 자정을 넘긴 값이라 09-08로 계산돼야 시각표의 실제 막차(가장 늦은 값)가 된다.
        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 8, 0, 10)));
    }

    @Test
    void 데드라인_이하_배차_중_가장_늦은_시각을_찾는다() {
        setUp();
        server.expect(requestTo(containsString("/searchSubwaySchedule")))
                .andExpect(queryParam("stationID", "150"))
                .andExpect(queryParam("wayCode", "1"))
                .andRespond(withSuccess(WEEKDAY_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        Optional<LocalDateTime> result = client.findLatestDepartureAtOrBefore(
                "150", "1", LocalDateTime.of(2026, 9, 7, 23, 55), DayType.WEEKDAY);

        assertThat(result).contains(LocalDateTime.of(2026, 9, 7, 23, 50));
    }

    @Test
    void 데드라인_이하_배차가_없으면_빈값을_반환한다() {
        setUp();
        server.expect(requestTo(containsString("/searchSubwaySchedule")))
                .andExpect(queryParam("stationID", "150"))
                .andExpect(queryParam("wayCode", "1"))
                .andRespond(withSuccess(WEEKDAY_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        Optional<LocalDateTime> result = client.findLatestDepartureAtOrBefore(
                "150", "1", LocalDateTime.of(2026, 9, 7, 5, 0), DayType.WEEKDAY);

        assertThat(result).isEmpty();
    }
}
