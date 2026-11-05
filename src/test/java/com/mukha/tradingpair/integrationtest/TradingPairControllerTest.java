package com.mukha.tradingpair.integrationtest;

import com.mukha.tradingpair.client.BinanceClient;
import com.mukha.tradingpair.model.TradingPair;
import com.mukha.tradingpair.repository.TradingPairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradingPairControllerTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/prices";
    private static final String DEFAULT_SYMBOL = "BTCUSDT";
    private static final String OTHER_SYMBOL = "ETHUSDT";

    private static final Instant T1 = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-09-04T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-09-05T00:00:00Z");

    private static final double EPS = 1e-9;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradingPairRepository repository;

    @MockitoBean
    private BinanceClient binanceClient;

    private static MockHttpServletRequestBuilder range(MockHttpServletRequestBuilder request, Instant from, Instant to) {
        return request.param("from", from.toString()).param("to", to.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void latest_shouldReturnMostRecentRecord_withDefaultSymbol() throws Exception {
        save(DEFAULT_SYMBOL, "100.5", T1);
        save(DEFAULT_SYMBOL, "102.7500000001", T3);
        save(DEFAULT_SYMBOL, "101.1", T2);
        save(OTHER_SYMBOL, "999", T5);

        mockMvc.perform(get(BASE + "/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(DEFAULT_SYMBOL))
                .andExpect(jsonPath("$.price").value("102.7500000001"))
                .andExpect(jsonPath("$.fetchedAt").value("2026-09-03T00:00:00Z"));
    }

    @Test
    void latest_shouldReturnRecordOfRequestedSymbol() throws Exception {
        save(DEFAULT_SYMBOL, "100", T1);
        save(OTHER_SYMBOL, "2500.5", T2);

        mockMvc.perform(get(BASE + "/latest").param("symbol", OTHER_SYMBOL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(OTHER_SYMBOL))
                .andExpect(jsonPath("$.price").value("2500.5000000000"));
    }

    @Test
    void latest_shouldReturn404_whenNothingSavedYet() throws Exception {
        mockMvc.perform(get(BASE + "/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString(DEFAULT_SYMBOL)));
    }

    @Test
    void average_shouldCalculateAverageInRange() throws Exception {
        save(DEFAULT_SYMBOL, "1000", T1.minusSeconds(1));
        save(DEFAULT_SYMBOL, "10", T1);
        save(DEFAULT_SYMBOL, "20", T2);
        save(DEFAULT_SYMBOL, "30", T3);
        save(DEFAULT_SYMBOL, "1000", T3.plusSeconds(1));
        save(OTHER_SYMBOL, "5000", T2);

        mockMvc.perform(range(get(BASE + "/average"), T1, T3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice", closeTo(20.0, EPS)));
    }

    @Test
    void average_shouldNotLosePrecision() throws Exception {
        save(DEFAULT_SYMBOL, "0.1", T1);
        save(DEFAULT_SYMBOL, "0.2", T2);

        mockMvc.perform(range(get(BASE + "/average"), T1, T2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0.1500000000")));
    }

    @Test
    void average_shouldReturn404_whenNoDataInRange() throws Exception {
        save(DEFAULT_SYMBOL, "10", T1);

        mockMvc.perform(range(get(BASE + "/average"), T4, T5))
                .andExpect(status().isNotFound());
    }

    @Test
    void average_shouldReturn400_whenFromIsNotBeforeTo() throws Exception {
        mockMvc.perform(range(get(BASE + "/average"), T3, T3)).andExpect(status().isBadRequest());
        mockMvc.perform(range(get(BASE + "/average"), T3, T1)).andExpect(status().isBadRequest());
    }

    @Test
    void average_shouldReturn400_whenParameterIsMissing() throws Exception {
        mockMvc.perform(get(BASE + "/average").param("from", T1.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/average").param("to", T1.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void average_shouldReturn400_whenDateIsMalformed() throws Exception {
        mockMvc.perform(get(BASE + "/average").param("from", "yesterday").param("to", T2.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_shouldReturnFirstPage_sortedByFetchedAtDescending() throws Exception {
        saveFiveRecords();

        mockMvc.perform(range(get(BASE), T1, T5).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].symbol").value(DEFAULT_SYMBOL))
                .andExpect(jsonPath("$.items[0].price").value("5.0000000000"))
                .andExpect(jsonPath("$.items[0].fetchedAt").value("2026-09-05T00:00:00Z"))
                .andExpect(jsonPath("$.items[1].price").value("4.0000000000"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void list_shouldUseDefaultPaging_whenNotSpecified() throws Exception {
        saveFiveRecords();

        mockMvc.perform(range(get(BASE), T1, T5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void list_shouldFilterByRangeAndSymbol() throws Exception {
        saveFiveRecords();
        save(OTHER_SYMBOL, "777", T3);

        mockMvc.perform(range(get(BASE), T2, T4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(range(get(BASE), T1, T5).param("symbol", OTHER_SYMBOL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].symbol").value(OTHER_SYMBOL));
    }

    @Test
    void list_shouldReturnEmptyPage_whenNoRecordsInRange() throws Exception {
        saveFiveRecords();

        mockMvc.perform(range(get(BASE), T5.plusSeconds(10), T5.plusSeconds(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void list_shouldReturn400_whenPageIsNegative() throws Exception {
        mockMvc.perform(range(get(BASE), T1, T5).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "101"})
    void list_shouldReturn400_whenSizeIsOutOfBounds(String size) throws Exception {
        mockMvc.perform(range(get(BASE), T1, T5).param("size", size))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trend_shouldBeUpward_whenPricesGrow() throws Exception {
        save(DEFAULT_SYMBOL, "1", T1);
        save(DEFAULT_SYMBOL, "2", T2);
        save(DEFAULT_SYMBOL, "3", T3);
        save(DEFAULT_SYMBOL, "4", T4);

        mockMvc.perform(range(get(BASE + "/trend"), T1, T5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(DEFAULT_SYMBOL))
                .andExpect(jsonPath("$.start_date").value("2026-09-01T00:00:00Z"))
                .andExpect(jsonPath("$.end_date").value("2026-09-05T00:00:00Z"))
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }

    @Test
    void trend_shouldBeDownward_whenPricesFall() throws Exception {
        save(DEFAULT_SYMBOL, "4", T1);
        save(DEFAULT_SYMBOL, "3", T2);
        save(DEFAULT_SYMBOL, "2", T3);
        save(DEFAULT_SYMBOL, "1", T4);

        mockMvc.perform(range(get(BASE + "/trend"), T1, T5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend").value("DOWNWARD"));
    }

    @Test
    void trend_shouldBeFlat_whenPricesAreEqual() throws Exception {
        save(DEFAULT_SYMBOL, "5", T1);
        save(DEFAULT_SYMBOL, "5", T2);
        save(DEFAULT_SYMBOL, "5", T3);
        save(DEFAULT_SYMBOL, "5", T4);

        mockMvc.perform(range(get(BASE + "/trend"), T1, T5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend").value("FLAT"));
    }

    @Test
    void trend_shouldIgnoreRecordsOfOtherSymbolsAndOutsideRange() throws Exception {
        save(DEFAULT_SYMBOL, "1", T2);
        save(DEFAULT_SYMBOL, "2", T3);
        save(DEFAULT_SYMBOL, "1000", T1.minusSeconds(1));
        save(OTHER_SYMBOL, "1000", T2);

        mockMvc.perform(range(get(BASE + "/trend"), T1, T5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }

    @Test
    void trend_shouldReturn404_whenLessThanTwoRecords() throws Exception {
        save(DEFAULT_SYMBOL, "1", T2);

        mockMvc.perform(range(get(BASE + "/trend"), T1, T5))
                .andExpect(status().isNotFound());
    }

    private void saveFiveRecords() {
        save(DEFAULT_SYMBOL, "1", T1);
        save(DEFAULT_SYMBOL, "2", T2);
        save(DEFAULT_SYMBOL, "3", T3);
        save(DEFAULT_SYMBOL, "4", T4);
        save(DEFAULT_SYMBOL, "5", T5);
    }

    private void save(String symbol, String price, Instant fetchedAt) {
        TradingPair pair = new TradingPair();
        pair.setSymbol(symbol);
        pair.setPrice(new BigDecimal(price));
        pair.setFetchedAt(fetchedAt);
        repository.save(pair);
    }
}
