package org.multiverse.stms.gamma.transactionalobjects;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.exceptions.PanicError;
import org.multiverse.stms.gamma.GammaStm;

import static org.junit.Assert.fail;
import static org.multiverse.stms.gamma.GammaTestUtils.*;

public class Orec_departAfterFailureTest {

    private GammaStm stm;

    @Before
    public void setUp() {
        stm = new GammaStm();
    }

    @Test
    public void whenUpdateBiasedAndNoSurplusAndNotLocked_thenPanicError() {
        AbstractGammaObject orec = new GammaLongRef(stm);

        try {
            orec.departAfterFailure();
            fail();
        } catch (PanicError expected) {
        }

        assertSurplus(orec, 0);
        assertUpdateBiased(orec);
        assertLockMode(orec, LOCKMODE_NONE);
        assertReadonlyCount(0, orec);
    }

    @Test
    public void whenUpdateBiasedAndSurplusAndNotLocked() {
        AbstractGammaObject orec = new GammaLongRef(stm);
        orec.arrive(1);

        orec.departAfterFailure();

        assertSurplus(orec, 0);
        assertUpdateBiased(orec);
        assertLockMode(orec, LOCKMODE_NONE);
        assertReadonlyCount(0, orec);
    }

    @Test
    public void whenUpdateBiasedAndSurplusAndLockedForCommit() {
        AbstractGammaObject orec = new GammaLongRef(stm);
        orec.arrive(1);
        orec.arrive(1);
        orec.tryLockAfterNormalArrive(1, LOCKMODE_COMMIT);

        orec.departAfterFailure();

        assertSurplus(orec, 1);
        assertUpdateBiased(orec);
        assertLockMode(orec, LOCKMODE_COMMIT);
        assertReadonlyCount(0, orec);
    }

    @Test
    public void whenUpdateBiasedAndSurplusAndLockedForUpdate() {
        AbstractGammaObject orec = new GammaLongRef(stm);
        orec.arrive(1);
        orec.arrive(1);
        orec.tryLockAfterNormalArrive(1, LOCKMODE_WRITE);

        orec.departAfterFailure();

        assertSurplus(orec, 1);
        assertUpdateBiased(orec);
        assertLockMode(orec, LOCKMODE_WRITE);
        assertReadonlyCount(0, orec);
    }


    @Test
    public void whenReadBiasedAndLockedForCommit_thenPanicError() {
        AbstractGammaObject orec = makeReadBiased(new GammaLongRef(stm));

        orec.tryLockAndArrive(1, LOCKMODE_COMMIT);

        try {
            orec.departAfterFailure();
            fail();
        } catch (PanicError expected) {
        }

        assertLockMode(orec, LOCKMODE_COMMIT);
        assertSurplus(orec, 1);
        assertReadBiased(orec);
        assertReadonlyCount(0, orec);
    }

    @Test
    public void whenReadBiasedAndNotLocked_thenPanicError() {
        AbstractGammaObject orec = makeReadBiased(new GammaLongRef(stm));

        try {
            orec.departAfterFailure();
            fail();
        } catch (PanicError expected) {
        }

        assertLockMode(orec, LOCKMODE_NONE);
        assertSurplus(orec, 0);
        assertReadBiased(orec);
        assertReadonlyCount(0, orec);
    }
}