package org.multiverse.benchmarks;

import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmUtils;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.multiverse.benchmarks.BenchmarkUtils.*;
import static org.multiverse.stms.beta.BetaStmUtils.format;

/**
 * @author Peter Veentjer
 */
public class UncontendedAtomicSetScalabilityTest {
    private BetaStm stm;

    public static void main(String[] args) {
        UncontendedAtomicSetScalabilityTest test = new UncontendedAtomicSetScalabilityTest();
        test.start(Long.parseLong(args[0]));
    }

    public void start(long transactionCount) {
        int[] processors = generateProcessorRange();

        System.out.println("Multiverse> Uncontended atomicSet transaction benchmark");
        System.out.println("Multiverse> 1 BetaRef per transaction");
        System.out.printf("Multiverse> %s Transactions per thread\n", format(transactionCount));
        System.out.printf("Multiverse> Running with the following processor range %s\n", Arrays.toString(processors));
        Result[] result = new Result[processors.length];

        System.out.println("Multiverse> Starting warmup run");
        test(1, transactionCount);
        System.out.println("Multiverse> Finished warmup run");

        long startNs = System.nanoTime();

        for (int k = 0; k < processors.length; k++) {
            int processorCount = processors[k];
            double performance = test(processorCount, transactionCount);
            result[k] = new Result(processorCount, performance);
        }

        long durationNs = System.nanoTime() - startNs;
        System.out.printf("Multiverse> Benchmark took %s seconds\n", TimeUnit.NANOSECONDS.toSeconds(durationNs));

        toGnuplot(result);
    }

    private double test(int threadCount, long transactionsPerThread) {
        System.out.printf("Multiverse> ----------------------------------------------\n");
        System.out.printf("Multiverse> Running with %s thread(s)\n", threadCount);

        stm = new BetaStm();

        UpdateThread[] threads = new UpdateThread[threadCount];

        for (int k = 0; k < threads.length; k++) {
            threads[k] = new UpdateThread(k, transactionsPerThread);
        }

        for (UpdateThread thread : threads) {
            thread.start();
        }

        for (UpdateThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        long totalDurationMs = 0;
        for (UpdateThread t : threads) {
            totalDurationMs += t.durationMs;
        }

        double transactionsPerSecondPerThread = transactionsPerSecondPerThread(
                transactionsPerThread, totalDurationMs, threadCount);
        System.out.printf("Multiverse> Performance %s transactions/second/thread with %s threads\n",
                format(transactionsPerSecondPerThread), threadCount);
        System.out.printf("Multiverse> Performance %s transactions/second\n",
                transactionsPerSecondAsString(transactionsPerThread, totalDurationMs, threadCount));

        return transactionsPerSecondPerThread;
    }

    class UpdateThread extends Thread {
        private final long transactionCount;
        private long durationMs;

        public UpdateThread(int id, long transactionCount) {
            super("UpdateThread-" + id);
            setPriority(Thread.MAX_PRIORITY);
            this.transactionCount = transactionCount;
        }

        public void run() {
            BetaLongRef ref = BetaStmUtils.createReadBiasedLongRef(stm, -1);

            BetaObjectPool pool = new BetaObjectPool();

            long startMs = System.currentTimeMillis();
            for (long k = 0; k < transactionCount; k++) {
                ref.atomicGetAndSet(k, pool);
            }

            assertEquals(transactionCount, ref.___unsafeLoad().value + 1);

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }
    }
}