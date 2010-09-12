package org.multiverse.stms.beta.integrationtest.pessimistic;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.PessimisticLockLevel;
import org.multiverse.api.exceptions.ReadConflict;
import org.multiverse.api.exceptions.WriteConflict;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmUtils;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;

public class PessimisticTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void constructedObjectAutomaticallyIsLocked() {
        BetaTransaction tx = stm.startDefaultTransaction();
        BetaLongRef ref = new BetaLongRef(tx);
        tx.openForConstruction(ref);

        assertSame(tx, ref.___getLockOwner());
    }

    @Test
    public void whenValueAlreadyRead_thenLockDoesntMatter() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForRead(ref, false);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        otherTx.openForRead(ref, true);

        long value = tx.openForRead(ref, false).value;

        assertEquals(0, value);
        assertIsActive(tx);
        assertEquals(0, ref.___unsafeLoad().value);
    }

    @Test
    public void whenCantComitedWhenLocked() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForWrite(ref, false).value++;

        BetaTransaction otherTx = stm.startDefaultTransaction();
        otherTx.openForRead(ref, true);

        try {
            tx.commit();
            fail();
        } catch (WriteConflict expected) {
        }

        assertIsAborted(tx);
        assertEquals(0, ref.___unsafeLoad().value);
    }

    @Test
    public void pessimisticWriteLevelOverridesOpenForWriteButNotLocked() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.createTransactionFactoryBuilder()
                .setPessimisticLockLevel(PessimisticLockLevel.Write)
                .build()
                .start();

        tx.openForWrite(ref, false);

        assertSame(tx, ref.___getLockOwner());
    }

    @Test
    public void whenPessimisticWriteLevelUsed_readIsNotLocked() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.createTransactionFactoryBuilder()
                .setPessimisticLockLevel(PessimisticLockLevel.Write)
                .build()
                .start();

        tx.openForRead(ref, false);

        assertNull(ref.___getLockOwner());
    }

    @Test
    public void pessimisticReadLevelOverridesOpenForReadButNotLocked() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.createTransactionFactoryBuilder()
                .setPessimisticLockLevel(PessimisticLockLevel.Read)
                .build()
                .start();

        tx.openForRead(ref, false);

        assertSame(tx, ref.___getLockOwner());
    }

    @Test
    public void lockAcquiredWhileDoingOpenForReadIsReentrant() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForRead(ref, true);
        tx.openForRead(ref, true);

        assertSame(tx, ref.___getLockOwner());
    }

    @Test
    public void lockAcquiredWhileDoingOpenForWriteIsReentrant() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForWrite(ref, true);
        tx.openForWrite(ref, true);

        assertSame(tx, ref.___getLockOwner());
    }

    @Test
    public void whenLockCantBeAcquired_thenTransactionAborted() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        otherTx.openForRead(ref, true);

        BetaTransaction tx = stm.startDefaultTransaction();
        try {
            tx.openForRead(ref, true);
            fail();
        } catch (ReadConflict e) {

        }

        assertIsAborted(tx);
        assertSame(otherTx, ref.___getLockOwner());
    }

    @Test
    public void whenTransactionAborted_thenLockReleased() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForRead(ref, true);
        tx.abort();

        assertIsAborted(tx);
        assertNull(ref.___getLockOwner());
    }

    @Test
    public void whenTransactionCommitted_thenLockReleased() {
        BetaLongRef ref = BetaStmUtils.newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        tx.openForRead(ref, true);
        tx.commit();

        assertIsCommitted(tx);
        assertNull(ref.___getLockOwner());
    }
}
