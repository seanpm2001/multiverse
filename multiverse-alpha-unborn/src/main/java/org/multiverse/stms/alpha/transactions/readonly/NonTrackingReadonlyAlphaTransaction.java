package org.multiverse.stms.alpha.transactions.readonly;

import org.multiverse.api.Stm;
import org.multiverse.api.TransactionFactory;
import org.multiverse.api.TransactionFactoryBuilder;
import org.multiverse.api.latches.Latch;
import org.multiverse.stms.alpha.AlphaStm;
import org.multiverse.stms.alpha.AlphaTranlocal;
import org.multiverse.stms.alpha.AlphaTransactionalObject;
import org.multiverse.stms.alpha.transactions.AlphaTransaction;

/**
 * A readonly {@link org.multiverse.stms.alpha.transactions.AlphaTransaction} implementation that doesn't track reads.
 * <p/>
 * Unlike the {@link org.multiverse.stms.alpha.transactions.update.MapUpdateAlphaTransaction} a readonly transaction doesn't need track
 * any reads done. This has the advantage that a readonly transaction consumes a lot less resources (so no collection
 * needed to track all the reads) and commits are also a lot quicker (no dirtyness checking).
 * <p/>
 * A disadvantage of not tracking reads, is that the retry/orelse functionality is not available in reaodnly
 * transactions because the transaction has no clue which objects were loaded. So it also has no clue about the objects
 * to listen to on a retry.
 * <p/>
 * Although readonly transactions are isolated from update transactions from a correctness point of view, from a
 * practical point of view a readonly transaction could be obstructed by an update transaction:
 * <p/>
 * in the following scenario, the <u>second</u> load will fail with a {@code LoadTooOldVersionException}:
 * <p/>
 * <pre>
 * T1 (ro): |load_X-----------------load_X|
 * T2 (up):         |write_X|
 * </pre>
 * In the future a version history will be added for previous committed data. So the chance that a old version is not
 * available is going to decrease.
 *
 * @author Peter Veentjer.
 */
public class NonTrackingReadonlyAlphaTransaction extends AbstractReadonlyAlphaTransaction {

    public static class Factory implements TransactionFactory<AlphaTransaction> {

        private final ReadonlyConfiguration config;
        private final AlphaStm.AlphaTransactionFactoryBuilder transactionFactoryBuilder;

        public Factory(ReadonlyConfiguration config, AlphaStm.AlphaTransactionFactoryBuilder transactionFactoryBuilder) {
            this.config = config;
            this.transactionFactoryBuilder = transactionFactoryBuilder;
        }

        @Override
        public AlphaTransaction start() {
            AlphaTransaction tx = create();
            tx.start();
            return tx;
        }

        @Override
        public Stm getStm() {
            return transactionFactoryBuilder.getStm();
        }

        @Override
        public AlphaTransaction create() {
            return new NonTrackingReadonlyAlphaTransaction(config);
        }

        @Override
        public TransactionFactoryBuilder getTransactionFactoryBuilder() {
            return transactionFactoryBuilder;
        }
    }

    public NonTrackingReadonlyAlphaTransaction(ReadonlyConfiguration config) {
        super(config);
    }

    @Override
    protected boolean dodoRegisterRetryLatch(Latch latch, long wakeupVersion) {
        return false;
    }

    @Override
    protected AlphaTranlocal findAttached(AlphaTransactionalObject txObject) {
        return null;
    }

    @Override
    protected void attach(AlphaTranlocal tranlocal) {
        throw new UnsupportedOperationException();
    }
}
