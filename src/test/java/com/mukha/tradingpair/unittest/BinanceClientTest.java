package com.mukha.tradingpair.unittest;

import com.mukha.tradingpair.client.BinanceClient;
import com.mukha.tradingpair.config.BinanceProperties;
import com.mukha.tradingpair.dto.response.BinanceAvgPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BinanceClientTest {

    private static final String BASE_URL = "http://binance.test";
    private static final String AVG_PRICE_URL = BASE_URL + "/api/v3/avgPrice?symbol=BTCUSDT";
    private static final String SYMBOL = "BTCUSDT";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String OK_BODY = "{\"mins\":5,\"price\":\"76000.25\",\"closeTime\":1760000000000}";

    private MockRestServiceServer server;
    private RestClient restClient;
    private BinanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = clientWithBackoff(Duration.ofMinutes(1));
    }

    private BinanceClient clientWithBackoff(Duration backoff) {
        BinanceProperties properties =
                new BinanceProperties(BASE_URL, SYMBOL, Duration.ofSeconds(30), backoff);
        return new BinanceClient(restClient, properties, FIXED_CLOCK);
    }

    @Test
    void getCurrentAvgPrice_shouldParsePrice_andIgnoreUnknownFields() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        Optional<BinanceAvgPriceResponse> result = client.getCurrentAvgPrice(SYMBOL);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo("76000.25");
        server.verify();
    }

    @Test
    void getCurrentAvgPrice_shouldReturnEmpty_onServerError() {
        server.expect(requestTo(AVG_PRICE_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
    }

    @Test
    void getCurrentAvgPrice_shouldReturnEmpty_onBadRequest_andNotPausePolling() {
        server.expect(requestTo(AVG_PRICE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-1121,\"msg\":\"Invalid symbol.\"}"));
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
        assertThat(client.getCurrentAvgPrice(SYMBOL)).isPresent();
        server.verify();
    }

    @Test
    void getCurrentAvgPrice_shouldReturnEmpty_onNetworkError() {
        server.expect(requestTo(AVG_PRICE_URL)).andRespond(request -> {
            throw new IOException("connection reset");
        });

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
    }

    @Test
    void getCurrentAvgPrice_shouldReturnEmpty_onMalformedJson() {
        server.expect(requestTo(AVG_PRICE_URL))
                .andRespond(withSuccess("{not a json", MediaType.APPLICATION_JSON));

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"price\":null}",
            "{\"price\":\"0\"}",
            "{\"price\":\"-1.5\"}"
    })
    void getCurrentAvgPrice_shouldRejectUnusablePrice(String body) {
        server.expect(requestTo(AVG_PRICE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 418})
    void getCurrentAvgPrice_shouldNotCallBinance_whilePauseIsActive(int status) {
        server.expect(requestTo(AVG_PRICE_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();
        assertThat(client.getCurrentAvgPrice(SYMBOL)).isEmpty();

        server.verify();
    }
}