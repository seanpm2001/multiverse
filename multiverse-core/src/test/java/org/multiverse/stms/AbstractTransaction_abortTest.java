package org.multiverse.stms;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Transaction;
import org.multiverse.api.TransactionLifecycleEvent;
import org.multiverse.api.TransactionLifecycleListener;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.utils.clock.StrictClock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.multiverse.TestUtils.assertIsAborted;
import static org.multiverse.TestUtils.assertIsCommitted;

/**
 * @author Peter Veentjer
 */
public class AbstractTransaction_abortTest {

    private StrictClock clock;

    @Before
    public void setUp() {
        clock = new StrictClock(1);
    }

    @Test
    public void whenActive_thenAbort() {
        Transaction tx = new AbstractTransactionImpl();
        long startVersion = clock.getVersion();
        tx.abort();
        assertEquals(startVersion, clock.getVersion());
        assertIsAborted(tx);
    }

    @Test
    public void whenDoAbortThrowsError() {
        AbstractTransaction tx = spy(new AbstractTransactionImpl());

        RuntimeException expected = new RuntimeException();
        doThrow(expected).when(tx).doAbortActive();

        try {
            tx.abort();
            fail();
        } catch (RuntimeException found) {
            assertSame(expected, found);
            assertIsAborted(tx);
        }
    }

    @Test
    public void whenPrepared_thenAbort() {
        AbstractTransaction tx = spy(new AbstractTransactionImpl());
        tx.prepare();

        tx.abort();
        verify(tx, times(1)).doAbortPrepared();
    }

    @Test
    public void whenAlreadyCommitted_thenDeadTransactionException() {
        Transaction tx = new AbstractTransactionImpl();
        tx.commit();

        long startVersion = clock.getVersion();
        try {
            tx.abort();
            fail();
        } catch (DeadTransactionException ex) {
        }

        assertEquals(startVersion, clock.getVersion());
        assertIsCommitted(tx);
    }

    @Test
    public void whenAborted_thenIgnored() {
        Transaction tx = new AbstractTransactionImpl();
        tx.abort();

        long startVersion = clock.getVersion();
        tx.abort();
        assertEquals(startVersion, clock.getVersion());
        assertIsAborted(tx);
    }

    @Test
    public void whenAbort_thenPreAndPostCommitTasksAreCalled() {
        Transaction tx = new AbstractTransactionImpl();

        TransactionLifecycleListener listener = mock(TransactionLifecycleListener.class);
        tx.register(listener);

        tx.abort();

        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.preCommit);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.postCommit);
        verify(listener, times(1)).notify(tx, TransactionLifecycleEvent.preAbort);
        verify(listener, times(1)).notify(tx, TransactionLifecycleEvent.postAbort);
    }

    @Test
    public void whenPreAbortTaskFails_thenStillAborted() {
        Transaction tx = new AbstractTransactionImpl();

        RuntimeException exception = new RuntimeException();

        TransactionLifecycleListener listener = mock(TransactionLifecycleListener.class);
        doThrow(exception).when(listener).notify(tx, TransactionLifecycleEvent.preAbort);

        tx.register(listener);

        try {
            tx.abort();
            fail();
        } catch (RuntimeException found) {
            assertSame(exception, found);
        }

        assertIsAborted(tx);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.preCommit);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.postCommit);
        verify(listener, times(1)).notify(tx, TransactionLifecycleEvent.preAbort);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.postAbort);
    }

    @Test
    public void whenPostAbortTaskFails_exceptionThrown() {
        Transaction tx = new AbstractTransactionImpl();

        RuntimeException exception = new RuntimeException();

        TransactionLifecycleListener listener = mock(TransactionLifecycleListener.class);
        doThrow(exception).when(listener).notify(tx, TransactionLifecycleEvent.postAbort);

        tx.register(listener);

        try {
            tx.abort();
            fail();
        } catch (RuntimeException found) {
            assertSame(exception, found);
        }

        assertIsAborted(tx);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.preCommit);
        verify(listener, times(0)).notify(tx, TransactionLifecycleEvent.postCommit);
        verify(listener, times(1)).notify(tx, TransactionLifecycleEvent.preAbort);
        verify(listener, times(1)).notify(tx, TransactionLifecycleEvent.postAbort);
    }


}
