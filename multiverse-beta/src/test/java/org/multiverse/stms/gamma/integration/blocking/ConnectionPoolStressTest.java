package org.multiverse.stms.gamma.integration.blocking;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.api.AtomicBlock;
import org.multiverse.api.LockMode;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicClosure;
import org.multiverse.api.closures.AtomicIntClosure;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.stms.gamma.GammaConstants;
import org.multiverse.stms.gamma.GammaStm;
import org.multiverse.stms.gamma.transactionalobjects.GammaIntRef;
import org.multiverse.stms.gamma.transactionalobjects.GammaRef;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.StmUtils.retry;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

/**
 * A StressTest that simulates a database connection pool. The code is quite ugly, but that is because
 * no instrumentation is used here.
 *
 * @author Peter Veentjer.
 */
public class ConnectionPoolStressTest implements GammaConstants {
    private int poolsize = processorCount();
    private int threadCount = processorCount() * 2;
    private volatile boolean stop;

    private ConnectionPool pool;
    private GammaStm stm;
    private LockMode lockMode;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stm = (GammaStm) getGlobalStmInstance();
        stop = false;
    }

    @Test
    public void testNoLocking() {
        test(LockMode.None);
    }

    @Test
    public void testReadLockMode() {
        test(LockMode.Read);
    }

    @Test
    public void testWriteLockMode() {
        test(LockMode.Write);
    }

    @Test
    public void testCommitLockMode() {
        test(LockMode.Commit);
    }

    public void test(LockMode lockMode) {
        this.lockMode = lockMode;

        pool = new ConnectionPool(poolsize);
        WorkerThread[] threads = createThreads();

        startAll(threads);

        sleepMs(30 * 1000);
        stop = true;

        joinAll(threads);
        assertEquals(poolsize, pool.size());
    }

    class ConnectionPool {
        final AtomicBlock takeConnectionBlock = stm.createTransactionFactoryBuilder()
                .setWriteLockMode(lockMode)
                .setMaxRetries(10000)
                .buildAtomicBlock();

        final AtomicBlock returnConnectionBlock = stm.createTransactionFactoryBuilder()
                .setWriteLockMode(lockMode)
                .buildAtomicBlock();

        final AtomicBlock sizeBlock = stm.createTransactionFactoryBuilder().buildAtomicBlock();

        final GammaIntRef size = new GammaIntRef(stm);
        final GammaRef<Node<Connection>> head = new GammaRef<Node<Connection>>(stm);

        ConnectionPool(final int poolsize) {
            stm.getDefaultAtomicBlock().execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    size.set(poolsize);

                    Node<Connection> h = null;
                    for(int k=0;k<poolsize;k++){
                        h = new Node<Connection>(h, new Connection());
                    }
                    head.set(h);
                }
            });
        }

        Connection takeConnection() {
            return takeConnectionBlock.execute(new AtomicClosure<Connection>() {
                @Override
                public Connection execute(Transaction tx) throws Exception {
                    if (size.get() == 0) {
                        retry();
                    }

                    size.increment();

                    Node<Connection> current = head.get();
                    head.set(current.next);
                    return current.item;
                }
            });
        }

        void returnConnection(final Connection c) {
            returnConnectionBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    size.increment();

                    Node<Connection> oldHead = head.get();
                    head.set(new Node<Connection>(oldHead, c));
                }
            });
        }

        int size() {
            return sizeBlock.execute(new AtomicIntClosure() {
                @Override
                public int execute(Transaction tx) throws Exception {
                    return size.get();
                }
            });
        }
    }

    static class Node<E> {
        final Node<E> next;
        final E item;

        Node(Node<E> next, E item) {
            this.next = next;
            this.item = item;
        }
    }

    static class Connection {

        AtomicInteger users = new AtomicInteger();

        void startUsing() {
            if (!users.compareAndSet(0, 1)) {
                fail();
            }
        }

        void stopUsing() {
            if (!users.compareAndSet(1, 0)) {
                fail();
            }
        }
    }

    private WorkerThread[] createThreads() {
        WorkerThread[] threads = new WorkerThread[threadCount];
        for (int k = 0; k < threads.length; k++) {
            threads[k] = new WorkerThread(k);
        }
        return threads;
    }

    class WorkerThread extends TestThread {

        public WorkerThread(int id) {
            super("WorkerThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            int k = 0;
            while (!stop) {
                if (k % 100 == 0) {
                    System.out.printf("%s is at %s\n", getName(), k);
                }

                Connection c = pool.takeConnection();
                assertNotNull(c);
                c.startUsing();

                try {
                    sleepRandomMs(50);
                } finally {
                    c.stopUsing();
                    pool.returnConnection(c);
                }
                k++;
            }
        }
    }
}