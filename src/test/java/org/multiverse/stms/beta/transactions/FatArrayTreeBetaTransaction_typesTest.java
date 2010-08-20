package org.multiverse.stms.beta.transactions;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.transactionalobjects.*;

import static org.junit.Assert.assertEquals;
import static org.multiverse.stms.beta.BetaStmUtils.*;

public class FatArrayTreeBetaTransaction_typesTest {

    private BetaStm stm;
    private BetaObjectPool pool;

    @Before
    public void setUp() {
        stm = new BetaStm();
        pool = new BetaObjectPool();
    }

    @Test
    public void whenIntRefUsed() {
        IntRef ref = createIntRef(stm, 100);

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        IntRefTranlocal read = tx.openForRead(ref, false, pool);
        assertEquals(100, read.value);
        IntRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;
        tx.commit();

        assertEquals(101, ref.___unsafeLoad().value);
    }

    @Test
    public void whenLongRefUsed() {
        LongRef ref = createLongRef(stm, 100);

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        LongRefTranlocal read = tx.openForRead(ref, false, pool);
        assertEquals(100, read.value);
        LongRefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value++;
        tx.commit();

        assertEquals(101, ref.___unsafeLoad().value);
    }

    @Test
    public void whenRefUsed() {
        Ref<String> ref = createRef(stm, "peter");

        FatArrayTreeBetaTransaction tx = new FatArrayTreeBetaTransaction(stm);
        RefTranlocal read = tx.openForRead(ref, false, pool);
        assertEquals("peter", read.value);
        RefTranlocal write = tx.openForWrite(ref, false, pool);
        write.value = "john";
        tx.commit();

        assertEquals("john", ref.___unsafeLoad().value);
    }

    @Test
    @Ignore
    public void whenCustomTransactionalObjectUsed(){}
}
