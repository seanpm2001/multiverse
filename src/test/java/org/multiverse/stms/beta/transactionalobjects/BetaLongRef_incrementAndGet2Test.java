package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.multiverse.TestUtils.assertIsAborted;
import static org.multiverse.TestUtils.assertIsCommitted;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmUtils.assertVersionAndValue;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;

public class BetaLongRef_incrementAndGet2Test {

    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
    }

    @Test
    public void whenTransactionNull_thenNullPointerException() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        try {
            ref.incrementAndGet(null, 10);
            fail();
        } catch (NullPointerException expected) {
        }

        assertVersionAndValue(ref, version, 10);
    }

    @Test
    public void whenTransactionCommitted_thenDeadTransactionException() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.commit();
        try {
            ref.incrementAndGet(tx, 10);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsCommitted(tx);
        assertVersionAndValue(ref, version, 10);
    }

    @Test
    public void whenTransactionAborted_thenDeadTransactionException() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.abort();
        try {
            ref.incrementAndGet(tx, 10);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, version, 10);
    }

    @Test
    public void whenTransactionPrepared_thenPreparedTransactionException() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.prepare();
        try {
            ref.incrementAndGet(tx, 10);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, version, 10);
    }

    @Test
    public void whenNoChange() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        long result = ref.incrementAndGet(tx, 0);
        tx.commit();

        assertEquals(10, result);
        assertIsCommitted(tx);
        assertVersionAndValue(ref, version, 10);
    }

    @Test
    public void whenSuccess() {
        BetaLongRef ref = newLongRef(stm, 10);
        long version = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        long result = ref.incrementAndGet(tx, 20);
        tx.commit();

        assertIsCommitted(tx);
        assertEquals(30, result);
        assertVersionAndValue(ref, version+1, 30);
    }
}
