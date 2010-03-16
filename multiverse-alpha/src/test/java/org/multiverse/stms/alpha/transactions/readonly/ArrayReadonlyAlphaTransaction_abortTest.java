package org.multiverse.stms.alpha.transactions.readonly;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRefTranlocal;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;
import org.multiverse.stms.alpha.transactions.OptimalSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.multiverse.TestUtils.assertIsAborted;

public class ArrayReadonlyAlphaTransaction_abortTest {
    private AlphaStm stm;
    private AlphaStmConfig stmConfig;
    private OptimalSize optimalSize;

    @Before
    public void setUp() {
        stmConfig = AlphaStmConfig.createDebugConfig();
        stm = new AlphaStm(stmConfig);
    }

    public ArrayReadonlyAlphaTransaction startTransactionUnderTest() {
        optimalSize = new OptimalSize(100);
        ArrayReadonlyAlphaTransaction.Config config = new ArrayReadonlyAlphaTransaction.Config(
                stmConfig.clock,
                stmConfig.backoffPolicy,
                null,
                stmConfig.maxRetryCount, true, optimalSize, 10);
        return new ArrayReadonlyAlphaTransaction(config, 5);
    }

    @Test
    public void whenUsed() {
        ManualRef ref = new ManualRef(stm);
        ManualRefTranlocal expectedTranlocal = (ManualRefTranlocal) ref.___load();

        long expectedVersion = stm.getVersion();
        AlphaTransaction tx = startTransactionUnderTest();
        tx.openForRead(ref);
        tx.abort();

        assertIsAborted(tx);
        assertSame(expectedTranlocal, ref.___load());
        assertEquals(expectedVersion, stm.getVersion());
    }

    @Test
    public void whenUnused() {
        long expectedVersion = stm.getVersion();
        AlphaTransaction tx = startTransactionUnderTest();
        tx.abort();

        assertEquals(expectedVersion, stm.getVersion());
        assertIsAborted(tx);
    }
}
