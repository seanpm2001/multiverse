package org.multiverse.stms.beta.transactions;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.lifecycle.TransactionEvent;
import org.multiverse.api.lifecycle.TransactionListener;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRefTranlocal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.beta.BetaStmTestUtils.*;
import static org.multiverse.stms.beta.transactionalobjects.OrecTestUtils.*;

/**
 * @author Peter Veentjer
 */
public class FatArrayTreeBetaTransaction_softResetTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void whenMaximumNumberOfRetriesReached() {
        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setMaxRetries(3);

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(config);
        assertTrue(tx.softReset());
        assertEquals(2, tx.getAttempt());
        assertIsActive(tx);

        assertTrue(tx.softReset());
        assertEquals(3, tx.getAttempt());
        assertIsActive(tx);

        assertFalse(tx.softReset());
        assertEquals(3, tx.getAttempt());
        assertIsAborted(tx);
    }

    @Test
    public void whenUnused() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }

    @Test
    public void whenContainsUnlockedNonPermanentRead() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForRead(ref, LOCKMODE_NONE);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertReadonlyCount(0, ref);
        assertUpdateBiased(ref);
        assertSurplus(0, ref);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, version, 0);
        assertHasNoUpdates(tx);
    }

    @Test
    public void whenContainsUnlockedPermanent() {
        BetaLongRef ref = newReadBiasedLongRef(stm);
        long version = ref.getVersion();

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForRead(ref, LOCKMODE_NONE);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertReadonlyCount(0, ref);
        assertReadBiased(ref);
        assertSurplus(1, ref);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, version, 0);
        assertHasNoUpdates(tx);
    }

    @Test
    public void whenNormalUpdate() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        BetaLongRefTranlocal write = tx.openForWrite(ref, LOCKMODE_NONE);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertReadonlyCount(0, ref);
        assertUpdateBiased(ref);
        assertSurplus(0, ref);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, version, 0);
        assertFalse(write.hasDepartObligation());
        assertFalse(write.isReadonly());
        assertHasNoUpdates(tx);
    }

    @Test
    public void whendLockedWrites() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        BetaLongRefTranlocal write = tx.openForWrite(ref, LOCKMODE_EXCLUSIVE);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertReadonlyCount(0, ref);
        assertUpdateBiased(ref);
        assertSurplus(0, ref);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, version, 0);
        assertFalse(write.hasDepartObligation());
        assertFalse(write.isReadonly());
        assertHasNoUpdates(tx);
    }

    @Test
    public void whenPreparedAndUnused() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.prepare();

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }

    @Test
    public void whenPreparedResourcesNeedRelease() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.openForWrite(ref, LOCKMODE_NONE);
        tx.prepare();

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertReadonlyCount(0, ref);
        assertUpdateBiased(ref);
        assertSurplus(0, ref);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, version, 0);
        assertHasNoUpdates(tx);
    }

    @Test
    public void whenContainsConstructed() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        BetaLongRef ref = new BetaLongRef(tx);
        BetaLongRefTranlocal constructed = tx.openForConstruction(ref);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
        assertFalse(constructed.isReadonly());
        assertFalse(constructed.hasDepartObligation());
        assertHasCommitLock(ref);
        assertSurplus(1, ref);
        assertHasNoUpdates(tx);
    }

    @Test
    public void whenHasNormalListener_thenTheyAreRemoved() {
        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        TransactionListener listener = mock(TransactionListener.class);
        tx.register(listener);

        boolean result = tx.softReset();

        assertTrue(result);
        verify(listener).notify(tx, TransactionEvent.PostAbort);
        assertHasNoNormalListeners(tx);
    }

    @Test
    public void whenAborted() {
        BetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.abort();

        boolean result = tx.softReset();

        assertIsActive(tx);
        assertTrue(result);
    }

    @Test
    public void whenCommitted() {
        BetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        tx.commit();

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }
}