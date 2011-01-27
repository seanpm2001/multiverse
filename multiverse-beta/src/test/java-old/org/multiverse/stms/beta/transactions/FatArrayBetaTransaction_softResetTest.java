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
public class FatArrayBetaTransaction_softResetTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void whenMaximumNumberOfRetriesReached() {
        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setMaxRetries(3);

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);
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
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }

    @Test
    public void whenContainsUnlockedNonPermanentRead() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
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

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
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

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.openForWrite(ref, LOCKMODE_NONE);

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
    public void whendLockedWrites() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.openForWrite(ref, LOCKMODE_EXCLUSIVE);

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
    public void whenPreparedAndUnused() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.prepare();

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }

    @Test
    public void whenPreparedResourcesNeedRelease() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
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
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
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
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        TransactionListener listener = mock(TransactionListener.class);
        tx.register(listener);

        boolean result = tx.softReset();

        assertTrue(result);
        verify(listener).notify(tx, TransactionEvent.PostAbort);
        assertHasNoNormalListeners(tx);
    }

    @Test
    public void whenAborted() {
        BetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.abort();

        boolean result = tx.softReset();

        assertIsActive(tx);
        assertTrue(result);
    }

    @Test
    public void whenCommitted() {
        BetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commit();

        boolean result = tx.softReset();

        assertTrue(result);
        assertIsActive(tx);
    }
}
