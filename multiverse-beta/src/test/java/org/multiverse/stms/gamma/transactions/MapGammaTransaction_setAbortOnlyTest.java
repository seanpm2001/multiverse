package org.multiverse.stms.gamma.transactions;

public class MapGammaTransaction_setAbortOnlyTest extends GammaTransaction_setAbortOnlyTest<MapGammaTransaction> {
    @Override
    protected MapGammaTransaction newTransaction() {
        return new MapGammaTransaction(stm);
    }

    @Override
    protected MapGammaTransaction newTransaction(GammaTransactionConfiguration config) {
        return new MapGammaTransaction(config);
    }
}