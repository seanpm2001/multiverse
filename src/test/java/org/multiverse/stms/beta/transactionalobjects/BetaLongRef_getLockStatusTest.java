package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.LockStatus;
import org.multiverse.api.Transaction;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmUtils;
import org.multiverse.stms.beta.transactions.LeanMonoBetaTransaction;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

public class BetaLongRef_getLockStatusTest {

    private BetaStm stm;
    private BetaObjectPool pool;

    @Before
    public void setUp() {
        stm = new BetaStm();
        pool = new BetaObjectPool();
        clearThreadLocalTransaction();
    }

    @Test(expected = NullPointerException.class)
    public void whenNull_thenNullPointerException() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);
        ref.getLockStatus(null);
    }

    @Test
    public void whenFree() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        Transaction tx = mock(Transaction.class);
        LockStatus result = ref.getLockStatus(tx);
        assertEquals(LockStatus.Free, result);
    }

    @Test
    public void whenLockedByOther() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        LeanMonoBetaTransaction otherTx = new LeanMonoBetaTransaction(stm);
        otherTx.openForRead(ref, true);

        Transaction tx = mock(Transaction.class);

        LockStatus result = ref.getLockStatus(tx);
        assertEquals(LockStatus.LockedByOther, result);
    }

    @Test
    public void whenLockedBySelf() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        LeanMonoBetaTransaction tx = new LeanMonoBetaTransaction(stm);
        tx.openForRead(ref, true);


        LockStatus result = ref.getLockStatus(tx);
        assertEquals(LockStatus.LockedBySelf, result);
    }
}
