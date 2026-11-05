package com.mukha.tradingpair.mapper;

import com.mukha.tradingpair.dto.response.TradingPairResponse;
import com.mukha.tradingpair.model.TradingPair;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradingPairMapper {

    TradingPairResponse toResponse(TradingPair tradingPair);
}
