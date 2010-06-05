package org.multiverse.stms.alpha.transactions;

import org.multiverse.api.TraceLevel;
import org.multiverse.api.TransactionFactory;
import org.multiverse.api.backoff.BackoffPolicy;
import org.multiverse.api.clock.PrimitiveClock;
import org.multiverse.stms.AbstractTransactionConfiguration;

/**
 * @author Peter Veentjer
 */
public class AbstractAlphaTransactionConfiguration extends AbstractTransactionConfiguration {

    public final SpeculativeConfiguration speculativeConfiguration;
    public final int syncToClock;

    public AbstractAlphaTransactionConfiguration(
            PrimitiveClock clock, BackoffPolicy backoffPolicy, String familyName,
            boolean readOnly, int maxRetries, boolean interruptible, boolean writeSkewAllowed,
            boolean readTrackingEnabled, boolean explicitRetryAllowed, SpeculativeConfiguration speculativeConfiguration,
            long timeoutNs, int maxReadSpinCount, TransactionFactory transactionFactory, TraceLevel traceLevel,
            int syncToClock) {

        super(clock, backoffPolicy, familyName, readOnly, maxRetries, interruptible,
                writeSkewAllowed, readTrackingEnabled, explicitRetryAllowed, timeoutNs,
                maxReadSpinCount, transactionFactory, traceLevel);

        this.syncToClock = syncToClock;
        this.speculativeConfiguration = speculativeConfiguration;
    }
}
