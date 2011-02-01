package org.multiverse.stms.gamma.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.TestThread;
import org.multiverse.stms.gamma.GammaConstants;
import org.multiverse.stms.gamma.GammaStm;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.multiverse.TestUtils.*;

public class GammaLongRef_consistentLoadStressTest implements GammaConstants {

    private GammaStm stm;
    private volatile boolean stop;
    private GammaLongRef ref;
    private final AtomicLong inconsistencyCount = new AtomicLong();

    @Before
    public void setUp() {
        stm = new GammaStm();
        stop = false;
        ref = new GammaLongRef(stm, AbstractGammaRef.VERSION_UNCOMMITTED + 1);
    }

    @Test
    public void test() {
        int readThreadCount = 10;
        ReadThread[] readThreads = new ReadThread[readThreadCount];
        for (int k = 0; k < readThreads.length; k++) {
            readThreads[k] = new ReadThread(k);
        }

        int writerCount = 2;
        UpdateThread[] updateThreads = new UpdateThread[writerCount];
        for (int k = 0; k < updateThreads.length; k++) {
            updateThreads[k] = new UpdateThread(k);
        }

        startAll(readThreads);
        startAll(updateThreads);
        sleepMs(30 * 1000);
        stop = true;
        joinAll(readThreads);
        joinAll(writerCount);
        assertEquals(0, inconsistencyCount.get());
    }

    class ReadThread extends TestThread {
        public ReadThread(int id) {
            super("ReadThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            GammaRefTranlocal tranlocal = new GammaRefTranlocal();
            int k = 0;
            while (!stop) {
                boolean success = ref.load(tranlocal, LOCKMODE_NONE, 100, true);
                if (success) {
                    if (tranlocal.version != tranlocal.long_value) {
                        inconsistencyCount.incrementAndGet();
                        System.out.printf("Inconsistency detected, version %s and value %s\n",tranlocal.version, tranlocal.long_value);
                    }
                    ref.departAfterReading();
                }
                k++;
                if(k% 100000 == 0){
                    System.out.printf("%s is at %s\n",getName(),k);
                }
            }
        }
    }

    class UpdateThread extends TestThread {


        public UpdateThread(int id) {
            super("UpdateThread-" + id);
        }

        @Override
        public void doRun() throws Exception {
            int k=0;
            while (!stop) {
                int arriveStatus = ref.tryLockAndArrive(1, LOCKMODE_EXCLUSIVE);
                if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
                    continue;
                }

                if (arriveStatus == ARRIVE_UNREGISTERED) {
                    throw new RuntimeException();
                }

                ref.long_value = ref.version+1;
                ref.version = ref.version+1;
                ref.departAfterUpdateAndUnlock();

                k++;
                if(k% 100000 == 0){
                    System.out.printf("%s is at %s\n",getName(),k);
                }
            }
        }
    }
}
