package org.multiverse.transactional.collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.Stm;

import static org.junit.Assert.assertEquals;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

public class TransactionalArrayList_hashCodeTest {

    private Stm stm;

    @Before
    public void setUp() {
        stm = getGlobalStmInstance();
        clearThreadLocalTransaction();
    }

    @After
    public void tearDown() {
        clearThreadLocalTransaction();
    }

    @Test
    public void testEqualListsGiveSameHash() {
        TransactionalArrayList<String> list1 = new TransactionalArrayList<String>("foo", "bar");
        TransactionalArrayList<String> list2 = new TransactionalArrayList<String>("foo", "bar");

        assertEquals(list1.hashCode(), list2.hashCode());
    }
}
