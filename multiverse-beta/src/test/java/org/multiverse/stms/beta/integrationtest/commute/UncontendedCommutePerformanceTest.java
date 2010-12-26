package org.multiverse.stms.beta.integrationtest.commute;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.api.AtomicBlock;
import org.multiverse.api.LockLevel;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.api.functions.Functions;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmTestUtils.newLongRef;
import static org.multiverse.stms.beta.benchmarks.BenchmarkUtils.transactionsPerSecondAsString;

public class UncontendedCommutePerformanceTest {
    private volatile boolean stop;
    private BetaStm stm;
    private BetaLongRef ref;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stm = (BetaStm) getGlobalStmInstance();
        ref = newLongRef(stm);
    }

    @Test
    public void withNormalIncrement() {
        NormalIncThread thread = new NormalIncThread();

        startAll(thread);
        long durationMs = getStressTestDurationMs(30 * 1000);
        sleepMs(durationMs);
        stop = true;
        joinAll(thread);

        long transactionCount = ref.atomicGet();
        String performance = transactionsPerSecondAsString(transactionCount, durationMs);
        System.out.println(performance + " Transactions/second");
    }

    @Test
    public void withCommuteIncrement() {
        CommuteIncThread thread = new CommuteIncThread();

        startAll(thread);
        long durationMs = getStressTestDurationMs(30 * 1000);
        sleepMs(durationMs);
        stop = true;
        joinAll(thread);

        long transactionCount = ref.atomicGet();
        String performance = transactionsPerSecondAsString(transactionCount, durationMs);
        System.out.println(performance + " Transactions/second");
    }

    public class NormalIncThread extends TestThread {
        public NormalIncThread() {
            super("NormalIncThread");
        }

        @Override
        public void doRun() throws Exception {
            AtomicBlock block = stm.createTransactionFactoryBuilder()
                    .setLockLevel(LockLevel.CommitLockReads)
                    .setDirtyCheckEnabled(false)
                    .buildAtomicBlock();

            AtomicVoidClosure closure = new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    BetaTransaction btx = (BetaTransaction) tx;
                    btx.openForWrite(ref, LOCKMODE_NONE).value++;
                }
            };

            while (!stop) {
                block.execute(closure);
            }
        }
    }

    public class CommuteIncThread extends TestThread {
        public CommuteIncThread() {
            super("CommuteIncThread");
        }

        @Override
        public void doRun() throws Exception {
            AtomicBlock block = stm.createTransactionFactoryBuilder()
                    .buildAtomicBlock();

            AtomicVoidClosure closure = new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    BetaTransaction btx = (BetaTransaction) tx;
                    btx.commute(ref, Functions.newIncLongFunction(1));
                }
            };

            while (!stop) {
                block.execute(closure);
            }
        }
    }
}