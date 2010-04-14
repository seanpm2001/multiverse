package org.multiverse.integrationtests;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.annotations.TransactionalMethod;
import org.multiverse.annotations.TransactionalObject;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.multiverse.TestUtils.*;

public class ObjectGranularityThread {

    public AtomicInteger executedCounter;

    @Before
    public void setUp() {
        executedCounter = new AtomicInteger();
    }

    @Test
    public void testFieldGranularityCausesNoWriteConflicts() {
        Pair pair = new Pair(0, 0);
        SetLeftThread leftThread = new SetLeftThread(pair);
        SetRightThread rightThread = new SetRightThread(pair);

        startAll(leftThread, rightThread);
        joinAll(leftThread, rightThread);

        assertEquals(10, pair.getLeft());
        assertEquals(10, pair.getRight());

        assertEquals(3, executedCounter.get());
    }

    class SetLeftThread extends TestThread {

        final Pair pair;

        SetLeftThread(Pair pair) {
            super("SetLeftThread");
            this.pair = pair;
        }

        @Override
        @TransactionalMethod
        public void doRun() throws Exception {
            pair.setLeft(10);
            sleepMs(1000);

            executedCounter.incrementAndGet();
        }
    }

    class SetRightThread extends TestThread {

        final Pair pair;

        SetRightThread(Pair pair) {
            super("SetRightThread");
            this.pair = pair;
        }

        @Override
        @TransactionalMethod
        public void doRun() throws Exception {
            pair.setRight(10);
            sleepMs(1000);

            executedCounter.incrementAndGet();
        }
    }

    @TransactionalObject
    public static class Pair {

        private int left;
        private int right;

        public Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }

        public int getLeft() {
            return left;
        }

        public void setLeft(int left) {
            this.left = left;
        }

        public int getRight() {
            return right;
        }

        public void setRight(int right) {
            this.right = right;
        }
    }
}
