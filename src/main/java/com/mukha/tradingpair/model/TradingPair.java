package com.mukha.tradingpair.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trading_pair")
@Getter
@Setter
@FieldNameConstants
public class TradingPair {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trading_pair_seq_generator")
    @SequenceGenerator(name = "trading_pair_seq_generator", sequenceName = "trading_pair_seq", allocationSize = 1)
    private Long id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "price", nullable = false, precision = 24, scale = 10)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
