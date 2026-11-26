package com.lastsafe.external.odsay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.route.PathCandidate;
import com.lastsafe.domain.route.PathLeg;
import com.lastsafe.entity.TransportType;

/**
 * 픽스처({@code src/test/resources/odsay/search-pub-trans-path-t-sample.json})는 2026-09-09 실제
 * ODsay 호출로 확인한 응답과 **필드 구조만 같고 값은 모두 가상**이다(역·정류장 이름, 노선 번호, ID,
 * 좌표 등). 실제 응답을 저장소에 두면 ODsay 이용약관 제4.5.10조(사전 동의 없는 결과 데이터
 * 저장·배포 금지)에 걸릴 수 있어 2026-11-26 교체했다. 구조는 실제 응답 기준이라, 다른 TAGO
 * 테스트들처럼 "문서로 추정한 형태"가 아니라 "실제로 받은 형태"를 검증한다.
 */
class OdsayRouteSearchClientTest {

    private MockRestServiceServer server;
    private OdsayRouteSearchClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OdsayProperties properties = new OdsayProperties("test-key", null);
        client = new OdsayRouteSearchClient(builder, properties);
    }

    private static String readRealSampleFixture() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("odsay/search-pub-trans-path-t-sample.json").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void 실제_응답_샘플을_경로_후보_리스트로_변환한다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andExpect(queryParam("SX", "127.027583"))
                .andExpect(queryParam("SY", "37.49791"))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        List<PathCandidate> result = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528);

        assertThat(result).hasSize(2);
    }

    @Test
    void 버스_구간은_버스번호와_ArsID를_담는다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate first = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(0);

        // 첫 경로: 도보 → 버스(9901번) → 도보
        assertThat(first.legs()).hasSize(3);
        PathLeg busLeg = first.legs().get(1);
        assertThat(busLeg.type()).isEqualTo(TransportType.BUS);
        assertThat(busLeg.lineName()).isEqualTo("9901");
        assertThat(busLeg.busStopArsId()).isEqualTo("99001");
        assertThat(busLeg.startName()).isEqualTo("가상출발정류장");
        assertThat(busLeg.durationSec()).isEqualTo(33 * 60);
    }

    @Test
    void 도보_구간은_지명과_노선정보가_없다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate first = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(0);

        PathLeg walkLeg = first.legs().get(0);
        assertThat(walkLeg.type()).isEqualTo(TransportType.WALK);
        assertThat(walkLeg.startName()).isNull();
        assertThat(walkLeg.lineName()).isNull();
        assertThat(walkLeg.busStopArsId()).isNull();
        assertThat(walkLeg.durationSec()).isEqualTo(4 * 60);
    }

    @Test
    void 지하철_구간은_노선명과_방향을_담는다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate second = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(1);

        // 두 번째 경로: 도보 → 버스 → 도보 → 지하철(가상 9호선) → 도보
        PathLeg subwayLeg = second.legs().get(3);
        assertThat(subwayLeg.type()).isEqualTo(TransportType.SUBWAY);
        assertThat(subwayLeg.lineName()).isEqualTo("가상 9호선");
        assertThat(subwayLeg.direction()).isEqualTo("가상종점");
        assertThat(subwayLeg.startName()).isEqualTo("가상환승역");
        assertThat(subwayLeg.endName()).isEqualTo("가상도착역");
        assertThat(subwayLeg.busStopArsId()).isNull();
    }

    @Test
    void 결과가_없으면_예외를_던진다() {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("{\"error\":[{\"code\":\"500\",\"message\":\"test\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchPaths(127.0, 37.0, 126.9, 37.5))
                .isInstanceOf(IllegalStateException.class);
    }
}
