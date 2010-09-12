package org.multiverse.stms.beta.benchmarks;

import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactionalobjects.BetaLongRef;
import org.multiverse.stms.beta.transactions.BetaTransactionConfiguration;
import org.multiverse.stms.beta.transactions.FatArrayBetaTransaction;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.multiverse.stms.beta.BetaStmUtils.format;
import static org.multiverse.stms.beta.BetaStmUtils.newReadBiasedLongRef;
import static org.multiverse.stms.beta.benchmarks.BenchmarkUtils.*;

/**
 * @author Peter Veentjer
 */
public class UncontendedMultipleReadScalabilityTest {
    private BetaStm stm;
    private final long transactionCount = 100 * 1000 * 1000;


    public static void main(String[] args) {
        //should be a power of two.
        int refCount = Integer.parseInt(args[0]);
        UncontendedMultipleReadScalabilityTest test = new UncontendedMultipleReadScalabilityTest();

        test.start(refCount);
    }

    public void start(int refCount) {
        int[] processors = generateProcessorRange();

        System.out.printf("Multiverse> Uncontended multiple read transaction benchmark\n");
        System.out.printf("Multiverse> Running with the following processor range %s\n", Arrays.toString(processors));
        System.out.printf("Multiverse> Running with %s transactionalobjects per transaction\n", refCount);
        System.out.printf("Multiverse> %s Transactions per thread\n", format(transactionCount));
        Result[] result = new Result[processors.length];

        System.out.printf("Multiverse> Starting warmup run\n");
        test(1, 1);
        System.out.printf("Multiverse> Finished warmup run\n");

        long startNs = System.nanoTime();

        for (int k = 0; k < processors.length; k++) {
            int processorCount = processors[k];
            double performance = test(processorCount, refCount);
            result[k] = new Result(processorCount, performance);
        }

        long durationNs = System.nanoTime() - startNs;
        System.out.printf("Benchmark took %s seconds\n", TimeUnit.NANOSECONDS.toSeconds(durationNs));

        toGnuplot(result);
    }


    private double test(int threadCount, int refCount) {
        System.out.printf("Multiverse> Running with %s processors\n", threadCount);

        stm = new BetaStm();

        ReadThread[] threads = new ReadThread[threadCount];

        for (int k = 0; k < threads.length; k++) {
            threads[k] = new ReadThread(k, refCount);
        }
        for (ReadThread thread : threads) {
            thread.start();
        }

        for (ReadThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        long totalDurationMs = 0;
        for (ReadThread t : threads) {
            totalDurationMs += t.durationMs;
        }

        double readsPerSecond = transactionsPerSecondPerThread(
                transactionCount * refCount, totalDurationMs, threadCount);

        System.out.printf("Multiverse> Performance %s reads/second with %s threads\n",
                format(readsPerSecond), threadCount);
        return readsPerSecond;
    }

    class ReadThread extends Thread {
        private final int refCount;
        private long durationMs;

        public ReadThread(int id, int refCount) {
            super("ReadThread-" + id);
            setPriority(Thread.MAX_PRIORITY);
            this.refCount = refCount;
        }

        public void run() {
            switch (refCount) {
                case 1:
                    run1();
                    break;
                case 2:
                    run2();
                    break;
                case 4:
                    run4();
                    break;
                case 8:
                    run8();
                    break;
                case 16:
                    run16();
                    break;
                case 32:
                    run32();
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        public void run1() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);

            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 1)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.commit();
                tx.hardReset();
            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }

        public void run2() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);
            BetaLongRef ref2 = newReadBiasedLongRef(stm);

            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 2)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.openForRead(ref2, false);
                tx.commit();
                tx.hardReset();

            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }

        public void run4() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);
            BetaLongRef ref2 = newReadBiasedLongRef(stm);
            BetaLongRef ref3 = newReadBiasedLongRef(stm);
            BetaLongRef ref4 = newReadBiasedLongRef(stm);

            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 4)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.openForRead(ref2, false);
                tx.openForRead(ref3, false);
                tx.openForRead(ref4, false);
                tx.commit();
                tx.hardReset();

            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }

        public void run8() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);
            BetaLongRef ref2 = newReadBiasedLongRef(stm);
            BetaLongRef ref3 = newReadBiasedLongRef(stm);
            BetaLongRef ref4 = newReadBiasedLongRef(stm);
            BetaLongRef ref5 = newReadBiasedLongRef(stm);
            BetaLongRef ref6 = newReadBiasedLongRef(stm);
            BetaLongRef ref7 = newReadBiasedLongRef(stm);
            BetaLongRef ref8 = newReadBiasedLongRef(stm);

            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 8)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.openForRead(ref2, false);
                tx.openForRead(ref3, false);
                tx.openForRead(ref4, false);
                tx.openForRead(ref5, false);
                tx.openForRead(ref6, false);
                tx.openForRead(ref7, false);
                tx.openForRead(ref8, false);

                tx.commit();
                tx.hardReset();

            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }

        public void run16() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);
            BetaLongRef ref2 = newReadBiasedLongRef(stm);
            BetaLongRef ref3 = newReadBiasedLongRef(stm);
            BetaLongRef ref4 = newReadBiasedLongRef(stm);
            BetaLongRef ref5 = newReadBiasedLongRef(stm);
            BetaLongRef ref6 = newReadBiasedLongRef(stm);
            BetaLongRef ref7 = newReadBiasedLongRef(stm);
            BetaLongRef ref8 = newReadBiasedLongRef(stm);
            BetaLongRef ref9 = newReadBiasedLongRef(stm);
            BetaLongRef ref10 = newReadBiasedLongRef(stm);
            BetaLongRef ref11 = newReadBiasedLongRef(stm);
            BetaLongRef ref12 = newReadBiasedLongRef(stm);
            BetaLongRef ref13 = newReadBiasedLongRef(stm);
            BetaLongRef ref14 = newReadBiasedLongRef(stm);
            BetaLongRef ref15 = newReadBiasedLongRef(stm);
            BetaLongRef ref16 = newReadBiasedLongRef(stm);

            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 16)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.openForRead(ref2, false);
                tx.openForRead(ref3, false);
                tx.openForRead(ref4, false);
                tx.openForRead(ref5, false);
                tx.openForRead(ref6, false);
                tx.openForRead(ref7, false);
                tx.openForRead(ref8, false);
                tx.openForRead(ref9, false);
                tx.openForRead(ref10, false);
                tx.openForRead(ref11, false);
                tx.openForRead(ref12, false);
                tx.openForRead(ref13, false);
                tx.openForRead(ref14, false);
                tx.openForRead(ref15, false);
                tx.openForRead(ref16, false);

                tx.commit();
                tx.hardReset();

            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }

        public void run32() {
            BetaLongRef ref1 = newReadBiasedLongRef(stm);
            BetaLongRef ref2 = newReadBiasedLongRef(stm);
            BetaLongRef ref3 = newReadBiasedLongRef(stm);
            BetaLongRef ref4 = newReadBiasedLongRef(stm);
            BetaLongRef ref5 = newReadBiasedLongRef(stm);
            BetaLongRef ref6 = newReadBiasedLongRef(stm);
            BetaLongRef ref7 = newReadBiasedLongRef(stm);
            BetaLongRef ref8 = newReadBiasedLongRef(stm);
            BetaLongRef ref9 = newReadBiasedLongRef(stm);
            BetaLongRef ref10 = newReadBiasedLongRef(stm);
            BetaLongRef ref11 = newReadBiasedLongRef(stm);
            BetaLongRef ref12 = newReadBiasedLongRef(stm);
            BetaLongRef ref13 = newReadBiasedLongRef(stm);
            BetaLongRef ref14 = newReadBiasedLongRef(stm);
            BetaLongRef ref15 = newReadBiasedLongRef(stm);
            BetaLongRef ref16 = newReadBiasedLongRef(stm);
            BetaLongRef ref17 = newReadBiasedLongRef(stm);
            BetaLongRef ref18 = newReadBiasedLongRef(stm);
            BetaLongRef ref19 = newReadBiasedLongRef(stm);
            BetaLongRef ref20 = newReadBiasedLongRef(stm);
            BetaLongRef ref21 = newReadBiasedLongRef(stm);
            BetaLongRef ref22 = newReadBiasedLongRef(stm);
            BetaLongRef ref23 = newReadBiasedLongRef(stm);
            BetaLongRef ref24 = newReadBiasedLongRef(stm);
            BetaLongRef ref25 = newReadBiasedLongRef(stm);
            BetaLongRef ref26 = newReadBiasedLongRef(stm);
            BetaLongRef ref27 = newReadBiasedLongRef(stm);
            BetaLongRef ref28 = newReadBiasedLongRef(stm);
            BetaLongRef ref29 = newReadBiasedLongRef(stm);
            BetaLongRef ref30 = newReadBiasedLongRef(stm);
            BetaLongRef ref31 = newReadBiasedLongRef(stm);
            BetaLongRef ref32 = newReadBiasedLongRef(stm);


            BetaTransactionConfiguration config = new BetaTransactionConfiguration(stm, 32)
                    .setReadonly(true);
            FatArrayBetaTransaction tx = new FatArrayBetaTransaction(config);

            long startMs = System.currentTimeMillis();

            for (long iteration = 0; iteration < transactionCount; iteration++) {
                tx.openForRead(ref1, false);
                tx.openForRead(ref2, false);
                tx.openForRead(ref3, false);
                tx.openForRead(ref4, false);
                tx.openForRead(ref5, false);
                tx.openForRead(ref6, false);
                tx.openForRead(ref7, false);
                tx.openForRead(ref8, false);
                tx.openForRead(ref9, false);
                tx.openForRead(ref10, false);
                tx.openForRead(ref11, false);
                tx.openForRead(ref12, false);
                tx.openForRead(ref13, false);
                tx.openForRead(ref14, false);
                tx.openForRead(ref15, false);
                tx.openForRead(ref16, false);
                tx.openForRead(ref17, false);
                tx.openForRead(ref18, false);
                tx.openForRead(ref19, false);
                tx.openForRead(ref20, false);
                tx.openForRead(ref21, false);
                tx.openForRead(ref22, false);
                tx.openForRead(ref23, false);
                tx.openForRead(ref24, false);
                tx.openForRead(ref25, false);
                tx.openForRead(ref26, false);
                tx.openForRead(ref27, false);
                tx.openForRead(ref28, false);
                tx.openForRead(ref29, false);
                tx.openForRead(ref30, false);
                tx.openForRead(ref31, false);
                tx.openForRead(ref32, false);

                tx.commit();
                tx.hardReset();
            }

            durationMs = System.currentTimeMillis() - startMs;
            System.out.printf("Multiverse> %s is finished in %s ms\n", getName(), durationMs);
        }
    }
}
