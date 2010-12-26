package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.functions.Functions;
import org.multiverse.api.functions.LongFunction;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmConstants;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.multiverse.TestUtils.assertHasCommutingFunctions;
import static org.multiverse.stms.beta.BetaStmTestUtils.newLongRef;

public class LongRefTranlocal_addCommutingFunctionTest implements BetaStmConstants {

    private BetaObjectPool pool;
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
        pool = new BetaObjectPool();
    }

    @Test
    public void whenFirstAddition() {
        BetaLongRef ref = newLongRef(stm);
        BetaLongRefTranlocal tranlocal = ref.___newTranlocal();
        tranlocal.setStatus(STATUS_COMMUTING);
        tranlocal.addCommutingFunction(Functions.newIncLongFunction(1), pool);

        assertFalse(tranlocal.isReadonly());
        assertTrue(tranlocal.isCommuting());
        assertEquals(0, tranlocal.value);
        assertHasCommutingFunctions(tranlocal, Functions.newIncLongFunction(1));
    }

    @Test
    public void whenMultipleAdditions() {
        BetaLongRef ref = newLongRef(stm);
        BetaLongRefTranlocal tranlocal = ref.___newTranlocal();
        tranlocal.setStatus(STATUS_COMMUTING);

        LongFunction function1 = mock(LongFunction.class);
        LongFunction function2 = mock(LongFunction.class);
        LongFunction function3 = mock(LongFunction.class);

        tranlocal.addCommutingFunction(function1, pool);
        tranlocal.addCommutingFunction(function2, pool);
        tranlocal.addCommutingFunction(function3, pool);

        assertFalse(tranlocal.isReadonly());
        assertTrue(tranlocal.isCommuting());
        assertEquals(0, tranlocal.value);
        assertHasCommutingFunctions(tranlocal, function3, function2, function1);
    }
}