package org.multiverse.stms.alpha.transactions.update;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.exceptions.*;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.AlphaTranlocal;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRefTranlocal;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;
import org.multiverse.stms.alpha.transactions.SpeculativeConfiguration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.alpha.transactions.AlphaTransactionTestUtils.assertIsUpdatableClone;

public class MonoUpdateAlphaTransaction_openForWriteTest {

    private AlphaStm stm;
    private AlphaStmConfig stmConfig;

    @Before
    public void setUp() {
        stmConfig = AlphaStmConfig.createDebugConfig();
        stm = new AlphaStm(stmConfig);
    }

    public MonoUpdateAlphaTransaction startSutTransaction(SpeculativeConfiguration speculativeConfig) {
        UpdateConfiguration config = new UpdateConfiguration(stmConfig.clock)
                .withSpeculativeConfiguration(speculativeConfig);
        return new MonoUpdateAlphaTransaction(config);
    }

    public MonoUpdateAlphaTransaction startSutTransaction() {
        return startSutTransaction(new SpeculativeConfiguration(100));
    }

    @Test
    public void whenNullTxObject_thenNullPointerException() {
        AlphaTransaction tx = startSutTransaction();

        try {
            tx.openForWrite(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertIsActive(tx);
    }

    @Test
    public void whenOpenForWriteFirstTime_thenAttached() {
        ManualRef ref = new ManualRef(stm);
        AlphaTranlocal committed = ref.___load();
        AlphaTransaction tx = startSutTransaction();

        AlphaTranlocal found = tx.openForWrite(ref);

        assertIsUpdatableClone(ref, committed, found);
        assertSame(found, getField(tx, "attached"));
    }

    @Test
    public void whenOpenForWriteOnFreshObject_thenUncommittedReadConflict() {
        ManualRef ref = ManualRef.createUncommitted();
        AlphaTransaction tx = startSutTransaction();

        long version = stm.getVersion();
        try {
            tx.openForWrite(ref);
            fail();
        } catch (UncommittedReadConflict expected) {

        }

        assertNull(ref.___load());
        assertNull(getField(tx, "attached"));
        assertEquals(version, stm.getVersion());
        assertIsActive(tx);
    }

    @Test
    public void whenVersionTooNew_thenOldVersionNotFoundReadConflict() {
        ManualRef ref = new ManualRef(stm);
        AlphaTransaction tx = startSutTransaction();
        ref.inc(stm);

        try {
            tx.openForWrite(ref);
            fail();
        } catch (OldVersionNotFoundReadConflict expected) {
        }

        assertIsActive(tx);
        assertNull(getField(tx, "attached"));
    }

    @Test
    public void whenLocked_thenLockNotFreeReadConflict() {
        ManualRef ref = new ManualRef(stm);
        AlphaTransaction lockOwner = mock(AlphaTransaction.class);
        ref.___tryLock(lockOwner);

        AlphaTransaction tx = startSutTransaction();
        try {
            tx.openForWrite(ref);
            fail();
        } catch (LockNotFreeReadConflict ex) {
        }

        assertIsActive(tx);
        assertNull(getField(tx, "attached"));
    }

    @Test
    public void whenOpenForWriteSecondTime_thenPreviousTranlocalReturned() {
        ManualRef ref = new ManualRef(stm);

        AlphaTransaction tx = startSutTransaction();
        AlphaTranlocal found1 = tx.openForWrite(ref);
        AlphaTranlocal found2 = tx.openForWrite(ref);
        assertSame(found1, found2);
    }

    @Test
    public void whenAlreadyOpenedForRead_thenUpgradedToOpenedForWrite() {
        ManualRef ref = new ManualRef(stm);
        AlphaTranlocal committed = ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.openForRead(ref);
        AlphaTranlocal found = tx.openForWrite(ref);

        assertIsUpdatableClone(ref, committed, found);
        assertSame(found, getField(tx, "attached"));
    }

    @Test
    @Ignore
    public void whenAlreadyOpenedForCommutingWrite() {

    }


    @Test
    public void whenAlreadyAnotherOpenForRead_thenSpeculativeConfigurationFailure() {
        ManualRef ref1 = new ManualRef(stm);
        ManualRef ref2 = new ManualRef(stm);

        SpeculativeConfiguration speculativeConfig = new SpeculativeConfiguration(100);
        AlphaTransaction tx = startSutTransaction(speculativeConfig);
        tx.openForRead(ref1);

        try {
            tx.openForWrite(ref2);
            fail();
        } catch (SpeculativeConfigurationFailure ex) {
        }

        assertIsActive(tx);
        assertEquals(2, speculativeConfig.getOptimalSize());
    }


    @Test
    public void whenAlreadyAnotherOpenForWrite_thenSpeculativeConfigurationFailure() {
        ManualRef ref1 = new ManualRef(stm);
        ManualRef ref2 = new ManualRef(stm);

        SpeculativeConfiguration speculativeConfig = new SpeculativeConfiguration(100);
        AlphaTransaction tx = startSutTransaction(speculativeConfig);
        tx.openForWrite(ref1);

        try {
            tx.openForWrite(ref2);
            fail();
        } catch (SpeculativeConfigurationFailure ex) {
        }

        assertIsActive(tx);
        assertEquals(2, speculativeConfig.getOptimalSize());
    }

    @Test
    public void whenAborted_thenDeadTransactionException() {
        ManualRef ref = new ManualRef(stm);
        ManualRefTranlocal committed = (ManualRefTranlocal) ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.abort();

        long version = stm.getVersion();

        try {
            tx.openForWrite(ref);
            fail();
        } catch (DeadTransactionException ex) {
        }

        assertIsAborted(tx);
        assertEquals(version, stm.getVersion());
        assertSame(committed, ref.___load());
    }

    @Test
    public void whenCommitted_thenDeadTransactionException() {
        ManualRef ref = new ManualRef(stm);
        ManualRefTranlocal committed = (ManualRefTranlocal) ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.commit();

        long version = stm.getVersion();

        try {
            tx.openForWrite(ref);
            fail();
        } catch (DeadTransactionException ex) {
        }

        assertIsCommitted(tx);
        assertEquals(version, stm.getVersion());
        assertSame(committed, ref.___load());
    }

    @Test
    public void whenPrepared_thenPreparedTransactionException() {
        ManualRef ref = new ManualRef(stm);

        AlphaTransaction tx = startSutTransaction();
        tx.prepare();

        try {
            tx.openForWrite(ref);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsPrepared(tx);
    }
}

