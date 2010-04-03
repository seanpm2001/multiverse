package org.multiverse.stms.alpha.transactions.update;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.exceptions.SpeculativeConfigurationFailure;
import org.multiverse.stms.alpha.AlphaProgrammaticLong;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.AlphaTranlocal;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRefTranlocal;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;
import org.multiverse.stms.alpha.transactions.SpeculativeConfiguration;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;

/**
 * @author Peter Veentjer
 */
public class MonoUpdateAlphaTransaction_openForConstructionTest {

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

        long version = stm.getVersion();
        try {
            tx.openForConstruction(null);
            fail();
        } catch (NullPointerException expected) {

        }

        assertIsActive(tx);
        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenCalledForTheFirstTime() {
        ManualRef ref = ManualRef.createUncommitted();

        AlphaTransaction tx = startSutTransaction();

        long version = stm.getVersion();
        ManualRefTranlocal tranlocal = (ManualRefTranlocal) tx.openForConstruction(ref);

        assertEquals(version, stm.getVersion());
        assertNotNull(tranlocal);
        assertTrue(tranlocal.isUncommitted());
        assertSame(ref, tranlocal.getTransactionalObject());
        assertNull(tranlocal.getOrigin());
        assertEquals(0, tranlocal.value);
    }

    @Test
    public void whenAlreadyOpenedForConstruction_sameInstanceReturned() {
        ManualRef ref = ManualRef.createUncommitted();

        AlphaTransaction tx = startSutTransaction();

        long version = stm.getVersion();
        ManualRefTranlocal firstTime = (ManualRefTranlocal) tx.openForConstruction(ref);
        ManualRefTranlocal found = (ManualRefTranlocal) tx.openForConstruction(ref);

        assertEquals(version, stm.getVersion());
        assertSame(firstTime, found);
        assertTrue(found.isUncommitted());
        assertSame(ref, found.getTransactionalObject());
        assertNull(found.getOrigin());
        assertEquals(0, found.value);
    }

    @Test
    @Ignore
    public void whenObjectAlreadyHasCommits_thenIllegalStateException() {
        ManualRef ref = new ManualRef(stm);
        AlphaTranlocal committed = ref.___load();

        AlphaTransaction tx = startSutTransaction();

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalStateException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertIsActive(tx);
        assertSame(committed, ref.___load());
    }


    @Test
    public void whenAlreadyOpenedForRead_thenIllegalStateException() {
        ManualRef ref = new ManualRef(stm);
        AlphaTranlocal committed = ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.openForRead(ref);

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalStateException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertIsActive(tx);
        assertSame(committed, ref.___load());
    }

    @Test
    public void whenAlreadyOpenedForWrite() {
        ManualRef ref = new ManualRef(stm);
        AlphaTranlocal committed = ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.openForWrite(ref);

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalStateException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertIsActive(tx);
        assertSame(committed, ref.___load());
    }

    @Test
    public void whenAlreadyOpenedForCommutingWrite() {
        AlphaProgrammaticLong ref = new AlphaProgrammaticLong(stm, 10);
        AlphaTranlocal committed = ref.___load();

        AlphaTransaction tx = startSutTransaction();
        tx.openForCommutingWrite(ref);

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (IllegalStateException expected) {
        }

        assertEquals(version, stm.getVersion());
        assertIsActive(tx);
        assertSame(committed, ref.___load());
    }

    @Test
    public void whenMaximumCapacityExceeded_thenTransactionTooSmallError() {
        ManualRef ref1 = ManualRef.createUncommitted();
        ManualRef ref2 = ManualRef.createUncommitted();

        SpeculativeConfiguration config = new SpeculativeConfiguration(100);
        AlphaTransaction tx = startSutTransaction(config);
        tx.openForConstruction(ref1);

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref2);
            fail();
        } catch (SpeculativeConfigurationFailure expected) {
        }

        assertEquals(2, config.getOptimalSize());
        assertIsActive(tx);
        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenAborted_thenDeadTransactionException() {
        ManualRef ref = ManualRef.createUncommitted();

        AlphaTransaction tx = startSutTransaction();
        tx.abort();

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsAborted(tx);
        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenCommitted_thenDeadTransactionException() {
        ManualRef ref = ManualRef.createUncommitted();

        AlphaTransaction tx = startSutTransaction();
        tx.commit();

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsCommitted(tx);
        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenPrepared_thenPreparedTransactionException() {
        ManualRef ref = ManualRef.createUncommitted();

        AlphaTransaction tx = startSutTransaction();
        tx.prepare();

        long version = stm.getVersion();
        try {
            tx.openForConstruction(ref);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsPrepared(tx);
        assertEquals(version, stm.getVersion());
    }
}