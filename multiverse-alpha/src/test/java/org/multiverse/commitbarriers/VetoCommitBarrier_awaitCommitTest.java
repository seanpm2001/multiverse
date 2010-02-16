package org.multiverse.commitbarriers;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.annotations.TransactionalMethod;
import org.multiverse.api.Stm;
import org.multiverse.api.Transaction;
import org.multiverse.api.TransactionFactory;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.TooManyRetriesException;
import org.multiverse.transactional.primitives.TransactionalInteger;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.api.ThreadLocalTransaction.getThreadLocalTransaction;

public class VetoCommitBarrier_awaitCommitTest {
    private Stm stm;
    private TransactionFactory txFactory;

    @Before
    public void setUp() {
        stm = getGlobalStmInstance();
        txFactory = stm.getTransactionFactoryBuilder().build();
        clearThreadLocalTransaction();
        clearCurrentThreadInterruptedStatus();
    }

    @After
    public void tearDown() {
        clearCurrentThreadInterruptedStatus();
    }


    @Test
    public void whenTransactionNull_thenNullPointerException() throws InterruptedException {
        VetoCommitBarrier barrier = new VetoCommitBarrier();

        try {
            barrier.awaitCommit(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertTrue(barrier.isClosed());
        assertEquals(0, barrier.getNumberWaiting());
    }

    @Test
    public void whenTransactionPreparable_thenAdded() {
        VetoCommitBarrier barrier = new VetoCommitBarrier();
        TransactionalInteger ref = new TransactionalInteger();
        IncThread thread = new IncThread(ref, barrier);
        thread.start();

        sleepMs(500);
        assertAlive(thread);
        assertTrue(barrier.isClosed());
        assertEquals(1, barrier.getNumberWaiting());
    }

    @Test
    @Ignore
    public void whenTransactionPrepared() {

    }

    @Test
    public void whenPrepareFails() throws InterruptedException {
        final VetoCommitBarrier group = new VetoCommitBarrier();
        final TransactionalInteger ref = new TransactionalInteger();

        FailToPrepareThread thread = new FailToPrepareThread(group, ref);
        thread.start();

        sleepMs(500);
        ref.inc();

        thread.join();
        thread.assertFailedWithException(TooManyRetriesException.class);
        assertEquals(0, group.getNumberWaiting());
    }

    class FailToPrepareThread extends TestThread {
        final VetoCommitBarrier group;
        final TransactionalInteger ref;

        FailToPrepareThread(VetoCommitBarrier group, TransactionalInteger ref) {
            this.group = group;
            this.ref = ref;
            setPrintStackTrace(false);
        }

        @Override
        @TransactionalMethod(maxRetryCount = 0)
        public void doRun() throws Exception {
            sleepMs(1000);
            ref.inc();
            group.awaitCommit(getThreadLocalTransaction());
        }
    }

    @Test
    public void whenTransactionAborted_thenDeadTransactionException() throws InterruptedException {
        Transaction tx = txFactory.start();
        tx.abort();

        VetoCommitBarrier group = new VetoCommitBarrier();
        try {
            group.awaitCommit(tx);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertTrue(group.isClosed());
        assertIsAborted(tx);
        assertEquals(0, group.getNumberWaiting());
    }

    @Test
    public void whenTransactionCommitted_thenDeadTransactionException() throws InterruptedException {
        Transaction tx = txFactory.start();
        tx.commit();

        VetoCommitBarrier group = new VetoCommitBarrier();
        try {
            group.awaitCommit(tx);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertTrue(group.isClosed());
        assertIsCommitted(tx);
        assertEquals(0, group.getNumberWaiting());
    }

    @Test
    public void whenBarrierAborted_thenClosedCommitBarrierException() throws InterruptedException {
        VetoCommitBarrier barrier = new VetoCommitBarrier();
        barrier.abort();

        Transaction tx = txFactory.start();
        try {
            barrier.awaitCommit(tx);
            fail();
        } catch (ClosedCommitBarrierException expected) {
        }

        assertIsActive(tx);
        assertTrue(barrier.isAborted());
        assertEquals(0, barrier.getNumberWaiting());
    }

    @Test
    public void whenCommitted_thenClosedCommitBarrierException() throws InterruptedException {
        VetoCommitBarrier barrier = new VetoCommitBarrier();
        barrier.commit();

        Transaction tx = txFactory.start();
        try {
            barrier.awaitCommit(tx);
            fail();
        } catch (ClosedCommitBarrierException expected) {
        }

        assertIsActive(tx);
        assertTrue(barrier.isCommitted());
        assertEquals(0, barrier.getNumberWaiting());
    }

    public class IncThread extends TestThread {
        private final TransactionalInteger ref;
        private final VetoCommitBarrier barrier;
        private Transaction tx;

        public IncThread(TransactionalInteger ref, VetoCommitBarrier barrier) {
            super("IncThread");
            this.barrier = barrier;
            this.ref = ref;
        }

        @Override
        @TransactionalMethod
        public void doRun() throws Exception {
            tx = getThreadLocalTransaction();
            ref.inc();
            barrier.awaitCommit(tx);
        }
    }
}