package org.multiverse.stms.beta.integrationtest.composability;

import org.junit.Before;
import org.junit.Test;

import static org.multiverse.api.ThreadLocalTransaction.clearThreadLocalTransaction;

public class ComposabilityAndBlockingTest {

    @Before
    public void setUp() {
        clearThreadLocalTransaction();
    }

    @Test
    public void test(){

    }
}