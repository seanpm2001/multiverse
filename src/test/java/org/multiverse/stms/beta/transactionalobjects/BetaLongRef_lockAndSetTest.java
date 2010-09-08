package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.exceptions.ReadConflict;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactions.FatMonoBetaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.stms.beta.BetaStmUtils.createLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.assertLocked;

public class BetaLongRef_lockAndSetTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void whenUnlocked() {
        BetaLongRef ref = createLongRef(stm, 10);

        FatMonoBetaTransaction tx = new FatMonoBetaTransaction(stm);
        ref.lockAndSet(tx, 20);
        assertSame(tx, ref.___getLockOwner());
        assertLocked(ref);

        tx.commit();

        assertNull(ref.___getLockOwner());
        assertEquals(20, ref.___unsafeLoad().value);
    }

    @Test
    public void whenAlreadyLockedBySelf() {
        BetaLongRef ref = createLongRef(stm, 10);

        FatMonoBetaTransaction tx = new FatMonoBetaTransaction(stm);
        ref.lockAndSet(tx, 10);
        ref.lockAndSet(tx, 20);
        assertSame(tx, ref.___getLockOwner());
        assertLocked(ref);

        tx.commit();

        assertNull(ref.___getLockOwner());
        assertEquals(20, ref.___unsafeLoad().value);
    }

    @Test
    public void whenAlreadyLockedByOther() {
        BetaLongRef ref = createLongRef(stm, 10);

        FatMonoBetaTransaction tx = new FatMonoBetaTransaction(stm);
        FatMonoBetaTransaction otherTx = new FatMonoBetaTransaction(stm);
        ref.lockAndGet(otherTx);
        try {
            ref.lockAndSet(tx, 20);
            fail();
        } catch (ReadConflict e) {

        }

        assertSame(otherTx, ref.___getLockOwner());
        assertLocked(ref);
    }
}