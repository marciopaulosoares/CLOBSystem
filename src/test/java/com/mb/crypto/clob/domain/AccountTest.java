package com.mb.crypto.clob.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static final AccountId ACCOUNT_ID = new AccountId("test-account");

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account(ACCOUNT_ID);
    }

    @Nested
    class Constructor {

        @Test
        void shouldRejectNullAccountId() {
            assertThrows(NullPointerException.class, () -> new Account(null));
        }

        @Test
        void shouldExposeAccountId() {
            assertEquals(ACCOUNT_ID, account.getId());
        }

        @Test
        void shouldStartWithEmptyTradeHistory() {
            assertTrue(account.getTradeHistory().isEmpty());
        }

        @Test
        void shouldReturnZeroBalanceForUnknownAsset() {
            assertEquals(BigDecimal.ZERO, account.getBalance(Asset.BTC));
        }
    }

    @Nested
    class Deposit {

        @Test
        void shouldCreditAvailableBalance() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            assertEquals(new BigDecimal("1000"), account.getBalance(Asset.BRL));
        }

        @Test
        void shouldAccumulateMultipleDeposits() {
            account.deposit(Asset.BRL, new BigDecimal("500"));
            account.deposit(Asset.BRL, new BigDecimal("300"));
            assertEquals(new BigDecimal("800"), account.getBalance(Asset.BRL));
        }

        @Test
        void shouldTrackDifferentAssetsIndependently() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            account.deposit(Asset.BTC, new BigDecimal("2"));
            assertEquals(new BigDecimal("1000"), account.getBalance(Asset.BRL));
            assertEquals(new BigDecimal("2"), account.getBalance(Asset.BTC));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.deposit(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.deposit(Asset.BRL, null));
        }

        @Test
        void shouldRejectZeroAmount() {
            assertThrows(IllegalArgumentException.class, () -> account.deposit(Asset.BRL, BigDecimal.ZERO));
        }

        @Test
        void shouldRejectNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> account.deposit(Asset.BRL, new BigDecimal("-1")));
        }
    }

    @Nested
    class Withdraw {

        @Test
        void shouldDebitAvailableBalance() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            account.withdraw(Asset.BRL, new BigDecimal("400"));
            assertEquals(new BigDecimal("600"), account.getBalance(Asset.BRL));
        }

        @Test
        void shouldAllowFullWithdrawal() {
            account.deposit(Asset.BRL, new BigDecimal("100"));
            account.withdraw(Asset.BRL, new BigDecimal("100"));
            assertEquals(BigDecimal.ZERO, account.getBalance(Asset.BRL));
        }

        @Test
        void shouldRejectWithdrawalExceedingAvailableBalance() {
            account.deposit(Asset.BRL, new BigDecimal("100"));
            assertThrows(IllegalArgumentException.class, () -> account.withdraw(Asset.BRL, new BigDecimal("101")));
        }

        @Test
        void shouldRejectWithdrawalWithNoBalance() {
            assertThrows(IllegalArgumentException.class, () -> account.withdraw(Asset.BRL, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.withdraw(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.withdraw(Asset.BRL, null));
        }

        @Test
        void shouldRejectZeroAmount() {
            assertThrows(IllegalArgumentException.class, () -> account.withdraw(Asset.BRL, BigDecimal.ZERO));
        }

        @Test
        void shouldRejectNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> account.withdraw(Asset.BRL, new BigDecimal("-1")));
        }
    }

    @Nested
    class Lock {

        @Test
        void shouldPreserveTotalBalanceWhenLocking() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            account.lock(Asset.BRL, new BigDecimal("300"));
            assertEquals(new BigDecimal("1000"), account.getBalance(Asset.BRL));
            assertEquals(new BigDecimal("300"), account.getLockedBalance(Asset.BRL));
            assertEquals(new BigDecimal("700"), account.getAvailableBalance(Asset.BRL));
        }

        @Test
        void shouldRejectLockExceedingAvailableBalance() {
            account.deposit(Asset.BRL, new BigDecimal("100"));
            assertThrows(IllegalArgumentException.class, () -> account.lock(Asset.BRL, new BigDecimal("101")));
        }

        @Test
        void shouldRejectLockWithNoBalance() {
            assertThrows(IllegalArgumentException.class, () -> account.lock(Asset.BRL, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.lock(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.lock(Asset.BRL, null));
        }
    }

    @Nested
    class Unlock {

        @Test
        void shouldReturnLockedFundsToAvailable() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            account.lock(Asset.BRL, new BigDecimal("300"));
            account.unlock(Asset.BRL, new BigDecimal("300"));
            assertEquals(new BigDecimal("1000"), account.getBalance(Asset.BRL));
            assertEquals(new BigDecimal("0"), account.getLockedBalance(Asset.BRL));
            assertEquals(new BigDecimal("1000"), account.getAvailableBalance(Asset.BRL));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.unlock(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.unlock(Asset.BRL, null));
        }
    }

    @Nested
    class Debit {

        @Test
        void shouldReduceTotalBalanceByDebitingLockedFunds() {
            account.deposit(Asset.BRL, new BigDecimal("1000"));
            account.lock(Asset.BRL, new BigDecimal("500"));
            account.debit(Asset.BRL, new BigDecimal("500"));
            assertEquals(new BigDecimal("500"), account.getBalance(Asset.BRL));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.debit(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.debit(Asset.BRL, null));
        }
    }

    @Nested
    class Credit {

        @Test
        void shouldIncreaseAvailableBalance() {
            account.credit(Asset.BTC, new BigDecimal("1"));
            assertEquals(new BigDecimal("1"), account.getBalance(Asset.BTC));
        }

        @Test
        void shouldAccumulateCreditsOnExistingBalance() {
            account.deposit(Asset.BTC, new BigDecimal("1"));
            account.credit(Asset.BTC, new BigDecimal("0.5"));
            assertEquals(new BigDecimal("1.5"), account.getBalance(Asset.BTC));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class, () -> account.credit(null, BigDecimal.ONE));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> account.credit(Asset.BTC, null));
        }
    }

    @Nested
    class RecordTrade {

        @Test
        void shouldAddTradeToHistory() {
            Trade trade = sampleTrade();
            account.recordTrade(trade);
            assertEquals(1, account.getTradeHistory().size());
            assertSame(trade, account.getTradeHistory().get(0));
        }

        @Test
        void shouldAccumulateMultipleTrades() {
            account.recordTrade(sampleTrade());
            account.recordTrade(sampleTrade());
            assertEquals(2, account.getTradeHistory().size());
        }

        @Test
        void shouldReturnUnmodifiableTradeHistory() {
            account.recordTrade(sampleTrade());
            assertThrows(UnsupportedOperationException.class,
                () -> account.getTradeHistory().add(sampleTrade()));
        }

        @Test
        void shouldRejectNullTrade() {
            assertThrows(NullPointerException.class, () -> account.recordTrade(null));
        }
    }

    private Trade sampleTrade() {
        return new Trade(1L, 10L, 20L,
            new BigDecimal("0.5"), new BigDecimal("150000"),
            Instant.now(),
            new AccountId("buyer"),
            new AccountId("seller"));
    }
}
