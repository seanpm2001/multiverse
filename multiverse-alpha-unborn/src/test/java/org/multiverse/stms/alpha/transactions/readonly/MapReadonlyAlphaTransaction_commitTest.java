package org.multiverse.stms.alpha.transactions.readonly;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Transaction;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRefTranlocal;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.multiverse.TestUtils.assertIsAborted;
import static org.multiverse.TestUtils.assertIsCommitted;

/**
 * @author Peter Veentjer
 */
public class MapReadonlyAlphaTransaction_commitTest {

    private AlphaStm stm;
    private AlphaStmConfig stmConfig;

    @Before
    public void setUp() {
        stmConfig = AlphaStmConfig.createDebugConfig();
        stm = new AlphaStm(stmConfig);
    }

    public MapReadonlyAlphaTransaction createSutTransaction() {
        ReadonlyConfiguration config = new ReadonlyConfiguration(stmConfig.clock, true);
        return new MapReadonlyAlphaTransaction(config);
    }

    @Test
    public void commitDoesNotLockTxObjects() {
        ManualRef ref = new ManualRef(stm);

        AlphaTransaction tx = createSutTransaction();
        tx.openForRead(ref);
        ref.resetLockInfo();
        tx.commit();

        ref.assertNoLockAcquired();
        ref.assertNoLocksReleased();
    }

    @Test
    public void whenUsed() {
        ManualRef ref = new ManualRef(stm);
        ManualRefTranlocal expectedTranlocal = (ManualRefTranlocal) ref.___load();

        long startVersion = stm.getVersion();
        AlphaTransaction tx = createSutTransaction();
        tx.openForRead(ref);
        tx.abort();

        assertIsAborted(tx);
        assertSame(expectedTranlocal, ref.___load());
        assertEquals(startVersion, stm.getVersion());
    }

    @Test
    public void whenUnused() {
        long startVersion = stm.getVersion();
        Transaction tx = createSutTransaction();
        tx.abort();

        assertEquals(startVersion, stm.getVersion());
        assertIsAborted(tx);
    }

    @Test
    public void whenWriteConflictTransaction_thenInsensitiveForConflict() {
        ManualRef ref = new ManualRef(stm, 10);

        AlphaTransaction tx = createSutTransaction();
        tx.openForRead(ref);

        //conflicting write
        ref.inc(stm);
        long expectedVersion = stm.getVersion();

        tx.commit();

        assertIsCommitted(tx);
        assertEquals(expectedVersion, stm.getVersion());
    }
}
