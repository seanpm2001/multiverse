package org.multiverse.stms.beta.transactions;

public class LeanArrayTreeBetaTransaction_retryTest
        extends BetaTransaction_retryTest {

    @Override
    public int getTransactionMaxCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isSupportingListeners() {
        return false;
    }

    @Override
    public boolean isSupportingCommute() {
        return false;
    }

    @Override
    public BetaTransaction newTransaction() {
        return new LeanArrayTreeBetaTransaction(stm);
    }

    @Override
    public BetaTransaction newTransaction(BetaTransactionConfiguration config) {
        return new LeanArrayTreeBetaTransaction(config);
    }
}