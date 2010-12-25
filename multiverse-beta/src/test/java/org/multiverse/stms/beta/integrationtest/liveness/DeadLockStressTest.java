package org.multiverse.stms.beta.integrationtest.liveness;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.api.AtomicBlock;
import org.multiverse.api.LockLevel;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactionalobjects.BetaIntRef;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmTestUtils.newIntRef;

public class DeadLockStressTest {

    enum Mode {
        Normal, Mix, PrivatizeReadLevelMode, PrivatizeWriteLevelMode
    }

    private volatile boolean stop;
    private int refCount = 100;
    private int threadCount = 10;
    private BetaIntRef[] refs;
    private ChangeThread[] threads;
    private BetaStm stm;
    private Mode mode;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stop = false;
        stm = (BetaStm) getGlobalStmInstance();
    }

    @Test
    public void whenNormal() {
        test(Mode.Normal);
    }

    @Test
    public void whenMix() {
        test(Mode.Mix);
    }

    @Test
    public void whenPessimisticReadLevel() {
        test(Mode.PrivatizeReadLevelMode);
    }

    @Test
    public void whenPessimisticWriteLevel() {
        test(Mode.PrivatizeWriteLevelMode);
    }

    public void test(Mode mode) {
        this.mode = mode;

        refs = new BetaIntRef[refCount];
        for (int k = 0; k < refCount; k++) {
            refs[k] = newIntRef(stm);
        }

        threads = new ChangeThread[threadCount];
        for (int k = 0; k < threadCount; k++) {
            threads[k] = new ChangeThread(k);
        }

        startAll(threads);
        sleepMs(getStressTestDurationMs(60 * 1000));
        stop = true;
        joinAll(threads);
    }

    public class ChangeThread extends TestThread {

        private final AtomicBlock normalBlock = stm.createTransactionFactoryBuilder()
                .buildAtomicBlock();

        private final AtomicBlock pessimisticReadLevelBlock = stm.createTransactionFactoryBuilder()
                .setLockLevel(LockLevel.CommitLockReads)
                .buildAtomicBlock();

        private final AtomicBlock pessimisticWriteLevelBlock = stm.createTransactionFactoryBuilder()
                .setLockLevel(LockLevel.CommitLockWrites)
                .buildAtomicBlock();

        public ChangeThread(int id) {
            super("ChangeThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            int k = 0;
            while (!stop) {
                if (k % 100000 == 0) {
                    System.out.printf("%s is at %s\n", getName(), k);
                }
                switch (mode) {
                    case PrivatizeReadLevelMode:
                        privatizeReadLevel();
                        break;
                    case PrivatizeWriteLevelMode:
                        privatizeWriteLevel();
                        break;
                    case Normal:
                        normal();
                        break;
                    case Mix:
                        switch (randomInt(3)) {
                            case 0:
                                privatizeReadLevel();
                                break;
                            case 1:
                                privatizeWriteLevel();
                                break;
                            case 2:
                                normal();
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    default:
                        throw new IllegalStateException();

                }
                k++;
            }
        }

        public void normal() {
            normalBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    doIt((BetaTransaction) tx);
                }
            });
        }

        public void privatizeReadLevel() {
            pessimisticReadLevelBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    doIt((BetaTransaction) tx);
                }
            });
        }

        public void privatizeWriteLevel() {
            pessimisticWriteLevelBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    doIt((BetaTransaction) tx);
                }
            });
        }


        public void doIt(BetaTransaction tx) {
            for (int k = 0; k < refs.length; k++) {
                if (!randomOneOf(10)) {
                    continue;
                }

                int index = randomInt(refs.length);
                BetaIntRef ref = refs[index];
                ref.getAndSet(tx, ref.get(tx) + 1);
            }
        }
    }
}
