package org.multiverse.transactional.collections;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Stm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

public class TransactionalLinkedList_constructorTest {

    private Stm stm;

    @Before
    public void setUp() {
        stm = getGlobalStmInstance();
        clearThreadLocalTransaction();
    }

    @Test
    public void testNoArgConstructor() {
        long version = stm.getVersion();
        TransactionalLinkedList<String> list = new TransactionalLinkedList<String>();

        assertEquals(0, list.size());
        assertEquals(version, stm.getVersion());
        assertEquals(Integer.MAX_VALUE, list.getMaxCapacity());
        assertEquals("[]", list.toString());
    }

    @Test
    public void constructorWithNegativeMaxCapacity() {
        long version = stm.getVersion();

        try {
            new TransactionalLinkedList(-1);
            fail();
        } catch (IllegalArgumentException ignore) {

        }

        assertEquals(version, stm.getVersion());
    }

    @Test
    public void constructorWithMaxCapacity() {
        long version = stm.getVersion();

        TransactionalLinkedList<String> list = new TransactionalLinkedList<String>(10);

        assertEquals(version, stm.getVersion());
        assertEquals(10, list.getMaxCapacity());
        assertEquals("[]", list.toString());
    }
}

