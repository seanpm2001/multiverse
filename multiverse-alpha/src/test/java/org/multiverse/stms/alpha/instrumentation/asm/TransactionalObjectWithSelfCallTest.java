package org.multiverse.stms.alpha.instrumentation.asm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.multiverse.transactional.annotations.TransactionalObject;
import org.multiverse.stms.alpha.AlphaStm;

import static org.junit.Assert.assertEquals;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;

/**
 * @author Peter Veentjer
 */
public class TransactionalObjectWithSelfCallTest {

    private AlphaStm stm;

    @Before
    public void setUp() {
        stm = (AlphaStm) getGlobalStmInstance();
    }


    @After
    public void tearDown() {
        //assertNoInstrumentationProblems();
    }

    @Test
    public void test() {
        Value value = new Value(20);
        value.inc();
        assertEquals(21, value.get());
    }


    @TransactionalObject
    static class Value {

        private int value;

        Value(int value) {
            this.value = value;
        }

        public int get() {
            return value;
        }

        public void inc() {
            set(get() + 1);
        }

        public void set(int value) {
            this.value = value;
        }
    }
}
