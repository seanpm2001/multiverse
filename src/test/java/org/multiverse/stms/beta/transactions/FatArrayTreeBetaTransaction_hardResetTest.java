package org.multiverse.stms.beta.transactions;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.lifecycle.TransactionLifecycleEvent;
import org.multiverse.api.lifecycle.TransactionLifecycleListener;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.refs.LongRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.beta.BetaStmUtils.createLongRef;

public class FatArrayTreeBetaTransaction_hardResetTest {

    private BetaStm stm;
    private BetaObjectPool pool;

    @Before
    public void setUp() {
        stm = new BetaStm();
        pool = new BetaObjectPool();
    }

    @Test
    public void testThatAttemptsAreReset() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.softReset(pool);
        tx.softReset(pool);
        tx.softReset(pool);

        tx.hardReset(pool);

        assertActive(tx);
        assertEquals(1, tx.getAttempt());
    }

    @Test
    public void whenHasConstructed() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        LongRef ref = new LongRef(tx);
        tx.openForConstruction(ref, pool);

        tx.hardReset(pool);
        assertWasHardReset(tx);
    }

    @Test
    public void whenHasNormalRead() {
        LongRef ref = createLongRef(stm);
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForRead(ref, false, pool);

        tx.hardReset(pool);
        assertWasHardReset(tx);
    }

    @Test
    public void whenHasPermanentRead() {
        LongRef ref = createReadBiasedLongRef(stm);
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForRead(ref, false, pool);
        tx.hardReset(pool);
        assertWasHardReset(tx);
    }

    @Test
    public void whenHasWrite() {
        LongRef ref = createLongRef(stm);
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForWrite(ref, false, pool);
        tx.hardReset(pool);
        assertWasHardReset(tx);
    }

    private static void assertWasHardReset(FatArrayTreeBetaTransaction tx) {
        assertActive(tx);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(1, tx.getAttempt());
        assertFalse((Boolean) getField(tx, "hasReads"));
        //todo: needs to be enabled as soon as the non automatic readtracking is added
        //assertFalse((Boolean) getField(tx, "hasUntrackedReads"));
    }


    @Test
    public void whenTimeout() {
        BetaTransactionConfig config = new BetaTransactionConfig(stm)
                .setTimeoutNs(100);
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(config);
        tx.setRemainingTimeoutNs(50);
        tx.prepare(pool);

        tx.hardReset(pool);

        assertActive(tx);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(1, tx.getAttempt());
        assertEquals(100, tx.getRemainingTimeoutNs());
    }

    @Test
    public void whenPrepared() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.prepare(pool);

        tx.hardReset(pool);

        assertEquals(1, tx.getAttempt());
        assertActive(tx);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(Long.MAX_VALUE, tx.getRemainingTimeoutNs());
    }

    @Test
    public void whenHasPermanentListener() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        TransactionLifecycleListener listener = mock(TransactionLifecycleListener.class);
        tx.registerPermanent(listener);

        tx.hardReset(pool);

        assertEquals(1, tx.getAttempt());
        assertActive(tx);
        verify(listener).notify(tx, TransactionLifecycleEvent.PostAbort);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(Long.MAX_VALUE, tx.getRemainingTimeoutNs());
    }

    @Test
    public void whenHasNormalListener() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        TransactionLifecycleListener listener = mock(TransactionLifecycleListener.class);
        tx.register(listener);

        tx.hardReset(pool);

        assertActive(tx);
        assertEquals(1, tx.getAttempt());
        verify(listener).notify(tx, TransactionLifecycleEvent.PostAbort);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(Long.MAX_VALUE, tx.getRemainingTimeoutNs());
    }

    @Test
    public void whenAborted() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.abort(pool);

        tx.hardReset(pool);
        assertEquals(1, tx.getAttempt());
        assertActive(tx);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(Long.MAX_VALUE, tx.getRemainingTimeoutNs());
    }

    @Test
    public void whenCommitted() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.commit(pool);

        tx.hardReset(pool);
        assertEquals(1, tx.getAttempt());
        assertActive(tx);
        assertHasNoNormalListeners(tx);
        assertHasNoPermanentListeners(tx);
        assertEquals(Long.MAX_VALUE, tx.getRemainingTimeoutNs());
    }
}
