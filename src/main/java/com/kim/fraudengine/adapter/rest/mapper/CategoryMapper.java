package com.kim.fraudengine.adapter.rest.mapper;

import com.kim.fraudengine.adapter.rest.dto.TransactionRequest;
import com.kim.fraudengine.domain.model.TransactionCategory;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static TransactionCategory toDomain(
            TransactionRequest.TransactionCategoryResponse category) {
        return switch (category) {
            case GROCERIES -> TransactionCategory.GROCERIES;
            case FUEL -> TransactionCategory.FUEL;
            case TRANSFER -> TransactionCategory.TRANSFER;
            case ENTERTAINMENT -> TransactionCategory.ENTERTAINMENT;
            case UTILITIES -> TransactionCategory.UTILITIES;
            case TRAVEL -> TransactionCategory.TRAVEL;
            case ONLINE_PURCHASE -> TransactionCategory.ONLINE_PURCHASE;
            case ATM_WITHDRAWAL -> TransactionCategory.ATM_WITHDRAWAL;
            case UNKNOWN -> TransactionCategory.UNKNOWN;
        };
    }
}
