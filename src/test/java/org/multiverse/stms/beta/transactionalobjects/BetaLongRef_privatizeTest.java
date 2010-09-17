package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Transaction;
import org.multiverse.api.exceptions.NoTransactionFoundException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.exceptions.ReadConflict;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.ThreadLocalTransaction.*;
import static org.multiverse.stms.beta.BetaStmUtils.newLongRef;
import static org.multiverse.stms.beta.orec.OrecTestUtils.*;

public class BetaLongRef_privatizeTest {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        clearThreadLocalTransaction();
    }

    @Test
    public void whenNoTransactionAvailable_thenNoTransactionFoundException() {
        BetaLongRef ref = newLongRef(stm);

        LongRefTranlocal committed = ref.___unsafeLoad();
        try {
            ref.privatize();
            fail();
        } catch (NoTransactionFoundException expected) {
        }

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertNull(getThreadLocalTransaction());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenCommittedTransactionAvailable() {
        BetaLongRef ref = newLongRef(stm);
        Transaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.commit();

        LongRefTranlocal committed = ref.___unsafeLoad();
        try {
            ref.privatize();
            fail();
        } catch (NoTransactionFoundException expected) {
        }


        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenAbortedTransactionAvailable() {
        BetaLongRef ref = newLongRef(stm);
        Transaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.abort();

        LongRefTranlocal committed = ref.___unsafeLoad();
        try {
            ref.privatize();
            fail();
        } catch (NoTransactionFoundException expected) {
        }


        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsAborted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenPreparedTransactionAvailable_thenPreparedTransactionException() {
        BetaLongRef ref = newLongRef(stm);
        Transaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        tx.prepare();

        LongRefTranlocal committed = ref.___unsafeLoad();
        try {
            ref.privatize();
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsAborted(tx);
        assertSame(tx, getThreadLocalTransaction());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenNullTransaction_thenNullPointerException() {
        BetaLongRef ref = newLongRef(stm);
        LongRefTranlocal committed = ref.___unsafeLoad();

        try {
            ref.privatize(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertNull(getThreadLocalTransaction());
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenFreeAndNotReadBefore() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        ref.privatize();

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsActive(tx);

        tx.commit();

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
    }

    @Test
    public void whenFreeAndReadBefore() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        ref.get();
        ref.privatize();

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsActive(tx);

        tx.commit();

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
    }

    @Test
    public void whenFreeButReadConflict() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);
        ref.get();

        //conflicting write
        ref.atomicSet(100);
        LongRefTranlocal committed = ref.___unsafeLoad();

        try {
            ref.privatize();
            fail();
        } catch (ReadConflict expected) {

        }

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertNull(ref.___getLockOwner());
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsAborted(tx);
        assertSame(committed, ref.___unsafeLoad());
    }

    @Test
    public void whenReadonlyTransaction() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.createTransactionFactoryBuilder()
                .setReadonly(true)
                .build()
                .start();
        setThreadLocalTransaction(tx);

        ref.privatize();

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsActive(tx);

        tx.commit();

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
    }

    @Test
    public void whenAlreadyPrivatizedBySelf() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        ref.privatize();
        ref.privatize();

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsActive(tx);

        tx.commit();

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
    }

    @Test
    public void whenAlreadyEnsuredBySelf() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        ref.ensure();
        ref.privatize();

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(tx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsActive(tx);

        tx.commit();

        assertSurplus(0, ref);
        assertUpdateBiased(ref);
        assertHasNoCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertNull(ref.___getLockOwner());
        assertIsCommitted(tx);
    }

    @Test
    public void whenAlreadyPrivatizedByOther_thenReadConflict() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.privatize(otherTx);

        try {
            ref.privatize();
            fail();
        } catch (ReadConflict expected) {

        }

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(otherTx, ref.___getLockOwner());
        assertHasCommitLock(ref);
        assertHasNoUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsAborted(tx);
    }

    @Test
    public void whenAlreadyEnsuredByOther() {
        BetaLongRef ref = newLongRef(stm);

        BetaTransaction tx = stm.startDefaultTransaction();
        setThreadLocalTransaction(tx);

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.ensure(otherTx);

        try {
            ref.privatize();
            fail();
        } catch (ReadConflict expected) {

        }

        assertSurplus(1, ref);
        assertUpdateBiased(ref);
        assertSame(otherTx, ref.___getLockOwner());
        assertHasNoCommitLock(ref);
        assertHasUpdateLock(ref);
        assertSame(tx, getThreadLocalTransaction());
        assertIsAborted(tx);
    }
}
