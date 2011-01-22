package org.multiverse.stms.gamma.transactions.lean;

import org.junit.Before;
import org.junit.Test;
import org.multiverse.api.LockMode;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.PreparedTransactionException;
import org.multiverse.api.exceptions.ReadWriteConflict;
import org.multiverse.api.exceptions.SpeculativeConfigurationError;
import org.multiverse.stms.gamma.GammaConstants;
import org.multiverse.stms.gamma.GammaStm;
import org.multiverse.stms.gamma.transactionalobjects.*;
import org.multiverse.stms.gamma.transactions.GammaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.*;
import static org.multiverse.stms.gamma.GammaTestUtils.*;

public abstract class LeanGammaTransaction_openForReadTest<T extends GammaTransaction> implements GammaConstants {

    protected GammaStm stm;

    public abstract T newTransaction();

    @Before
    public void setUp() {
        stm = new GammaStm();
    }

    @Test
    public void whenExplicitLocking_thenSpeculativeConfigurationFailure() {
        whenExplicitLocking(LockMode.Read);
        whenExplicitLocking(LockMode.Write);
        whenExplicitLocking(LockMode.Exclusive);
    }

    public void whenExplicitLocking(LockMode lockMode) {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        try {
            ref.openForRead(tx, lockMode.asInt());
            fail();
        } catch (SpeculativeConfigurationError expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertSurplus(ref, 0);
        assertReadonlyCount(ref, 0);
        assertUpdateBiased(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertTrue(tx.config.speculativeConfiguration.get().areLocksRequired);
    }

    @Test
    public void whenIntRef_thenSpeculativeConfigurationError() {
        int initialValue = 10;
        GammaIntRef ref = new GammaIntRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (SpeculativeConfigurationError expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSpeculativeConfigurationNonRefTypeRequired(tx);
    }

    @Test
    public void whenBooleanRef_thenSpeculativeConfigurationError() {
        boolean initialValue = true;
        GammaBooleanRef ref = new GammaBooleanRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (SpeculativeConfigurationError expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSpeculativeConfigurationNonRefTypeRequired(tx);
    }

    @Test
    public void whenDoubleRef_thenSpeculativeConfigurationError() {
        double initialValue = 10;
        GammaDoubleRef ref = new GammaDoubleRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (SpeculativeConfigurationError expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSpeculativeConfigurationNonRefTypeRequired(tx);
    }

    @Test
    public void whenLongRef_thenSpeculativeConfigurationError() {
        long initialValue = 10;
        GammaLongRef ref = new GammaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (SpeculativeConfigurationError expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
        assertSpeculativeConfigurationNonRefTypeRequired(tx);
    }


    @Test
    public void whenExclusiveLockObtainedByOthers() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        GammaTransaction otherTx = stm.startDefaultTransaction();
        ref.getLock().acquire(otherTx, LockMode.Exclusive);

        T tx = newTransaction();
        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (ReadWriteConflict expected) {

        }

        assertIsAborted(tx);
        assertRefHasExclusiveLock(ref, otherTx);
        assertSurplus(ref, 1);
        assertUpdateBiased(ref);
        assertReadonlyCount(ref, 0);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenNonExclusiveLockAcquiredByOthers() {
        whenNonExclusiveLockAcquiredByOthers(LockMode.Read);
        whenNonExclusiveLockAcquiredByOthers(LockMode.Write);
    }

    public void whenNonExclusiveLockAcquiredByOthers(LockMode lockMode) {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        GammaTransaction otherTx = stm.startDefaultTransaction();
        ref.getLock().acquire(otherTx, lockMode);

        T tx = newTransaction();
        GammaRefTranlocal tranlocal = ref.openForRead(tx, LOCKMODE_NONE);

        assertIsActive(tx);
        assertNotNull(tranlocal);
        assertSame(ref, tranlocal.owner);
        assertSame(initialValue, tranlocal.ref_value);
        assertNull(tranlocal.ref_oldValue);
        assertEquals(LOCKMODE_NONE, tranlocal.lockMode);
        assertEquals(TRANLOCAL_READ, tranlocal.mode);
        assertFalse(tranlocal.hasDepartObligation);
        assertEquals(initialVersion, tranlocal.version);

        assertRefHasLockMode(ref, otherTx, lockMode.asInt());
        assertSurplus(ref, 1);
        assertUpdateBiased(ref);
        assertReadonlyCount(ref, 0);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenNotOpenedBefore() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        GammaTransaction tx = newTransaction();
        GammaRefTranlocal tranlocal = ref.openForRead(tx, LOCKMODE_NONE);

        assertNotNull(tranlocal);
        assertSame(ref, tranlocal.owner);
        assertEquals(LOCKMODE_NONE, tranlocal.lockMode);
        assertEquals(TRANLOCAL_READ, tranlocal.mode);
        assertSame(initialValue, tranlocal.ref_value);
        assertNull(tranlocal.ref_oldValue);
        assertEquals(initialVersion, tranlocal.version);

        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyOpenedForRead() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        GammaTransaction tx = newTransaction();
        GammaRefTranlocal first = ref.openForRead(tx, LOCKMODE_NONE);
        GammaRefTranlocal tranlocal = ref.openForRead(tx, LOCKMODE_NONE);

        assertSame(first, tranlocal);
        assertNotNull(tranlocal);
        assertSame(ref, tranlocal.owner);
        assertEquals(LOCKMODE_NONE, tranlocal.lockMode);
        assertEquals(TRANLOCAL_READ, tranlocal.mode);
        assertSame(initialValue, tranlocal.ref_value);
        assertNull(tranlocal.ref_oldValue);
        assertEquals(initialVersion, tranlocal.version);

        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyOpenedForWrite() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        GammaTransaction tx = newTransaction();
        GammaRefTranlocal first = ref.openForWrite(tx, LOCKMODE_NONE);
        GammaRefTranlocal tranlocal = ref.openForRead(tx, LOCKMODE_NONE);

        assertSame(first, tranlocal);
        assertNotNull(tranlocal);
        assertSame(ref, tranlocal.owner);
        assertEquals(LOCKMODE_NONE, tranlocal.lockMode);
        assertEquals(TRANLOCAL_WRITE, tranlocal.mode);
        assertSame(initialValue, tranlocal.ref_value);
        assertNull(tranlocal.ref_oldValue);
        assertEquals(initialVersion, tranlocal.version);

        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyPreparedAndunused() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        tx.prepare();

        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (PreparedTransactionException expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyCommitted() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        tx.commit();

        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsCommitted(tx);
        assertRefHasNoLocks(ref);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyAborted() {
        String initialValue = "foo";
        GammaRef<String> ref = new GammaRef<String>(stm, initialValue);
        long initialVersion = ref.getVersion();

        T tx = newTransaction();
        tx.abort();

        try {
            ref.openForRead(tx, LOCKMODE_NONE);
            fail();
        } catch (DeadTransactionException expected) {
        }

        assertIsAborted(tx);
        assertRefHasNoLocks(ref);
        assertIsAborted(tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }
}