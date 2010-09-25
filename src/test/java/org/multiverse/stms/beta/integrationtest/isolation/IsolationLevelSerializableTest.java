package org.multiverse.stms.beta.integrationtest.isolation;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.IsolationLevel;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.api.exceptions.ReadWriteConflict;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaTransactionFactory;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;

public class IsolationLevelSerializableTest {

    private BetaStm stm;
    private BetaTransactionFactory transactionFactory;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
        transactionFactory = stm.createTransactionFactoryBuilder()
                .setSpeculativeConfigurationEnabled(false)
                .setIsolationLevel(IsolationLevel.Serializable)
                .build();
    }

    @Test
    @Ignore
    public void unrepeatableRead() {

    }

    @Test
    public void causalConsistencyViolationNotPossible() {
        final BetaLongRef ref1 = newLongRef(stm);
        final BetaLongRef ref2 = newLongRef(stm);

        BetaTransaction tx = transactionFactory.newTransaction();

        ref1.get(tx);

        stm.getDefaultAtomicBlock().execute(new AtomicVoidClosure() {
            @Override
            public void execute(Transaction tx) throws Exception {
                ref1.incrementAndGet(1);
                ref2.incrementAndGet(1);
            }
        });

        try {
            ref2.get(tx);
            fail();
        } catch (ReadWriteConflict expected) {

        }
    }

    @Test
    public void writeSkewNotPossible() {
        final BetaLongRef ref1 = newLongRef(stm);
        final BetaLongRef ref2 = newLongRef(stm);

        BetaTransaction tx = transactionFactory.newTransaction();
        ref1.get(tx);

        ref2.incrementAndGet(tx, 1);

        ref1.atomicIncrementAndGet(1);

        try {
            tx.commit();
            fail();
        } catch (ReadWriteConflict expected) {

        }

        assertEquals(1, ref1.atomicGet());
        assertEquals(0, ref2.atomicGet());
    }
}
