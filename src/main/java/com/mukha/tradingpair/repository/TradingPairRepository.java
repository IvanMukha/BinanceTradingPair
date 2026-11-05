package com.mukha.tradingpair.repository;

import com.mukha.tradingpair.model.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradingPairRepository extends JpaRepository<TradingPair, Long>, JpaSpecificationExecutor<TradingPair> {

    Optional<TradingPair> findFirstBySymbolOrderByFetchedAtDesc(String symbol);

    @Query("SELECT AVG(tp.price) FROM TradingPair tp WHERE tp.symbol = :symbol AND tp.fetchedAt BETWEEN :fetchedFrom AND :fetchedTo")
    BigDecimal getAveragePrice(
            @Param("symbol") String symbol,
            @Param("fetchedFrom") Instant fetchedFrom,
            @Param("fetchedTo") Instant fetchedTo
    );

    List<TradingPair> findAllBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(
            String symbol, Instant from, Instant to);
}

