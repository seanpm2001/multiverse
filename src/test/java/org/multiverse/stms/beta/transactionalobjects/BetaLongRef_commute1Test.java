package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.functions.IncLongFunction;
import org.multiverse.api.functions.LongFunction;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.ThreadLocalTransaction.*;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.*;

public class BetaLongRef_commute1Test {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
    }

    @Test
    public void whenActiveTransactionAvailable() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        LongFunction function = new IncLongFunction(1);
        ref.commute(function);

        LongRefTranlocal commuting = (LongRefTranlocal) tx.get(ref);
        assertNotNull(commuting);
        assertTrue(commuting.isCommuting);
        assertFalse(commuting.isCommitted);
        assertSurplus(0, ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertEquals(0, commuting.value);
        assertIsActive(tx);
        assertSame(tx, getThreadLocalTransaction());
        tx.commit();

        assertEquals(1, ref.get());
        assertIsCommitted(tx);
        assertSurplus(0, ref);
        assertHasNoCommitLock(ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenActiveTransactionAvailableAndNoChange() {
        BetaLongRef ref = newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        LongFunction function = new IdentityLongFunction();
        ref.commute(function);

        LongRefTranlocal commuting = (LongRefTranlocal) tx.get(ref);
        assertNotNull(commuting);
        assertTrue(commuting.isCommuting);
        assertFalse(commuting.isCommitted);
        assertSurplus(0, ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertEquals(0, commuting.value);
        assertIsActive(tx);
        assertSame(tx, getThreadLocalTransaction());
        tx.commit();

        assertEquals(0, ref.get());
        assertSame(committed, ref.___unsafeLoad());
        assertIsCommitted(tx);
        assertSurplus(0, ref);
        assertHasNoCommitLock(ref);
        assertUpdateBiased(ref);
    }

    @Test
    public void whenActiveTransactionAvailableAndNullFunction_thenNullPointerException() {
        BetaLongRef ref = newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();
        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        try {
            ref.commute(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertSame(committed, ref.___unsafeLoad());
        assertIsAborted(tx);
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertEquals(0, ref.get());
    }

    @Test
    public void whenNoTransactionAvailable_thenExecutedAtomically() {
        BetaLongRef ref = newLongRef(stm, 2);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        ref.commute(function);

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertEquals(3, ref.atomicGet());
    }

    @Test
    public void whenCommittedTransactionAvailable_thenExecuteAtomically() {
        BetaLongRef ref = newLongRef(stm, 2);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.commit();

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        ref.commute(function);

        assertIsCommitted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertEquals(3, ref.atomicGet());
    }

    @Test
    public void whenAbortedTransactionAvailable_thenExecuteAtomically() {
        BetaLongRef ref = newLongRef(stm, 2);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.abort();

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        ref.commute(function);

        assertIsAborted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertEquals(3, ref.atomicGet());
    }

    @Test
    public void whenPreparedTransactionAvailable_thenPreparedTransactionException() {
        BetaLongRef ref = newLongRef(stm, 2);
        LongRefTranlocal committed = ref.___unsafeLoad();

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.prepare();

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;
        try {
            ref.commute(function);
            fail();
        } catch (PreparedTransactionException expected) {

        }

        assertIsAborted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertNull(ref.___getLockOwner());
        assertSame(committed, ref.___unsafeLoad());
        assertEquals(2, ref.atomicGet());
    }

}
