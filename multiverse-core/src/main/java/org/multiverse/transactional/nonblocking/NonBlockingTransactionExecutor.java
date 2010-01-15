package org.multiverse.transactional.nonblocking;

import org.multiverse.api.Latch;
import org.multiverse.api.Stm;
import org.multiverse.api.Transaction;
import org.multiverse.api.exceptions.RetryError;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.setThreadLocalTransaction;

/**
 * Waarom zou je meer transacties willen? Ivm blocking van transaction. Atm heeft iedere transactie zijn eigen thread
 * nodig. Maar kan een thread ook meerdere transacties verwerken?
 * <p/>
 * Problems
 * <ol>
 * <li>
 * </li>
 * </ol>
 *
 * @author Peter Veentjer.
 */
public class NonBlockingTransactionExecutor {

    private final Stm stm;
    private volatile boolean shutdown = false;
    private Thread[] workerThreads;
    private int threadCount;
    private BlockingQueue<NonBlockingTaskWrapper> tasks = new LinkedBlockingQueue<NonBlockingTaskWrapper>();

    public NonBlockingTransactionExecutor(int threadCount) {
        stm = getGlobalStmInstance();
        this.threadCount = threadCount;
    }

    public void start() {
        workerThreads = new Thread[threadCount];
        for (int k = 0; k < workerThreads.length; k++) {
            workerThreads[k] = new Thread(new Worker());
            workerThreads[k].start();
        }
    }

    public void execute(TransactionalTask task) {
        if (task == null) {
            throw new NullPointerException();
        }

        try {
            tasks.put(new NonBlockingTaskWrapper(task));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException();
        }
    }

    public class Worker implements Runnable {

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    NonBlockingTaskWrapper task = tasks.take();
                    run(task);
                }
            } catch (InterruptedException ex) {
            }
        }

        private void run(NonBlockingTaskWrapper task) {
            Transaction t;
            if (task.previous == null) {
                t = task.transactionalTask.getTransactionFactory().start();
            } else {
                t = task.previous;
                t.restart();
            }

            setThreadLocalTransaction(t);
            try {
                boolean again = task.transactionalTask.execute(t);
                t.commit();
                if (again) {
                    //todo: return value ignored here.. s
                    tasks.offer(new NonBlockingTaskWrapper(task.transactionalTask));
                }
            } catch (RetryError ex) {
                Latch latch = new NonBlockingLatch(t, task.transactionalTask);
                t.registerRetryLatch(latch);
                ///System.out.println("RetryError encountered");
            }
        }
    }

    public class NonBlockingTaskWrapper {

        final TransactionalTask transactionalTask;
        final Transaction previous;

        public NonBlockingTaskWrapper(TransactionalTask transactionalTask, Transaction previous) {
            this.transactionalTask = transactionalTask;
            this.previous = previous;
        }

        public NonBlockingTaskWrapper(TransactionalTask transactionalTask) {
            this.transactionalTask = transactionalTask;
            this.previous = null;
        }
    }

    public class NonBlockingLatch implements Latch {

        private final AtomicBoolean open = new AtomicBoolean();
        private final Transaction previousTransaction;
        private final TransactionalTask transactionalTask;

        public NonBlockingLatch(Transaction previousTransaction, TransactionalTask transactionalTask) {
            this.previousTransaction = previousTransaction;
            this.transactionalTask = transactionalTask;
        }

        @Override
        public void open() {
            if (open.compareAndSet(false, true)) {
                NonBlockingTaskWrapper nonBlockingTaskWrapper = new NonBlockingTaskWrapper(
                        transactionalTask, previousTransaction);

                try {
                    tasks.put(nonBlockingTaskWrapper);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void await() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void awaitUninterruptible() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryAwait(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryAwaitUninterruptible(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}

