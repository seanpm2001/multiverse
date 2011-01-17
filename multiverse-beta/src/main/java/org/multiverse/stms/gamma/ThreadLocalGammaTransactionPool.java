package org.multiverse.stms.gamma;

import org.multiverse.stms.gamma.transactions.GammaTransactionPool;

public class ThreadLocalGammaTransactionPool {

    private final static ThreadLocal<GammaTransactionPool> threadlocal = new ThreadLocal<GammaTransactionPool>() {
        @Override
        protected GammaTransactionPool initialValue() {
            return new GammaTransactionPool();
        }
    };


    /**
     * Returns the BetaObjectPool stored in the ThreadLocalBetaTransactionPool. If no instance exists,
     * a new instance is created.
     *
     * @return the BetaObjectPool.
     */
    public static GammaTransactionPool getThreadLocalGammaTransactionPool() {
        return threadlocal.get();
    }

    //we don't want any instances.

    private ThreadLocalGammaTransactionPool() {
    }
}
