package com.mb.crypto.clob.domain;

import java.util.Objects;

/**
 * Immutable value object that uniquely identifies an account.
 * Implemented as a record to enforce identity-by-value semantics in maps and sets.
 */
public record AccountId(String value) {

    /**
     * Validates that the account identifier is non-null and non-blank.
     */
    public AccountId {
        Objects.requireNonNull(value, "AccountId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AccountId value cannot be blank");
        }
    }
}
