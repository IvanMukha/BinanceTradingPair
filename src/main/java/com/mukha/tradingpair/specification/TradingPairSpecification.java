package com.mukha.tradingpair.specification;

import com.mukha.tradingpair.model.TradingPair;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class TradingPairSpecification {

    public static Specification<TradingPair> fetchedWithinRange(String symbol, Instant fetchedFrom, Instant fetchedTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get(TradingPair.Fields.symbol), symbol));
            if (fetchedFrom != null && fetchedTo != null) {
                predicates.add(cb.between(root.get(TradingPair.Fields.fetchedAt), fetchedFrom, fetchedTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
