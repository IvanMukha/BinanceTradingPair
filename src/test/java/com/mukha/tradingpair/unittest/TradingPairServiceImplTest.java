package com.mukha.tradingpair.unittest;

import com.mukha.tradingpair.dto.response.AveragePriceResponse;
import com.mukha.tradingpair.dto.response.TradingPairResponse;
import com.mukha.tradingpair.dto.response.TrendResponse;
import com.mukha.tradingpair.exception.InvalidPeriodException;
import com.mukha.tradingpair.exception.NoPriceDataException;
import com.mukha.tradingpair.exception.TradingPairNotFoundException;
import com.mukha.tradingpair.mapper.TradingPairMapper;
import com.mukha.tradingpair.model.TradingPair;
import com.mukha.tradingpair.model.trend.TradingPairTrendType;
import com.mukha.tradingpair.repository.TradingPairRepository;
import com.mukha.tradingpair.service.impl.TradingPairServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingPairServiceImplTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    private TradingPairRepository repository;

    @Mock
    private TradingPairMapper mapper;

    @InjectMocks
    private TradingPairServiceImpl service;

    private static TradingPair entity(String price, Instant fetchedAt) {
        TradingPair pair = new TradingPair();
        pair.setSymbol(SYMBOL);
        pair.setPrice(new BigDecimal(price));
        pair.setFetchedAt(fetchedAt);
        return pair;
    }

    @Test
    void save_shouldSaveEntityWithGivenValues() {
        BigDecimal price = new BigDecimal("76000.2500000001");
        Instant fetchedAt = Instant.parse("2026-09-10T12:00:00Z");

        service.save(SYMBOL, price, fetchedAt);

        ArgumentCaptor<TradingPair> captor = ArgumentCaptor.forClass(TradingPair.class);
        verify(repository).save(captor.capture());
        TradingPair saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo(SYMBOL);
        assertThat(saved.getPrice()).isEqualByComparingTo(price);
        assertThat(saved.getFetchedAt()).isEqualTo(fetchedAt);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void getLastFetched_shouldReturnResponse_whenExists() {
        TradingPair entity = entity("76000.25", FROM);
        TradingPairResponse response = new TradingPairResponse(SYMBOL, entity.getPrice(), FROM);
        when(repository.findFirstBySymbolOrderByFetchedAtDesc(SYMBOL)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        TradingPairResponse result = service.getLastFetched(SYMBOL);

        assertThat(result).isSameAs(response);
    }

    @Test
    void getLastFetched_shouldThrowNotFoundException_whenDataNotFound() {
        when(repository.findFirstBySymbolOrderByFetchedAtDesc(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLastFetched(SYMBOL))
                .isInstanceOf(TradingPairNotFoundException.class)
                .hasMessageContaining(SYMBOL);
        verifyNoInteractions(mapper);
    }

    @Test
    void getAveragePriceForPeriod_shouldReturnAverageWithFixedScale() {
        when(repository.getAveragePrice(SYMBOL, FROM, TO)).thenReturn(new BigDecimal("10.25"));

        AveragePriceResponse result = service.getAveragePriceForPeriod(SYMBOL, FROM, TO);

        assertThat(result.averagePrice()).isEqualByComparingTo("10.25");
        assertThat(result.averagePrice().scale()).isEqualTo(10);
    }

    @Test
    void getAveragePriceForPeriod_shouldThrowNoData_whenReturnNull() {
        when(repository.getAveragePrice(SYMBOL, FROM, TO)).thenReturn(null);

        assertThatThrownBy(() -> service.getAveragePriceForPeriod(SYMBOL, FROM, TO))
                .isInstanceOf(NoPriceDataException.class)
                .hasMessageContaining(SYMBOL);
    }

    @Test
    void getAveragePriceForPeriod_shouldRejectFromAfterTo() {
        assertThatThrownBy(() -> service.getAveragePriceForPeriod(SYMBOL, TO, FROM))
                .isInstanceOf(InvalidPeriodException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void getAllWithinRange_shouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, TradingPair.Fields.fetchedAt));
        TradingPair entity = entity("100.5", FROM);
        TradingPairResponse response = new TradingPairResponse(SYMBOL, entity.getPrice(), FROM);
        when(repository.findAll(ArgumentMatchers.<Specification<TradingPair>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(mapper.toResponse(entity)).thenReturn(response);

        Page<TradingPairResponse> result = service.getAllWithinRange(SYMBOL, FROM, TO, pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isZero();
    }

    @Test
    void getTrend_shouldBeUpward_whenSecondHalfAverageIsHigher() {
        stubPoints("1", "2", "3", "4");

        TrendResponse result = service.getTrend(SYMBOL, FROM, TO);

        assertThat(result.trend()).isEqualTo(TradingPairTrendType.UPWARD);
        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.startDate()).isEqualTo(FROM);
        assertThat(result.endDate()).isEqualTo(TO);
    }

    @Test
    void getTrend_shouldBeDownward_whenSecondHalfAverageIsLower() {
        stubPoints("4", "3", "2", "1");

        assertThat(service.getTrend(SYMBOL, FROM, TO).trend()).isEqualTo(TradingPairTrendType.DOWNWARD);
    }

    @Test
    void getTrend_shouldBeFlat_whenHalvesHaveEqualAverages() {
        stubPoints("5", "5", "5", "5");

        assertThat(service.getTrend(SYMBOL, FROM, TO).trend()).isEqualTo(TradingPairTrendType.FLAT);
    }

    @Test
    void getTrend_shouldThrowNoData_whenNoPoints() {
        stubPoints();

        assertThatThrownBy(() -> service.getTrend(SYMBOL, FROM, TO))
                .isInstanceOf(NoPriceDataException.class);
    }
    
    @Test
    void getTrend_shouldBeFlat_whenTwoPointsAreEqual() {
        stubPoints("5", "5");

        assertThat(service.getTrend(SYMBOL, FROM, TO).trend()).isEqualTo(TradingPairTrendType.FLAT);
    }

    private void stubPoints(String... prices) {
        List<TradingPair> points = Stream.of(prices)
                .map(price -> entity(price, FROM))
                .toList();
        when(repository.findAllBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(SYMBOL, FROM, TO))
                .thenReturn(points);
    }
}
