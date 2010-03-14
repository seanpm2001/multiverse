package org.multiverse.transactional.collections;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.Stm;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;

public class TransactionalArrayList_removeAllTest {
    private Stm stm;

    @Before
    public void setUp() {
        stm = getGlobalStmInstance();
    }

    @Test
    public void whenCollectionNull_thenNullPointerException() {
        TransactionalArrayList<String> list = new TransactionalArrayList<String>();

        long version = stm.getVersion();

        try {
            list.removeAll(null);
            fail();
        } catch (NullPointerException expected) {
        }

        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenCollectionEmpty() {
        TransactionalArrayList<String> list = new TransactionalArrayList<String>();

        long version = stm.getVersion();
        boolean changed = list.removeAll(new HashSet<String>());

        assertFalse(changed);
        assertEquals(version, stm.getVersion());
    }

    @Test
    public void whenNoMatchingElements() {
        TransactionalArrayList<String> list = new TransactionalArrayList<String>("a", "b", "c");

        Set<String> items = new HashSet<String>();
        items.add("d");
        items.add("e");

        long version = stm.getVersion();
        boolean changed = list.removeAll(items);

        assertFalse(changed);
        assertEquals(version, stm.getVersion());
    }

    @Test
    @Ignore
    public void test() {
    }
}
