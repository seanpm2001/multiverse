package org.multiverse.stms.alpha.transactions.update;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Transaction;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRefTranlocal;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.assertIsAborted;

/**
 * @author Peter Veentjer
 */
public class GrowingUpdateAlphaTransaction_abortTest {

    private AlphaStm stm;
    private AlphaStmConfig stmConfig;

    @Before
    public void setUp() {
        stmConfig = AlphaStmConfig.createDebugConfig();
        stm = new AlphaStm(stmConfig);
    }

    public GrowingUpdateAlphaTransaction startSutTransaction() {
        GrowingUpdateAlphaTransaction.Config config = new GrowingUpdateAlphaTransaction.Config(
                stmConfig.clock,
                stmConfig.restartBackoffPolicy,
                null,
                stmConfig.profiler,
                stmConfig.commitLockPolicy,
                stmConfig.maxRetryCount,
                false, true, true, true,true);
        return new GrowingUpdateAlphaTransaction(config);
    }

    @Test
    public void abortDoesNotLockDirtyObject() {
        ManualRef ref = new ManualRef(stm);

        AlphaTransaction tx = startSutTransaction();
        ManualRefTranlocal tranlocal = (ManualRefTranlocal) tx.openForWrite(ref);
        tranlocal.value++;
        ref.resetLockInfo();
        tx.abort();

        assertFalse(ref.isTryLockCalled());
    }

    @Test
    public void abortDoesNotLockReadonlyObject() {
        ManualRef ref = new ManualRef(stm);

        AlphaTransaction tx = startSutTransaction();
        tx.openForRead(ref);
        ref.resetLockInfo();
        tx.abort();

        assertFalse(ref.isTryLockCalled());
    }

    @Test
    public void abortDoesNotLockFreshObject() {
        AlphaTransaction tx = startSutTransaction();
        ManualRef ref = new ManualRef(tx,0);
        tx.openForWrite(ref);
        ref.resetLockInfo();
        tx.abort();

        assertFalse(ref.isTryLockCalled());
    }

    @Test
    public void whenUnused() {
        Transaction tx = startSutTransaction();
        long startTime = stm.getVersion();
        tx.abort();
        assertEquals(startTime, stm.getVersion());
        assertIsAborted(tx);
    }

    @Test
    public void whenTransactionContainsReads() {
        ManualRef ref = new ManualRef(stm, 10);
        ManualRefTranlocal original = (ManualRefTranlocal) ref.___load();

        long version = stm.getVersion();
        AlphaTransaction tx = startSutTransaction();
        tx.openForRead(ref);
        tx.abort();

        assertEquals(version, stm.getVersion());
        assertIsAborted(tx);
        assertSame(original, ref.___load());
    }

    @Test
    public void whenTransactionContainsNonDirtyObject_thenChangesAreNotWritten() {
        ManualRef ref = new ManualRef(stm, 10);
        ManualRefTranlocal original = (ManualRefTranlocal) ref.___load();

        long version = stm.getVersion();
        AlphaTransaction tx = startSutTransaction();
        ManualRefTranlocal tranlocal = (ManualRefTranlocal) tx.openForWrite(ref);
        tx.abort();

        assertEquals(version, stm.getVersion());
        assertIsAborted(tx);
        assertSame(original, ref.___load());
    }

    @Test
    public void whenTransactionContainsDirtyObject_thenChangesAreNotWritten() {
        ManualRef ref = new ManualRef(stm, 10);
        ManualRefTranlocal original = (ManualRefTranlocal) ref.___load();

        long version = stm.getVersion();
        AlphaTransaction tx = startSutTransaction();
        ManualRefTranlocal tranlocal = (ManualRefTranlocal) tx.openForWrite(ref);
        tranlocal.value++;
        tx.abort();

        assertEquals(version, stm.getVersion());
        assertIsAborted(tx);
        assertSame(original, ref.___load());
    }

    @Test
    public void whenTransactionContainsFreshObject_thenFreshObjectGetsNoTranlocal() {
        long startVersion = stm.getVersion();

        AlphaTransaction tx = startSutTransaction();
        ManualRef ref = new ManualRef(tx, 10);
        tx.abort();

        assertIsAborted(tx);
        assertEquals(startVersion, stm.getVersion());
        assertNull(ref.___load());
    }

    // ================== complex scenario ========================

    @Test
    public void abortComplexScenario() {
        ManualRef ref1 = new ManualRef(stm, 1);
        ManualRef ref2 = new ManualRef(stm, 1);
        ManualRef ref3 = new ManualRef(stm, 1);

        long startTime = stm.getVersion();

        AlphaTransaction tx = startSutTransaction();
        ref1.inc(tx);
        ref2.inc(tx);
        ref3.inc(tx);
        tx.abort();

        assertIsAborted(tx);
        assertEquals(startTime, stm.getVersion());
        assertEquals(1, ref1.get(stm));
        assertEquals(1, ref2.get(stm));
        assertEquals(1, ref3.get(stm));
    }

}
