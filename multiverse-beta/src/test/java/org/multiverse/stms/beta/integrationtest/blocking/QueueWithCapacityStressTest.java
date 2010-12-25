package org.multiverse.stms.beta.integrationtest.blocking;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.api.AtomicBlock;
import org.multiverse.api.Transaction;
import org.multiverse.api.closures.AtomicClosure;
import org.multiverse.api.closures.AtomicVoidClosure;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmConstants;
import org.multiverse.stms.beta.transactionalobjects.BetaIntRef;
import org.multiverse.stms.beta.transactionalobjects.BetaIntRefTranlocal;
import org.multiverse.stms.beta.transactionalobjects.BetaRef;
import org.multiverse.stms.beta.transactionalobjects.BetaRefTranlocal;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.multiverse.TestUtils.joinAll;
import static org.multiverse.TestUtils.startAll;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.StmUtils.retry;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;
import static org.multiverse.stms.beta.BetaStmTestUtils.newIntRef;
import static org.multiverse.stms.beta.BetaStmTestUtils.newRef;

public class QueueWithCapacityStressTest implements BetaStmConstants {

    private boolean pessimistic;

    private BetaStm stm;
    private Queue<Integer> queue;
    private int itemCount = 2 * 1000 * 1000;
    private int maxCapacity = 1000;

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
        stm = (BetaStm) getGlobalStmInstance();
        queue = new Queue<Integer>();
    }

    @Test
    public void testPessimistic() {
        test(true);
    }

    @Test
    public void testOptimistic() {
        test(false);
    }

    public void test(boolean pessimistic) {
        this.pessimistic = pessimistic;

        ProduceThread produceThread = new ProduceThread();
        ConsumeThread consumeThread = new ConsumeThread();

        startAll(produceThread, consumeThread);
        joinAll(produceThread, consumeThread);

        assertEquals(itemCount, produceThread.producedItems.size());
        assertEquals(produceThread.producedItems, consumeThread.consumedItems);
    }

    class ConsumeThread extends TestThread {

        private final LinkedList<Integer> consumedItems = new LinkedList<Integer>();

        public ConsumeThread() {
            super("ConsumeThread");
        }

        @Override
        public void doRun() throws Exception {
            for (int k = 0; k < itemCount; k++) {
                int item = queue.pop();
                consumedItems.add(item);

                if (k % 100000 == 0) {
                    System.out.printf("%s is at %s\n", getName(), k);
                }
            }
        }
    }

    class ProduceThread extends TestThread {

        private final LinkedList<Integer> producedItems = new LinkedList<Integer>();

        public ProduceThread() {
            super("ProduceThread");
        }

        @Override
        public void doRun() throws Exception {
            for (int k = 0; k < itemCount; k++) {
                queue.push(k);
                producedItems.add(k);

                if (k % 100000 == 0) {
                    System.out.printf("%s is at %s\n", getName(), k);
                }
            }
        }
    }

    class Queue<E> {
        final Stack<E> pushedStack = new Stack<E>();
        final Stack<E> readyToPopStack = new Stack<E>();
        final AtomicBlock pushBlock = stm.createTransactionFactoryBuilder().buildAtomicBlock();
        final AtomicBlock popBlock = stm.createTransactionFactoryBuilder().buildAtomicBlock();
        final BetaIntRef size = newIntRef(stm);

        public void push(final E item) {
            pushBlock.execute(new AtomicVoidClosure() {
                @Override
                public void execute(Transaction tx) throws Exception {
                    if (size.get() >= maxCapacity) {
                        retry();
                    }

                    BetaTransaction btx = (BetaTransaction) tx;
                    size.incrementAndGet(1);
                    pushedStack.push(btx, item);
                }
            });
        }

        public E pop() {
            return popBlock.execute(new AtomicClosure<E>() {
                @Override
                public E execute(Transaction tx) throws Exception {
                    BetaTransaction btx = (BetaTransaction) tx;

                    BetaIntRefTranlocal sizeTranlocal = btx.openForWrite(size, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);

                    if (!readyToPopStack.isEmpty(btx)) {
                        sizeTranlocal.value--;
                        return readyToPopStack.pop(btx);
                    }

                    while (!pushedStack.isEmpty(btx)) {
                        E item = pushedStack.pop(btx);
                        readyToPopStack.push(btx, item);
                    }

                    if (!readyToPopStack.isEmpty(btx)) {
                        sizeTranlocal.value--;
                        return readyToPopStack.pop(btx);
                    }

                    retry();
                    return null;
                }
            });
        }
    }

    class Stack<E> {
        final BetaRef<Node<E>> head = newRef(stm);

        void push(BetaTransaction tx, E item) {
            BetaRefTranlocal<Node<E>> headTranlocal = tx.openForWrite(head, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
            headTranlocal.value = new Node<E>(item, headTranlocal.value);
        }

        boolean isEmpty(BetaTransaction tx) {
            BetaRefTranlocal<Node<E>> headTranlocal = tx.openForRead(head, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
            return headTranlocal.value == null;
        }

        E pop(BetaTransaction tx) {
            BetaRefTranlocal<Node<E>> headTranlocal = tx.openForWrite(head, pessimistic ? LOCKMODE_COMMIT : LOCKMODE_NONE);
            Node<E> node = headTranlocal.value;

            if (node == null) {
                retry();
            }

            headTranlocal.value = node.next;
            return node.item;
        }
    }

    class Node<E> {
        final E item;
        Node<E> next;

        Node(E item, Node<E> next) {
            this.item = item;
            this.next = next;
        }
    }
}
