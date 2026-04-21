package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        void shouldReturnSameLockForSameInstrument() {
            StampedLock first  = registry.getLock(BTC_BRL);
            StampedLock second = registry.getLock(BTC_BRL);
            assertSame(first, second);
        }

        @Test
        void shouldReturnDistinctLocksForDifferentInstruments() {
            StampedLock lockA = registry.getLock(BTC_BRL);
            StampedLock lockB = registry.getLock(BRL_BTC);
            assertNotSame(lockA, lockB);
        }

        @Test
        void shouldReturnSameLockForEqualInstrumentInstances() {
            Instrument a = new Instrument(Asset.BTC, Asset.BRL);
            Instrument b = new Instrument(Asset.BTC, Asset.BRL);
            assertSame(registry.getLock(a), registry.getLock(b));
        }

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class, () -> registry.getLock(null));
        }
    }

    @Nested
    class LockBehavior {

        @Test
        void returnedLockShouldSupportWriteLocking() {
            StampedLock lock = registry.getLock(BTC_BRL);
            long stamp = lock.writeLock();
            try {
                assertTrue(stamp != 0);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        @Test
        void returnedLockShouldSupportReadLocking() {
            StampedLock lock = registry.getLock(BTC_BRL);
            long stamp = lock.readLock();
            try {
                assertTrue(stamp != 0);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        @Test
        void returnedLockShouldSupportOptimisticRead() {
            StampedLock lock = registry.getLock(BTC_BRL);
            long stamp = lock.tryOptimisticRead();
            assertTrue(lock.validate(stamp));
        }

        @Test
        void writeLockShouldInvalidateOptimisticReadStamp() {
            StampedLock lock = registry.getLock(BTC_BRL);
            long optimistic = lock.tryOptimisticRead();

            long write = lock.writeLock();
            try {
                assertFalse(lock.validate(optimistic));
            } finally {
                lock.unlockWrite(write);
            }
        }

        @Test
        void locksForDifferentInstrumentsShouldBeIndependent() {
            StampedLock lockA = registry.getLock(BTC_BRL);
            StampedLock lockB = registry.getLock(BRL_BTC);

            long stampA = lockA.writeLock();
            try {
                // acquiring BRL_BTC write lock must not block while BTC_BRL write lock is held
                long stampB = lockB.tryWriteLock();
                assertTrue(stampB != 0, "BRL_BTC lock should be acquirable independently of BTC_BRL lock");
                lockB.unlockWrite(stampB);
            } finally {
                lockA.unlockWrite(stampA);
            }
        }
    }

    @Nested
    class Concurrency {

        @Test
        void concurrentCallsShouldAlwaysReturnSameLockInstance() throws InterruptedException {
            int threads = 16;
            Set<StampedLock> seen = ConcurrentHashMap.newKeySet();
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>(threads);

            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    seen.add(registry.getLock(BTC_BRL));
                });
                workers.add(t);
                t.start();
            }

            ready.await();
            start.countDown();
            for (Thread t : workers) {
                t.join(2_000);
            }

            assertEquals(1, seen.size(), "All threads must observe the same StampedLock instance");
        }

        @Test
        void concurrentWritesShouldMutuallyExclude() throws InterruptedException {
            int threads = 8;
            int[] counter = {0};
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            StampedLock lock = registry.getLock(BTC_BRL);
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    long stamp = lock.writeLock();
                    try {
                        int current = counter[0];
                        Thread.yield();
                        counter[0] = current + 1;
                    } finally {
                        lock.unlockWrite(stamp);
                        done.countDown();
                    }
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(threads, counter[0], "Write lock must prevent lost updates");
        }
    }
}
