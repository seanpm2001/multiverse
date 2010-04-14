package org.multiverse.stms.alpha.integrationtests;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.annotations.TransactionalMethod;
import org.multiverse.api.exceptions.ReadonlyException;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaTranlocal;
import org.multiverse.stms.alpha.AlphaTransactionalObject;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;
import org.multiverse.transactional.DefaultTransactionalReference;

import static org.junit.Assert.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.api.ThreadLocalTransaction.getThreadLocalTransaction;

public class ReadonlyTransactionTest {

    private static AlphaStm stm;

    @Before
    public void setUp() {
        stm = (AlphaStm) getGlobalStmInstance();
        clearThreadLocalTransaction();
    }

    @Test
    public void refIsTransformed() {
        DefaultTransactionalReference ref = new DefaultTransactionalReference();
        assertTrue(((Object) ref) instanceof AlphaTransactionalObject);
    }

    @Test
    public void whenNonTrackingRead_thenReadonlyOperationSucceeds() {
        DefaultTransactionalReference<Integer> ref = new DefaultTransactionalReference<Integer>(10);

        long version = stm.getVersion();

        executeNonTrackingReadonlyMethod(ref, 10);

        assertEquals(version, stm.getVersion());
    }

    @TransactionalMethod(readonly = true)
    public static void executeNonTrackingReadonlyMethod(DefaultTransactionalReference<Integer> ref, int expectedValue) {
        assertEquals(expectedValue, (int) ref.get());
    }

    @Test
    public void whenTrackingRead_thenReadonlyOperationSucceeds() {
        DefaultTransactionalReference<Integer> ref = new DefaultTransactionalReference<Integer>(10);

        long version = stm.getVersion();

        executeTrackingReadonlyMethod(ref, 10);

        assertEquals(version, stm.getVersion());
    }

    @TransactionalMethod(readonly = true, automaticReadTrackingEnabled = true)
    public static void executeTrackingReadonlyMethod(DefaultTransactionalReference<Integer> ref, int expectedValue) {
        int found = ref.get();
        assertEquals(expectedValue, found);
    }

    @Test
    public void whenNonTrackingReadonly_thenModificationInReadonlyTransactionIsDetected() {
        DefaultTransactionalReference<Integer> ref = new DefaultTransactionalReference<Integer>(0);

        long version = stm.getVersion();

        try {
            nonTrackingReadonlyMethodThatUpdates(ref);
            fail();
        } catch (ReadonlyException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertEquals(0, (int) ref.get());
    }

    @TransactionalMethod(readonly = true, automaticReadTrackingEnabled = false)
    public static void nonTrackingReadonlyMethodThatUpdates(DefaultTransactionalReference<Integer> ref) {
        ref.set(1);
    }

    @Test
    public void whenTrackingReadonly_thenModificationInReadonlyTransactionIsDetected() {
        DefaultTransactionalReference<Integer> ref = new DefaultTransactionalReference<Integer>(0);

        long version = stm.getVersion();

        try {
            trackingReadonlyMethodThatUpdates(ref);
            fail();
        } catch (ReadonlyException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertEquals(0, (int) ref.get());
    }

    @TransactionalMethod(readonly = true, automaticReadTrackingEnabled = false)
    public static void trackingReadonlyMethodThatUpdates(DefaultTransactionalReference<Integer> ref) {
        ref.set(1);
    }

    public static AlphaTranlocal getTranlocal(Object atomicObject) {
        AlphaTransaction t = (AlphaTransaction) getThreadLocalTransaction();
        return t.openForRead((AlphaTransactionalObject) atomicObject);
    }
}