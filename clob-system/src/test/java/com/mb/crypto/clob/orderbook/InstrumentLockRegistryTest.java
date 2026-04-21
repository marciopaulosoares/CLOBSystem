package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentLockRegistryTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final Instrument BRL_BTC = new Instrument(Asset.BRL, Asset.BTC);

    private InstrumentLockRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InstrumentLockRegistry();
    }

    @Nested
    class GetLock {

        @Test
        void shouldReturnNonNullLock() {
            assertNotNull(registry.getLock(BTC_BRL));
        }

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class, () -> registry.getLock(null));
        }

        @Test
        void shouldReturnSameLockInstanceForSameInstrument() {
            StampedLock first = registry.getLock(BTC_BRL);
            StampedLock second = registry.getLock(BTC_BRL);
            assertSame(first, second);
        }

        @Test
        void shouldReturnDifferentLockInstancesForDifferentInstruments() {
            assertNotSame(registry.getLock(BTC_BRL), registry.getLock(BRL_BTC));
        }

        @Test
        void shouldReturnAFunctionalWritableLock() {
            StampedLock lock = registry.getLock(BTC_BRL);
            long stamp = lock.writeLock();
            try {
                assertNotEquals(0L, stamp);
            } finally {
                lock.unlockWrite(stamp);
            }
        }
    }

    @Nested
    class Concurrency {

        private static final int THREADS = 32;

        @RepeatedTest(5)
        void shouldReturnSameLockInstanceUnderConcurrentAccess() throws InterruptedException {
            Set<StampedLock> observed = ConcurrentHashMap.newKeySet();
            CyclicBarrier barrier = new CyclicBarrier(THREADS);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        observed.add(registry.getLock(BTC_BRL));
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(1, observed.size(), "All threads must receive the exact same StampedLock instance");
        }

        @RepeatedTest(5)
        void shouldReturnDistinctLocksForDifferentInstrumentsConcurrently() throws InterruptedException {
            ConcurrentHashMap<Instrument, StampedLock> observed = new ConcurrentHashMap<>();
            Instrument[] instruments = {BTC_BRL, BRL_BTC};
            CyclicBarrier barrier = new CyclicBarrier(THREADS);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                Instrument instrument = instruments[i % instruments.length];
                pool.submit(() -> {
                    try {
                        barrier.await();
                        StampedLock lock = registry.getLock(instrument);
                        observed.putIfAbsent(instrument, lock);
                        assertSame(observed.get(instrument), lock);
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(2, observed.size());
            assertNotSame(observed.get(BTC_BRL), observed.get(BRL_BTC));
        }

        @RepeatedTest(3)
        void shouldAllowConcurrentWriteLocksOnDifferentInstruments() throws InterruptedException {
            AtomicInteger concurrentHolders = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            CyclicBarrier barrier = new CyclicBarrier(2);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            for (Instrument instrument : new Instrument[]{BTC_BRL, BRL_BTC}) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        StampedLock lock = registry.getLock(instrument);
                        long stamp = lock.writeLock();
                        int current = concurrentHolders.incrementAndGet();
                        maxConcurrent.accumulateAndGet(current, Math::max);
                        Thread.sleep(20);
                        concurrentHolders.decrementAndGet();
                        lock.unlockWrite(stamp);
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(2, maxConcurrent.get(),
                "Write locks on different instruments must not block each other");
        }

        @RepeatedTest(3)
        void shouldBlockConcurrentWriteLocksOnSameInstrument() throws InterruptedException {
            AtomicInteger concurrentHolders = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            CyclicBarrier barrier = new CyclicBarrier(2);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await();
                        StampedLock lock = registry.getLock(BTC_BRL);
                        long stamp = lock.writeLock();
                        int current = concurrentHolders.incrementAndGet();
                        maxConcurrent.accumulateAndGet(current, Math::max);
                        Thread.sleep(20);
                        concurrentHolders.decrementAndGet();
                        lock.unlockWrite(stamp);
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(1, maxConcurrent.get(),
                "Write locks on the same instrument must be mutually exclusive");
        }
    }
}
