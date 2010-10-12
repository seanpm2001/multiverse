package org.multiverse.stms.beta.transactionalobjects;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.multiverse.api.exceptions.ReadWriteConflict;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.BetaStmConstants;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.junit.Assert.*;
import static org.multiverse.TestUtils.assertIsAborted;
import static org.multiverse.TestUtils.assertIsActive;
import static org.multiverse.stms.beta.BetaStmTestUtils.*;

public class LongTranlocal_openForReadTest implements BetaStmConstants {
    private BetaStm stm;

    @Before
    public void setUp() {
        stm = new BetaStm();
    }

    @Test
    public void selfUpgrade_whenNoLockAndUpgradeToNone() {
        self_upgrade(LOCKMODE_NONE, LOCKMODE_NONE, LOCKMODE_NONE);
    }

    @Test
    public void selfUpgrade_whenNoLockAndUpgradeToEnsure() {
        self_upgrade(LOCKMODE_NONE, LOCKMODE_UPDATE, LOCKMODE_UPDATE);
    }

    @Test
    public void selfUpgrade_whenNoLockAndUpgradeToPrivatize() {
        self_upgrade(LOCKMODE_NONE, LOCKMODE_COMMIT, LOCKMODE_COMMIT);
    }

    @Test
    public void selfUpgrade_whenEnsuredAndUpgradeToNone() {
        self_upgrade(LOCKMODE_UPDATE, LOCKMODE_NONE, LOCKMODE_UPDATE);
    }

    @Test
    public void selfUpgrade_whenEnsuredAndUpgradeToEnsure() {
        self_upgrade(LOCKMODE_UPDATE, LOCKMODE_UPDATE, LOCKMODE_UPDATE);
    }

    @Test
    public void selfUpgrade_whenEnsuredAndUpgradeToPrivatize() {
        self_upgrade(LOCKMODE_UPDATE, LOCKMODE_COMMIT, LOCKMODE_COMMIT);
    }

    @Test
    public void selfUpgrade_whenPrivatizedAndUpgradeToNone() {
        self_upgrade(LOCKMODE_COMMIT, LOCKMODE_NONE, LOCKMODE_COMMIT);
    }

    @Test
    public void selfUpgrade_whenPrivatizeAndUpgradeToEnsure() {
        self_upgrade(LOCKMODE_COMMIT, LOCKMODE_UPDATE, LOCKMODE_COMMIT);
    }

    @Test
    public void selfUpgrade_whenNoPrivatizeAndUpgradeToPrivatize() {
        self_upgrade(LOCKMODE_COMMIT, LOCKMODE_COMMIT, LOCKMODE_COMMIT);
    }

    public void self_upgrade(int firstTimeLockMode, int secondTimeLockMode, int expected) {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);

        tranlocal.openForRead(firstTimeLockMode);
        tranlocal.openForRead(secondTimeLockMode);

        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertIsActive(tx);
        assertRefLockMode(ref, tx, expected);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void locking_whenEnsuredByOther_thenSuccess() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.ensure(otherTx);

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);

        tranlocal.openForRead(LOCKMODE_NONE);

        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertIsActive(tx);
        assertRefHasUpdateLock(ref, otherTx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void locking_whenPrivatizedByOther_thenReadWriteConflict() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction otherTx = stm.startDefaultTransaction();
        ref.privatize(otherTx);

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);

        try {
            tranlocal.openForRead(LOCKMODE_NONE);
            fail();
        } catch (ReadWriteConflict expected) {

        }

        assertIsAborted(tx);
        assertRefHasCommitLock(ref, otherTx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void locking_whenEnsuredBySelf() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);

        tranlocal.openForRead(LOCKMODE_UPDATE);

        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertIsActive(tx);
        assertRefHasUpdateLock(ref, tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void locking_whenPrivatizedBySelf() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);

        tranlocal.openForRead(LOCKMODE_COMMIT);

        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertIsActive(tx);
        assertRefHasCommitLock(ref, tx);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenNotOpenedBefore() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);
        tranlocal.openForRead(LOCKMODE_NONE);

        assertRefHasNoLocks(ref);
        assertTrue(tranlocal.isReadonly());
        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertEquals(initialValue, tranlocal.oldValue);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyOpenedForRead() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);
        tranlocal.openForRead(LOCKMODE_NONE);
        tranlocal.openForRead(LOCKMODE_NONE);

        assertRefHasNoLocks(ref);
        assertTrue(tranlocal.isReadonly());
        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertEquals(initialValue, tranlocal.oldValue);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    public void whenAlreadyOpenedForWrite() {
        long initialValue = 10;
        BetaLongRef ref = new BetaLongRef(stm, initialValue);
        long initialVersion = ref.getVersion();

        BetaTransaction tx = stm.startDefaultTransaction();
        LongRefTranlocal tranlocal = tx.open(ref);
        tranlocal.openForWrite(LOCKMODE_NONE);
        tranlocal.openForRead(LOCKMODE_NONE);

        assertRefHasNoLocks(ref);
        assertFalse(tranlocal.isReadonly());
        assertHasVersionAndValue(tranlocal, initialVersion, initialValue);
        assertEquals(initialValue, tranlocal.oldValue);
        assertVersionAndValue(ref, initialVersion, initialValue);
    }

    @Test
    @Ignore
    public void whenAlreadyOpenedForConstruction() {
        /*
        BetaTransaction tx = stm.startDefaultTransaction();
        BetaLongRef ref = new BetaLongRef(tx);

        long initialVersion = ref.getVersion();


        LongRefTranlocal tranlocal = tx.open(ref);
        tranlocal.openForConstruction();
        tranlocal.openForRead(LOCKMODE_NONE);

        assertFalse(tranlocal.isCommitted);
        assertEquals(initialVersion, tranlocal.version);
        assertEquals(initialValue, tranlocal.value);
        assertEquals(initialValue, tranlocal.oldValue);
        assertVersionAndValue(ref, initialVersion, initialValue);
        */
    }

    @Test
    @Ignore
    public void state_whenTransactionAborted() {

    }

    @Test
    @Ignore
    public void state_whenTransactionPrepared() {

    }

    @Test
    @Ignore
    public void state_whenTransactionCommitted() {

    }

}