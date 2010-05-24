package org.multiverse.stms.alpha.transactions.readonly;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaStmConfig;
import org.multiverse.stms.alpha.manualinstrumentation.ManualRef;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.multiverse.TestUtils.format;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

/**
 * todo:
 * variable number of threads
 * variable number of reads.
 *
 * @author Peter Veentjer
 */
public class NonTrackingReadonlyAlphaTransaction_performanceTest {

    private AlphaStm stm;
    private AlphaStmConfig stmConfig;
    private int transactionCount = 200 * 1000 * 1000;
    private ReadonlyConfiguration config;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stmConfig = AlphaStmConfig.createFastConfig();
        stm = new AlphaStm(stmConfig);
        config = new ReadonlyConfiguration(stmConfig.clock, false);
    }

    public NonTrackingReadonlyAlphaTransaction createSutTransaction() {
        return new NonTrackingReadonlyAlphaTransaction(config);
    }

    @Test
    public void whenTransactionsNotReused() {
        ManualRef ref = new ManualRef(stm, 10);

        long startNs = System.nanoTime();

        long version = stm.getVersion();

        for (int k = 0; k < transactionCount; k++) {
            AlphaTransaction tx = createSutTransaction();
            tx.openForRead(ref);
            tx.commit();

            if (k % (10 * 1000 * 1000) == 0) {
                System.out.printf("at %s\n", k);
            }
        }

        long periodNs = System.nanoTime() - startNs;
        double transactionPerSecond = (transactionCount * TimeUnit.SECONDS.toNanos(1)) / periodNs;
        System.out.printf("%s read-transactions/second\n", format(transactionPerSecond));

        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenTransactionReused() {
        ManualRef ref = new ManualRef(stm, 10);

        long startNs = System.nanoTime();
        long version = stm.getVersion();
        AlphaTransaction tx = createSutTransaction();

        for (int k = 0; k < transactionCount; k++) {
            tx.reset();
            tx.openForRead(ref);
            tx.commit();

            if (k % (10 * 1000 * 1000) == 0) {
                System.out.printf("at %s\n", k);
            }
        }

        long periodNs = System.nanoTime() - startNs;
        double transactionPerSecond = (transactionCount * TimeUnit.SECONDS.toNanos(1)) / periodNs;
        System.out.printf("%s reads-transactions/second\n", format(transactionPerSecond));

        assertEquals(version, stm.getVersion());
    }
}
