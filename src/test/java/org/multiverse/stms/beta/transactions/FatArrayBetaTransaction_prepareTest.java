package org.multiverse.stms.beta.transactions;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.WriteConflict;
import org.multiverse.functions.IncLongFunction;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.refs.LongRef;
import org.multiverse.stms.beta.refs.LongRefTranlocal;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.beta.BetaStmUtils.createLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.*;

/**
 * @author Peter Veentjer
 */
public class FatArrayBetaTransaction_prepareTest {
    private BetaStm stm;
    private BetaObjectPool pool;

    @Before
    public void setUp() {
        stm = new BetaStm();
        pool = new BetaObjectPool();
    }

    @Test
    @Ignore
    public void whenUnstarted() {

    }

    @Test
    public void whenUnused() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);

        tx.prepare();

        assertPrepared(tx);
    }

    @Test
    public void whenReadIsConflictedByWrite() {
        LongRef ref = createLongRef(stm);

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal read = tx.openForRead(ref, false, pool);

        FatArrayBetaTransaction otherTx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal conflictingWrite = otherTx.openForWrite(ref, false, pool);
        conflictingWrite.value++;
        otherTx.commit();

        tx.prepare(pool);

        assertPrepared(tx);
        assertCommitted(otherTx);
        assertUnlocked(ref);
        assertNull(ref.getLockOwner());
        assertSame(conflictingWrite, ref.unsafeLoad());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenReadIsConflictedByLock_thenPrepareSuccess() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal read = tx.openForRead(ref, false, pool);

        FatArrayBetaTransaction otherTx = new FatArrayBetaTransaction(stm);
        otherTx.openForRead(ref, true, pool);

        tx.prepare(pool);

        assertPrepared(tx);
        assertActive(otherTx);
        assertLocked(ref);
        assertSame(otherTx, ref.getLockOwner());
        assertSame(committed, ref.unsafeLoad());
        assertSurplus(2, ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenUpdate() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;
        tx.prepare(pool);

        assertPrepared(tx);

        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSame(committed, ref.unsafeLoad());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertFalse(write.isCommitted);
    }

    @Test
    public void whenUpdateAlreadyLockedBySelf() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write = tx.openForWrite(ref, true, pool);
        tx.prepare(pool);

        assertPrepared(tx);

        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSame(committed, ref.unsafeLoad());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertFalse(write.isCommitted);
    }

    @Test
    public void whenUpdateAndLockedByOther_thenWriteConflict() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;

        FatArrayBetaTransaction otherTx = new FatArrayBetaTransaction(stm);
        otherTx.openForRead(ref, true, pool);

        try {
            tx.prepare(pool);
            fail();
        } catch (WriteConflict expected) {
        }

        assertAborted(tx);
        assertActive(otherTx);

        assertLocked(ref);
        assertSame(otherTx, ref.getLockOwner());
        assertSame(committed, ref.unsafeLoad());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertFalse(write.isCommitted);
    }

    @Test
    public void whenOnlyConstructed() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRef ref = new LongRef(tx);
        LongRefTranlocal constructed = tx.openForConstruction(ref, pool);
        tx.prepare();

        assertFalse(constructed.isCommitted);
        assertFalse(constructed.isPermanent);
        assertPrepared(tx);
        assertSame(tx, ref.getLockOwner());
        assertLocked(ref);
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertNull(ref.unsafeLoad());
    }

    @Test
    public void whenConflictingWrite() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;

        FatArrayBetaTransaction otherTx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal conflictingWrite = otherTx.openForWrite(ref, false, pool);
        conflictingWrite.value++;
        otherTx.commit();

        try {
            tx.prepare(pool);
            fail();
        } catch (WriteConflict expected) {
        }

        assertAborted(tx);
        assertCommitted(otherTx);

        assertUnlocked(ref);
        assertNull(ref.getLockOwner());
        assertSame(conflictingWrite, ref.unsafeLoad());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertFalse(write.isCommitted);
    }

    @Test
    public void whenMultipleItems() {
        LongRef ref1 = createLongRef(stm);
        LongRef ref2 = createLongRef(stm);
        LongRef ref3 = createLongRef(stm);

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write1 = tx.openForWrite(ref1, false, pool);
        write1.value++;
        LongRefTranlocal write2 = tx.openForWrite(ref2, false, pool);
        write2.value++;
        LongRefTranlocal write3 = tx.openForWrite(ref3, false, pool);
        write3.value++;
        tx.prepare();

        assertSame(tx, ref1.getLockOwner());
        assertSame(tx, ref2.getLockOwner());
        assertSame(tx, ref3.getLockOwner());
        assertLocked(ref1);
        assertLocked(ref2);
        assertLocked(ref3);
        assertSurplus(1, ref1);
        assertSurplus(1, ref2);
        assertSurplus(1, ref3);
        assertUpdateBiased(ref1);
        assertUpdateBiased(ref2);
        assertUpdateBiased(ref3);
    }

    @Test
    @Ignore
    public void whenOneOfTheItemsFails() {

    }

    @Test
    public void whenAbortOnly() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.setAbortOnly();

        try {
            tx.prepare();
            fail();
        } catch (WriteConflict conflict) {
        }

        assertAborted(tx);
    }

    @Test
    public void whenReferenceHasMultipleCommutes() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);
        tx.prepare();

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);

        assertNotNull(commute);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertSame(ref, commute.owner);
        assertSame(committed, commute.read);
        assertEquals(3, commute.value);
        assertTrue(commute.isDirty);
        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
    }

    @Test
    @Ignore
    public void whenMultipleReferencesHaveCommute() {
        LongRef ref1 = createLongRef(stm, 10);
        LongRef ref2 = createLongRef(stm, 20);
        LongRef ref3 = createLongRef(stm, 30);

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commute(ref1, pool, IncLongFunction.INSTANCE);
        tx.commute(ref2, pool, IncLongFunction.INSTANCE);
        tx.commute(ref3, pool, IncLongFunction.INSTANCE);
        tx.prepare();

        LongRefTranlocal commute1 = (LongRefTranlocal) tx.get(ref1);
        LongRefTranlocal commute2 = (LongRefTranlocal) tx.get(ref1);
        LongRefTranlocal commute3 = (LongRefTranlocal) tx.get(ref1);

        assertNotNull(commute1);
        assertEquals(11, commute1.value);
        assertNotNull(commute1);
        assertEquals(21, commute2.value);
        assertNotNull(commute1);
        assertEquals(31, commute3.value);
    }

    @Test
    public void whenHasCommuteAndNoDirtyCheck() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setDirtyCheckEnabled(false);
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);
        tx.prepare();

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);

        assertNotNull(commute);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertSame(ref, commute.owner);
        assertSame(committed, commute.read);
        assertEquals(1, commute.value);
        assertTrue(commute.isDirty);
        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenHasCommuteAndDirtyCheck() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm)
                .setDirtyCheckEnabled(true);
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);
        tx.prepare();

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);

        assertNotNull(commute);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertSame(ref, commute.owner);
        assertSame(committed, commute.read);
        assertEquals(1, commute.value);
        assertTrue(commute.isDirty);
        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenHasCommuteButLockedByOtherTransaction_thenWriteConflict() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        otherTx.openForRead(ref, true, pool);

        try {
            tx.prepare();
            fail();
        } catch (WriteConflict expected) {
        }

        assertAborted(tx);

        assertLocked(ref);
        assertSame(otherTx, ref.getLockOwner());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(committed, ref.unsafeLoad());
    }

    @Test
    public void whenHasCommuteThatConflicts() {
        LongRef ref = createLongRef(stm);

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commute(ref, pool, IncLongFunction.INSTANCE);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        otherTx.openForWrite(ref, false, pool).value++;
        otherTx.commit();
        LongRefTranlocal committed = ref.unsafeLoad();

        tx.prepare();

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);

        assertNotNull(commute);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertSame(ref, commute.owner);
        assertSame(committed, commute.read);
        assertEquals(2, commute.value);
        assertTrue(commute.isDirty);
        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
    }


    @Test
    public void whenPreparedAlreadyPrepared() {
        LongRef ref = createLongRef(stm);
        LongRefTranlocal committed = ref.unsafeLoad();

        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        LongRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;
        tx.prepare(pool);

        tx.prepare(pool);
        assertPrepared(tx);

        assertLocked(ref);
        assertSame(tx, ref.getLockOwner());
        assertSame(committed, ref.unsafeLoad());
        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertFalse(write.isCommitted);
    }

    @Test
    public void whenCommitted_thenDeadTransactionException() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.commit(pool);

        try {
            tx.prepare(pool);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertCommitted(tx);
    }

    @Test
    public void whenAborted_thenDeadTransactionException() {
        FatArrayBetaTransaction tx = new FatArrayBetaTransaction(stm);
        tx.abort(pool);

        try {
            tx.prepare(pool);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertAborted(tx);
    }
}
