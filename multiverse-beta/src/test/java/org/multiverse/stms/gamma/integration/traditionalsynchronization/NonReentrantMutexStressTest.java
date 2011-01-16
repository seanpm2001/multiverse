package org.multiverse.stms.gamma.integration.traditionalsynchronization;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.TestUtils;
import org.multiverse.api.AtomicBlock;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.stms.gamma.GammaStm;
import org.multiverse.stms.gamma.transactionalobjects.GammaLongRef;
import org.multiverse.stms.gamma.transactionalobjects.GammaRefTranlocal;
import org.multiverse.stms.gamma.transactions.GammaTransaction;

import static org.junit.Assert.assertEquals;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.StmUtils.retry;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

/**
 * A stresstest that checks if the NonReentrantMutex; a traditional synchronization structure, can be build
 * using stm. It isn't meant as a replacement for Mutex, but just to see if the system behaves like it should.
 *
 * @author Peter Veentjer.
 */
public class NonReentrantMutexStressTest {

    private volatile boolean stop;
    private int accountCount = 50;

    private int threadCount = processorCount() * 4;
    private ProtectedIntValue[] intValues;
    private GammaStm stm;
    private boolean pessimistic;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stm = (GammaStm) getGlobalStmInstance();
        stop = false;
    }

    @Test
    public void testPessimistic() {
        test(true);
    }

    @Test
    public void testOptimistic() {
        test(false);
    }

    public void test(boolean pessimistic) {
        this.pessimistic = pessimistic;

        intValues = new ProtectedIntValue[accountCount];
        for (int k = 0; k < accountCount; k++) {
            intValues[k] = new ProtectedIntValue();
        }

        IncThread[] threads = new IncThread[threadCount];
        for (int k = 0; k < threads.length; k++) {
            threads[k] = new IncThread(k);
        }

        startAll(threads);
        sleepMs(TestUtils.getStressTestDurationMs(60 * 1000));
        stop = true;
        joinAll(threads);

        assertEquals(sum(threads), sum(intValues));
        System.out.println("total increments: " + sum(threads));
    }

    int sum(IncThread[] threads) {
        int result = 0;
        for (IncThread thread : threads) {
            result += thread.count;
        }
        return result;
    }

    int sum(ProtectedIntValue[] intValues) {
        int result = 0;
        for (ProtectedIntValue intValue : intValues) {
            result += intValue.balance;
        }
        return result;
    }

    class IncThread extends TestThread {
        private int count;

        public IncThread(int id) {
            super("IncThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            while (!stop) {
                ProtectedIntValue intValue = intValues[TestUtils.randomInt(accountCount)];
                intValue.inc();

                if (count % 500000 == 0) {
                    System.out.printf("%s is at %s\n", getName(), count);
                }

                count++;
            }
        }
    }

    class ProtectedIntValue {
        final NonReentrantMutex mutex = new NonReentrantMutex();

        int balance;

        public void inc() {
            mutex.lock();
            balance++;
            mutex.unlock();
        }
    }

    class NonReentrantMutex {
        final GammaLongRef locked = new GammaLongRef(stm, 0);
        final AtomicBlock lockBlock = stm.createTransactionFactoryBuilder()
                .buildAtomicBlock();
        final AtomicBlock unlockBlock = stm.createTransactionFactoryBuilder()
                .buildAtomicBlock();

        final AtomicVoidClosure lockClosure = new AtomicVoidClosure() {
            @Override
            public void execute(Transaction tx) throws Exception {
                GammaTransaction btx = (GammaTransaction) tx;

                GammaRefTranlocal read = locked.openForRead(btx, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
                if (read.long_value == 1) {
                    retry();
                }

                GammaRefTranlocal write = locked.openForWrite(btx, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
                write.long_value = 1;
            }
        };

        final AtomicVoidClosure unlockClosure = new AtomicVoidClosure() {
            @Override
            public void execute(Transaction tx) throws Exception {
                GammaTransaction btx = (GammaTransaction) tx;

                GammaRefTranlocal write = locked.openForWrite(btx, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
                if (write.long_value == 0) {
                    throw new IllegalStateException();
                }

                write.long_value = 0;
            }
        };

        public void lock() {
            lockBlock.execute(lockClosure);
        }

        public void unlock() {
            unlockBlock.execute(unlockClosure);
        }
    }
}