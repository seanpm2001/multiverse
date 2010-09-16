package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.functions.IncLongFunction;
import org.multiverse.api.functions.LongFunction;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmUtils;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.multiverse.TestUtils.assertIsActive;
import static org.multiverse.api.ThreadLocalTransaction.*;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.assertHasNoCommitLock;
import static org.multiverse.stms.beta.orec.OrecTestUtils.assertSurplus;

public class BetaLongRef_atomicAlterAndGetTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
    }

    @Test
    public void whenFunctionCausesException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        LongFunction function = mock(LongFunction.class);
        RuntimeException ex = new RuntimeException();
        when(function.call(anyLong())).thenThrow(ex);

        try {
            ref.atomicAlterAndGet(function);
            fail();
        } catch (RuntimeException found) {
            assertSame(ex, found);
        }

        assertEquals(0, ref.atomicGet());
        assertHasNoCommitLock(ref);
        assertSurplus(0, ref);
        assertNull(getThreadLocalTransaction());
    }

    @Test
    public void whenNullFunction_thenNullPointerException() {
        BetaLongRef ref = newLongRef(stm, 5);
        LongRefTranlocal committed = ref.___unsafeLoad();

        try {
            ref.atomicAlterAndGet(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertHasNoCommitLock(ref);
        assertSurplus(0, ref);
        assertEquals(5, ref.atomicGet());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenSuccess() {
        BetaLongRef ref = newLongRef(stm, 5);
        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;

        long result = ref.atomicAlterAndGet(function);

        assertEquals(6, result);
        assertHasNoCommitLock(ref);
        assertSurplus(0, ref);
        assertNull(ref.___getLockOwner());
        assertEquals(6, ref.atomicGet());
    }

    @Test
    @Ignore
    public void whenListenersAvailable() {

    }

    @Test
    public void whenNoChange() {
        BetaLongRef ref = newLongRef(stm, 5);
        LongRefTranlocal committed = ref.___unsafeLoad();

        LongFunction function = new IdentityLongFunction();

        long result = ref.atomicAlterAndGet(function);

        assertEquals(5, result);
        assertHasNoCommitLock(ref);
        assertSurplus(0, ref);
        assertNull(ref.___getLockOwner());
        assertEquals(5, ref.atomicGet());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenActiveTransactionAvailable_thenIgnored() {
        BetaLongRef ref = newLongRef(stm, 5);
        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        ref.set(tx, 100);

        LongFunction function = IncLongFunction.INSTANCE_INC_ONE;

        long result = ref.atomicAlterAndGet(function);

        assertEquals(6, result);
        assertHasNoCommitLock(ref);
        assertSurplus(1, ref);
        assertNull(ref.___getLockOwner());
        assertEquals(6, ref.atomicGet());
        assertIsActive(tx);
        assertSame(tx,getThreadLocalTransaction());
    }

    @Test
    @Ignore
    public void whenLocked() {

    }


}
