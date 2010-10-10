package org.multiverse.stms.beta.transactions;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.PessimisticLockLevel;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.exceptions.ReadonlyException;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmConstants;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactionalobjects.LongRefTranlocal;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.beta.BetaStmTestUtils.assertVersionAndValue;
import static org.multiverse.stms.beta.BetaStmTestUtils.newLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.*;

public abstract class BetaTransaction_openForConstructionTest implements BetaStmConstants {

    protected BetaStm stm;

    public abstract BetaTransaction newTransaction();

    public abstract BetaTransaction newTransaction(BetaTransactionConfiguration config);

    protected abstract boolean hasLocalConflictCounter();

    protected abstract int getMaxTransactionCapacity();

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void whenNullRef_thenNullPointerException() {
        BetaTransaction tx = newTransaction();

        try {
            tx.openForConstruction((BetaLongRef) null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertIsAborted(tx);
    }

    @Test
    public void whenSuccess() {
        BetaTransaction tx = newTransaction();
        BetaLongRef ref = new BetaLongRef(tx);
        long version = ref.getVersion();
        LongRefTranlocal write = tx.openForConstruction(ref);

        assertNotNull(write);
        assertEquals(0, write.value);
        assertEquals(version, write.version);
        assertSame(ref, write.owner);
        assertFalse(write.isCommitted);
        assertFalse(write.hasDepartObligation);
        assertTrue(write.isDirty);

        assertIsActive(tx);
        assertAttached(tx, write);
        assertSame(tx, ref.___getLockOwner());

        assertHasCommitLock(ref);
        assertSurplus(1, ref);
    }

    @Test
    public void whenAlreadyOpenedForConstruction_thenNoProblem() {
        BetaTransaction tx = newTransaction();
        BetaLongRef ref = new BetaLongRef(tx);
        LongRefTranlocal construction1 = tx.openForConstruction(ref);
        LongRefTranlocal construction2 = tx.openForConstruction(ref);

        assertNotNull(construction1);
        assertSame(construction1, construction2);
        assertEquals(0, construction1.value);
        assertSame(ref, construction1.owner);
        assertFalse(construction1.isCommitted);
        assertFalse(construction1.hasDepartObligation);

        assertIsActive(tx);
        assertAttached(tx, construction1);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertSurplus(1, ref);
        assertTrue(construction1.isDirty);
    }

    @Test
    public void whenAlreadyCommitted_thenIllegalArgumentException() {
        BetaLongRef ref = newLongRef(stm, 100);
        long version = ref.getVersion();

        BetaTransaction tx = newTransaction();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, version, 100);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertReadonlyCount(0, ref);
    }

    @Test
    public void whenAlreadyOpenedForReadingAndPrivatized_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = newTransaction();
        ref.get(tx);
        ref.privatize(tx);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertNull(ref.___getLockOwner());
        assertHasNoUpdateLock(ref);
        assertHasNoCommitLock(ref);
    }

    @Test
    public void whenAlreadyOpenedForReadingAndEnsured_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = newTransaction();
        ref.get(tx);
        ref.ensure(tx);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertNull(ref.___getLockOwner());
        assertHasNoUpdateLock(ref);
        assertHasNoCommitLock(ref);
    }

    @Test
    public void whenAlreadyPrivatizedByOther_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = newTransaction();
        ref.set(tx, initialValue + 1);
        ref.privatize(tx);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertNull(ref.___getLockOwner());
        assertHasNoUpdateLock(ref);
        assertHasNoCommitLock(ref);
    }

    @Test
    public void whenAlreadyEnsuredByOther_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.ensure(otherTx);

        BetaTransaction tx = newTransaction();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSame(otherTx, ref.___getLockOwner());
        assertHasUpdateLock(ref);
        assertHasNoCommitLock(ref);
    }

    @Test
    public void whenAlreadyOpenedForWritingAndPrivatized_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.privatize(otherTx);

        BetaTransaction tx = newTransaction();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSame(otherTx, ref.___getLockOwner());
        assertHasNoUpdateLock(ref);
        assertHasCommitLock(ref);
    }

    @Test
    public void whenAlreadyOpenedForWritingAndEnsured_thenIllegalArgumentException() {
        long initialValue = 100;
        BetaLongRef ref = newLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = newTransaction();
        ref.set(tx, initialValue + 1);
        ref.ensure(tx);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertNull(ref.___getLockOwner());
        assertHasNoUpdateLock(ref);
        assertHasNoCommitLock(ref);
    }

    @Test
    public void whenAlreadyOpenedForReading_thenIllegalArgumentException() {
        BetaLongRef ref = newLongRef(stm, 100);
        long version = ref.getVersion();

        BetaTransaction tx = newTransaction();
        tx.openForRead(ref, LOCKMODE_NONE);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, version, 100);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertReadonlyCount(0, ref);
    }

    @Test
    public void whenAlreadyOpenedForWrite_thenIllegalArgumentException() {
        BetaLongRef ref = newLongRef(stm, 100);
        long version = ref.getVersion();

        BetaTransaction tx = newTransaction();
        tx.openForWrite(ref, LOCKMODE_NONE);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        assertIsAborted(tx);
        assertVersionAndValue(ref, version, 100);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertReadonlyCount(0, ref);
    }

    // ================= readonly =========================

    @Test
    public void whenReadonly_thenReadonlyException() {
        BetaLongRef ref = newLongRef(stm);
        long version = ref.getVersion();

        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setReadonly(true);

        BetaTransaction tx = newTransaction(config);

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (ReadonlyException expected) {
        }

        assertIsAborted(tx);
        assertHasNoCommitLock(ref);
        assertVersionAndValue(ref, version, 0);
        assertNull(ref.___getLockOwner());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertReadonlyCount(0, ref);
    }

    // ================ isolation level ============================

    @Test
    public void pessimisticLockLevel_whenPessimisticThenNoConflictDetectionNeeded() {
        assumeTrue(getMaxTransactionCapacity() > 2);
        assumeTrue(hasLocalConflictCounter());

        BetaLongRef ref1 = newLongRef(stm);

        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setPessimisticLockLevel(PessimisticLockLevel.PrivatizeReads)
                .init();

        BetaTransaction tx = newTransaction(config);
        tx.openForRead(ref1, LOCKMODE_NONE);

        long oldLocalConflictCount = tx.getLocalConflictCounter().get();

        stm.getGlobalConflictCounter().signalConflict(newLongRef(stm));
        BetaLongRef ref2 = new BetaLongRef(tx);
        tx.openForConstruction(ref2);

        assertEquals(oldLocalConflictCount, tx.getLocalConflictCounter().get());
    }

    // ============ consistency ============================

    @Test
    public void consistency_conflictCounterIsNotReset() {
        assumeTrue(hasLocalConflictCounter());

        BetaTransaction tx = newTransaction();
        long oldConflictCount = tx.getLocalConflictCounter().get();
        BetaLongRef ref = new BetaLongRef(tx);

        stm.getGlobalConflictCounter().signalConflict(newLongRef(stm));
        tx.openForConstruction(ref);

        assertEquals(oldConflictCount, tx.getLocalConflictCounter().get());
        assertIsActive(tx);
    }

    @Test
    @Ignore
    public void consistency_whenThereIsConflict_thenItIsNotTriggered() {

    }

    // ============================ state ====================

    @Test
    public void state_whenPrepared_thenPreparedTransactionException() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = newTransaction();
        tx.prepare();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsAborted(tx);
    }

    @Test
    public void state_whenAborted_thenDeadTransactionException() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = newTransaction();
        tx.abort();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsAborted(tx);
    }

    @Test
    public void state_whenCommitted_thenDeadTransactionException() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = newTransaction();
        tx.commit();

        try {
            tx.openForConstruction(ref);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsCommitted(tx);
    }
}