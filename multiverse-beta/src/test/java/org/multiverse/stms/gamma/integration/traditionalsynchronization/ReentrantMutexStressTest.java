package org.multiverse.stms.gamma.integration.traditionalsynchronization;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.api.LockMode;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.api.references.IntRef;
import org.multiverse.api.references.Ref;
import org.multiverse.stms.gamma.GammaAtomicBlock;
import org.multiverse.stms.gamma.GammaStm;

import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.StmUtils.*;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

public class ReentrantMutexStressTest {
    private GammaStm stm;
    private int threadCount = 10;
    private ReentrantMutex mutex;
    private volatile boolean stop;

    @Before
    public void setUp() {
        stm = (GammaStm) getGlobalStmInstance();
        clearThreadLocalTransaction();
        stop = false;
    }

    @Test
    public void testNoLocking() {
        test(LockMode.None);
    }

    @Test
    public void testReadLock() {
        test(LockMode.Read);
    }

    @Test
    public void testWriteLock() {
        test(LockMode.Write);
    }

    @Test
    public void testExclusiveLock() {
        test(LockMode.Exclusive);
    }

    public void test(LockMode lockMode) {
        mutex = new ReentrantMutex(lockMode);
        StressThread[] threads = new StressThread[threadCount];
        for (int k = 0; k < threads.length; k++) {
            threads[k] = new StressThread(k);
        }

        startAll(threads);
        sleepMs(30000);
        stop = true;
        joinAll(threads);
    }

    class StressThread extends TestThread {
        public StressThread(int id) {
            super("StressThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            long count = 0;
            while (!stop) {
                mutex.lock(this);

                boolean nested = randomOneOf(3);
                if (nested) {
                    mutex.lock(this);
                }

                sleepRandomMs(100);
                mutex.unlock(this);

                if (nested) {
                    mutex.unlock(this);
                }

                count++;
                if (count % 10 == 0) {
                    System.out.printf("%s is at %s\n", getName(), count);
                }
            }
        }
    }

    class ReentrantMutex {
        private final Ref<Thread> owner = newRef();
        private final IntRef count = newIntRef();
        private final GammaAtomicBlock lockBlock;
        private final GammaAtomicBlock unlockBlock;

        ReentrantMutex(LockMode readLockMode) {
            lockBlock = stm.newTransactionFactoryBuilder()
                    .setReadLockMode(readLockMode)
                    .buildAtomicBlock();
            unlockBlock = stm.newTransactionFactoryBuilder()
                    .setReadLockMode(readLockMode)
                    .buildAtomicBlock();
        }

        public void lock(final Thread thread) {
            lockBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    if (owner.get() == null) {
                        owner.set(thread);
                        count.increment();
                        return;
                    }

                    if (owner.get() == thread) {
                        count.increment();
                        return;
                    }

                    retry();
                }
            });
        }

        public void unlock(final Thread thread) {
            unlockBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    if (owner.get() != thread) {
                        throw new IllegalMonitorStateException();
                    }

                    count.decrement();
                    if (count.get() == 0) {
                        owner.set(null);
                    }
                }
            });
        }
    }
}
