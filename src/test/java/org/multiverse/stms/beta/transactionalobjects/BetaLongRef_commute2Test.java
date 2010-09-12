package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.Transaction;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.functions.IncLongFunction;
import org.multiverse.api.functions.LongFunction;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmUtils;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.api.ThreadLocalTransaction.getThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.assertSurplus;
import static org.multiverse.stms.beta.orec.OrecTestUtils.assertUnlocked;

public class BetaLongRef_commute2Test {

    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
    }

    @Test
    public void whenCommuteFunctionCausesProblems_thenNoProblemsSinceCommuteFunctionNotEvaluatedImmediately() {
        BetaLongRef ref = newLongRef(stm);

        LongFunction function = mock(LongFunction.class);
        RuntimeException ex = new RuntimeException();
        when(function.call(anyLong())).thenThrow(ex);

        BetaTransaction tx = stm.startDefaultTransaction();
        ref.commute(tx, function);

        assertHasCommutingFunctions((LongRefTranlocal) tx.get(ref), function);

        assertIsActive(tx);
        assertEquals(0, ref.atomicGet());
        assertUnlocked(ref);
        assertSurplus(0, ref);
        assertNull(getThreadLocalTransaction());
    }

    @Test
    @Ignore
    public void whenLocked() {

    }

    @Test
    public void whenSuccess() {
        BetaLongRef ref = newLongRef(stm, 10);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        BetaTransaction tx = stm.startDefaultTransaction();
        ref.commute(tx, function);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertTrue(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(0, commute.value);
        tx.commit();

        assertEquals(11, ref.get());
    }

    @Test
    public void whenNoChange() {
        BetaLongRef ref = newLongRef(stm, 10);
        LongRefTranlocal committed = ref.___unsafeLoad();

        LongFunction function = new IdentityLongFunction();
        BetaTransaction tx = stm.startDefaultTransaction();
        ref.commute(tx, function);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertTrue(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(0, commute.value);
        tx.commit();

        assertEquals(10, ref.get());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenNormalTransactionUsed() {
        BetaLongRef ref = newLongRef(stm, 10);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        Transaction tx = stm.startDefaultTransaction();
        ref.commute(tx, function);
        tx.commit();

        assertEquals(11, ref.get());
    }

    @Test
    public void whenAlreadyOpenedForRead() {
        BetaLongRef ref = newLongRef(stm, 10);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        BetaTransaction tx = stm.startDefaultTransaction();
        ref.get(tx);
        ref.commute(tx, function);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(11, commute.value);
        tx.commit();

        assertEquals(11, ref.get());
    }

    @Test
    public void whenAlreadyOpenedForConstruction() {
        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        BetaTransaction tx = stm.startDefaultTransaction();
        BetaLongRef ref = new BetaLongRef(tx);
        tx.openForConstruction(ref);
        ref.commute(tx, function);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(1, commute.value);
        tx.commit();

        assertEquals(1, ref.get());
    }

    @Test
    public void whenAlreadyOpenedForWrite() {
        BetaLongRef ref = newLongRef(stm, 10);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        BetaTransaction tx = stm.startDefaultTransaction();
        ref.set(tx, 11);
        ref.commute(tx, function);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertFalse(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(12, commute.value);
        tx.commit();

        assertEquals(12, ref.get());
    }

    @Test
    public void whenAlreadyCommuting() {
        BetaLongRef ref = newLongRef(stm, 10);

        LongFunction function1 = IncLongFunction.INSTANCE_INC_ONE;
        LongFunction function2 = IncLongFunction.INSTANCE_INC_ONE;
        BetaTransaction tx = stm.startDefaultTransaction();
        ref.commute(tx, function1);
        ref.commute(tx, function2);

        LongRefTranlocal commute = (LongRefTranlocal) tx.get(ref);
        assertTrue(commute.isCommuting);
        assertFalse(commute.isCommitted);
        assertEquals(0, commute.value);
        tx.commit();

        assertEquals(12, ref.get());
    }

    @Test
    public void whenNullFunction_thenNullPointerException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();

        BetaTransaction tx = stm.startDefaultTransaction();

        try {
            ref.commute(tx, null);
            fail();
        } catch (NullPointerException expected) {

        }

        assertIsAborted(tx);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenNullTransaction_thenNullPointerException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        LongFunction function = mock(LongFunction.class);

        try {
            ref.commute(null, function);
            fail();
        } catch (NullPointerException expected) {
        }

        verifyZeroInteractions(function);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenTransactionAborted_thenDeadTransactionException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        LongFunction function = mock(LongFunction.class);
        BetaTransaction tx = stm.startDefaultTransaction();
        tx.abort();

        try {
            ref.commute(tx, function);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsAborted(tx);
        verifyZeroInteractions(function);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenTransactionCommitted_thenDeadTransactionException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        LongFunction function = mock(LongFunction.class);
        BetaTransaction tx = stm.startDefaultTransaction();
        tx.commit();

        try {
            ref.commute(tx, function);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsCommitted(tx);
        verifyZeroInteractions(function);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenTransactionPrepared_thenPreparedTransactionException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        LongFunction function = mock(LongFunction.class);
        BetaTransaction tx = stm.startDefaultTransaction();
        tx.prepare();

        try {
            ref.commute(tx, function);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsAborted(tx);
        verifyZeroInteractions(function);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void fullExample() {
        BetaLongRef ref1 = newLongRef(stm, 10);
        BetaLongRef ref2 = newLongRef(stm, 10);

        BetaTransaction tx1 = stm.startDefaultTransaction();
        tx1.openForWrite(ref1, false).value++;
        tx1.commute(ref2, IncLongFunction.INSTANCE_INC_ONE);

        BetaTransaction tx2 = stm.startDefaultTransaction();
        tx2.openForWrite(ref2, false).value++;
        tx2.commit();

        tx1.commit();

        assertIsCommitted(tx1);
        assertEquals(11, ref1.___unsafeLoad().value);
        assertEquals(12, ref2.___unsafeLoad().value);
    }
}
