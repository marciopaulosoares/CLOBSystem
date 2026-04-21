package com.mb.crypto.clob.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BalanceTest {

    private Balance balance;

    @BeforeEach
    void setUp() {
        balance = new Balance(new BigDecimal("1000.00"));
    }

    @Nested
    class Constructor {

        @Test
        void shouldSetAvailableAndZeroLocked() {
            assertEquals(new BigDecimal("1000.00"), balance.getAvailable());
            assertEquals(BigDecimal.ZERO, balance.getLocked());
        }

        @Test
        void shouldRejectNullAvailable() {
            assertThrows(NullPointerException.class, () -> new Balance(null));
        }
    }

    @Nested
    class Accessors {

        @Test
        void shouldReturnAvailableAsTotalWhenNothingIsLocked() {
            assertEquals(new BigDecimal("1000.00"), balance.getTotal());
        }

        @Test
        void shouldReturnSumOfAvailableAndLockedAsTotal() {
            balance.lock(new BigDecimal("300.00"));
            assertEquals(new BigDecimal("1000.00"), balance.getTotal());
        }
    }

    @Nested
    class AddAvailable {

        @Test
        void shouldIncreaseAvailable() {
            balance.addAvailable(new BigDecimal("500.00"));
            assertEquals(new BigDecimal("1500.00"), balance.getAvailable());
        }

        @Test
        void shouldNotAffectLocked() {
            balance.lock(new BigDecimal("200.00"));
            BigDecimal lockedBefore = balance.getLocked();
            balance.addAvailable(new BigDecimal("500.00"));
            assertEquals(lockedBefore, balance.getLocked());
        }
    }

    @Nested
    class SubtractAvailable {

        @Test
        void shouldDecreaseAvailable() {
            balance.subtractAvailable(new BigDecimal("400.00"));
            assertEquals(new BigDecimal("600.00"), balance.getAvailable());
        }

        @Test
        void shouldAllowSubtractingExactAvailableAmount() {
            balance.subtractAvailable(new BigDecimal("1000.00"));
            assertEquals(0, balance.getAvailable().compareTo(BigDecimal.ZERO));
        }

        @Test
        void shouldThrowWhenAmountExceedsAvailable() {
            assertThrows(IllegalArgumentException.class,
                () -> balance.subtractAvailable(new BigDecimal("1500.00")));
        }
    }

    @Nested
    class Lock {

        @Test
        void shouldMoveAmountFromAvailableToLocked() {
            balance.lock(new BigDecimal("250.00"));
            assertEquals(new BigDecimal("750.00"), balance.getAvailable());
            assertEquals(new BigDecimal("250.00"), balance.getLocked());
        }

        @Test
        void shouldNotChangeTotalBalance() {
            BigDecimal totalBefore = balance.getTotal();
            balance.lock(new BigDecimal("400.00"));
            assertEquals(totalBefore, balance.getTotal());
        }

        @Test
        void shouldThrowWhenInsufficientAvailable() {
            assertThrows(IllegalArgumentException.class,
                () -> balance.lock(new BigDecimal("2000.00")));
        }
    }

    @Nested
    class Unlock {

        @Test
        void shouldMoveAmountFromLockedToAvailable() {
            balance.lock(new BigDecimal("500.00"));
            balance.unlock(new BigDecimal("200.00"));
            assertEquals(new BigDecimal("700.00"), balance.getAvailable());
            assertEquals(new BigDecimal("300.00"), balance.getLocked());
        }

        @Test
        void shouldNotChangeTotalBalance() {
            balance.lock(new BigDecimal("500.00"));
            BigDecimal totalAfterLock = balance.getTotal();
            balance.unlock(new BigDecimal("500.00"));
            assertEquals(totalAfterLock, balance.getTotal());
        }

        @Test
        void shouldAllowPartialUnlock() {
            balance.lock(new BigDecimal("600.00"));
            balance.unlock(new BigDecimal("200.00"));
            assertEquals(new BigDecimal("600.00"), balance.getAvailable());
            assertEquals(new BigDecimal("400.00"), balance.getLocked());
        }
    }

    @Nested
    class Debit {

        @Test
        void shouldDecreaseLockedToZero() {
            balance.lock(new BigDecimal("600.00"));
            balance.debit(new BigDecimal("600.00"));
            assertEquals(0, balance.getLocked().compareTo(BigDecimal.ZERO));
        }

        @Test
        void shouldDecreaseTotalBalance() {
            balance.lock(new BigDecimal("600.00"));
            BigDecimal totalBefore = balance.getTotal();
            balance.debit(new BigDecimal("600.00"));
            assertEquals(totalBefore.subtract(new BigDecimal("600.00")), balance.getTotal());
        }

        @Test
        void shouldAllowPartialDebit() {
            balance.lock(new BigDecimal("600.00"));
            balance.debit(new BigDecimal("200.00"));
            assertEquals(new BigDecimal("400.00"), balance.getLocked());
            assertEquals(new BigDecimal("800.00"), balance.getTotal());
        }
    }

    @Nested
    class Credit {

        @Test
        void shouldIncreaseAvailable() {
            balance.credit(new BigDecimal("300.00"));
            assertEquals(new BigDecimal("1300.00"), balance.getAvailable());
        }

        @Test
        void shouldIncreaseTotalBalance() {
            BigDecimal totalBefore = balance.getTotal();
            balance.credit(new BigDecimal("300.00"));
            assertEquals(totalBefore.add(new BigDecimal("300.00")), balance.getTotal());
        }

        @Test
        void shouldNotAffectLocked() {
            balance.lock(new BigDecimal("200.00"));
            BigDecimal lockedBefore = balance.getLocked();
            balance.credit(new BigDecimal("500.00"));
            assertEquals(lockedBefore, balance.getLocked());
        }
    }
}
