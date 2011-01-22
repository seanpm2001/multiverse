package org.multiverse.stms.gamma.transactionalobjects;

import org.multiverse.api.IsolationLevel;
import org.multiverse.api.LockMode;
import org.multiverse.api.Transaction;
import org.multiverse.api.exceptions.LockedException;
import org.multiverse.api.exceptions.TodoException;
import org.multiverse.api.exceptions.TransactionRequiredException;
import org.multiverse.api.functions.*;
import org.multiverse.stms.gamma.GammaObjectPool;
import org.multiverse.stms.gamma.GammaStm;
import org.multiverse.stms.gamma.GammaStmUtils;
import org.multiverse.stms.gamma.Listeners;
import org.multiverse.stms.gamma.transactions.GammaTransaction;
import org.multiverse.stms.gamma.transactions.GammaTransactionConfiguration;
import org.multiverse.stms.gamma.transactions.fat.FatFixedLengthGammaTransaction;
import org.multiverse.stms.gamma.transactions.fat.FatMapGammaTransaction;
import org.multiverse.stms.gamma.transactions.fat.FatMonoGammaTransaction;
import org.multiverse.stms.gamma.transactions.lean.LeanFixedLengthGammaTransaction;
import org.multiverse.stms.gamma.transactions.lean.LeanMonoGammaTransaction;

import static org.multiverse.api.ThreadLocalTransaction.getThreadLocalTransaction;
import static org.multiverse.stms.gamma.GammaStmUtils.asGammaTransaction;
import static org.multiverse.stms.gamma.GammaStmUtils.getRequiredThreadLocalGammaTransaction;
import static org.multiverse.stms.gamma.ThreadLocalGammaObjectPool.getThreadLocalGammaObjectPool;

@SuppressWarnings({"OverlyComplexClass", "OverlyCoupledClass"})
public abstract class AbstractGammaRef extends AbstractGammaObject {

    public final int type;
    @SuppressWarnings({"VolatileLongOrDoubleField"})
    public volatile long long_value;
    public volatile Object ref_value;

    protected AbstractGammaRef(GammaStm stm, int type) {
        super(stm);
        this.type = type;
    }

    public final int getType() {
        return type;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public final boolean flattenCommute(final GammaTransaction tx, final GammaRefTranlocal tranlocal, final int lockMode) {
        final GammaTransactionConfiguration config = tx.config;

        if (!load(tranlocal, lockMode, config.spinCount, tx.arriveEnabled)) {
            return false;
        }

        tranlocal.setDirty(!config.dirtyCheck);
        tranlocal.mode = TRANLOCAL_WRITE;

        if (!tx.isReadConsistent(tranlocal)) {
            return false;
        }

        boolean abort = true;
        //evaluatingCommute = true;
        try {
            CallableNode node = tranlocal.headCallable;
            while (node != null) {
                evaluate(tranlocal, node.function);
                CallableNode newNext = node.next;
                tx.pool.putCallableNode(node);
                node = newNext;
            }
            tranlocal.headCallable = null;

            abort = false;
        } finally {
            //evaluatingCommute = false;
            if (abort) {
                tx.abort();
            }
        }

        return true;
    }

    private void evaluate(final GammaRefTranlocal tranlocal, final Function function) {
        switch (type) {
            case TYPE_REF:
                tranlocal.ref_value = function.call(tranlocal.ref_value);
                break;
            case TYPE_INT:
                IntFunction intFunction = (IntFunction) function;
                tranlocal.long_value = intFunction.call((int) tranlocal.long_value);
                break;
            case TYPE_LONG:
                LongFunction longFunction = (LongFunction) function;
                tranlocal.long_value = longFunction.call(tranlocal.long_value);
                break;
            case TYPE_DOUBLE:
                DoubleFunction doubleFunction = (DoubleFunction) function;
                double doubleResult = doubleFunction.call(GammaStmUtils.longAsDouble(tranlocal.long_value));
                tranlocal.long_value = GammaStmUtils.doubleAsLong(doubleResult);
                break;
            case TYPE_BOOLEAN:
                BooleanFunction booleanFunction = (BooleanFunction) function;
                boolean booleanResult = booleanFunction.call(GammaStmUtils.longAsBoolean(tranlocal.long_value));
                tranlocal.long_value = GammaStmUtils.booleanAsLong(booleanResult);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public final Listeners safe(final GammaRefTranlocal tranlocal, final GammaObjectPool pool) {
        if (!tranlocal.isDirty) {
            releaseAfterReading(tranlocal, pool);
            return null;
        }

        if (type == TYPE_REF) {
            ref_value = tranlocal.ref_value;
            //we need to set them to null to prevent memory leaks.
            tranlocal.ref_value = null;
            tranlocal.ref_oldValue = null;
        } else {
            long_value = tranlocal.long_value;
        }

        version = tranlocal.version + 1;

        Listeners listenerAfterWrite = listeners;

        if (listenerAfterWrite != null) {
            listenerAfterWrite = ___removeListenersAfterWrite();
        }

        //todo: content of this method can be inlined here.
        releaseAfterUpdate(tranlocal, pool);
        return listenerAfterWrite;
    }

    public final Listeners leanSafe(final GammaRefTranlocal tranlocal) {
        if (tranlocal.mode == TRANLOCAL_READ) {
            tranlocal.ref_value = null;
            tranlocal.owner = null;
            return null;
        }

        ref_value = tranlocal.ref_value;

        version = tranlocal.version + 1;

        Listeners listenerAfterWrite = listeners;

        if (listenerAfterWrite != null) {
            listenerAfterWrite = ___removeListenersAfterWrite();
        }

        if (tranlocal.hasDepartObligation) {
            departAfterUpdateAndUnlock();
        } else {
            unlockByUnregistered();
        }
        tranlocal.ref_value = null;
        tranlocal.lockMode = LOCKMODE_NONE;
        tranlocal.owner = null;
        tranlocal.hasDepartObligation = false;
        return listenerAfterWrite;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public final boolean prepare(final GammaTransaction tx, final GammaRefTranlocal tranlocal) {
        final int mode = tranlocal.getMode();

        if (mode == TRANLOCAL_CONSTRUCTING) {
            return true;
        }

        if (mode == TRANLOCAL_READ) {
            return !tranlocal.writeSkewCheck
                    || tryLockAndCheckConflict(tx.config.spinCount, tranlocal, LOCKMODE_READ);
        }

        if (mode == TRANLOCAL_COMMUTING) {
            if (!flattenCommute(tx, tranlocal, LOCKMODE_EXCLUSIVE)) {
                return false;
            }
        }

        if (!tranlocal.isDirty()) {
            boolean isDirty;
            if (type == TYPE_REF) {
                //noinspection ObjectEquality
                isDirty = tranlocal.ref_value != tranlocal.ref_oldValue;
            } else {
                isDirty = tranlocal.long_value != tranlocal.long_oldValue;
            }

            if (!isDirty) {
                return !tranlocal.writeSkewCheck ||
                        tryLockAndCheckConflict(tx.config.spinCount, tranlocal, LOCKMODE_READ);
            }

            tranlocal.setDirty(true);
        }

        return tryLockAndCheckConflict(tx.config.spinCount, tranlocal, LOCKMODE_EXCLUSIVE);
    }

    public final void releaseAfterFailure(final GammaRefTranlocal tranlocal, final GammaObjectPool pool) {
        if (type == TYPE_REF) {
            tranlocal.ref_value = null;
            tranlocal.ref_oldValue = null;
        }

        if (tranlocal.headCallable != null) {
            CallableNode node = tranlocal.headCallable;
            do {
                CallableNode next = node.next;
                pool.putCallableNode(node);
                node = next;
            } while (node != null);
            tranlocal.headCallable = null;
        }

        if (tranlocal.hasDepartObligation()) {
            if (tranlocal.getLockMode() != LOCKMODE_NONE) {
                departAfterFailureAndUnlock();
                tranlocal.setLockMode(LOCKMODE_NONE);
            } else {
                departAfterFailure();
            }
            tranlocal.setDepartObligation(false);
        } else if (tranlocal.getLockMode() != LOCKMODE_NONE) {
            unlockByUnregistered();
            tranlocal.setLockMode(LOCKMODE_NONE);
        }

        tranlocal.owner = null;
    }

    public final void releaseAfterUpdate(final GammaRefTranlocal tranlocal, final GammaObjectPool pool) {
        if (type == TYPE_REF) {
            tranlocal.ref_value = null;
            tranlocal.ref_oldValue = null;
        }

        departAfterUpdateAndUnlock();
        tranlocal.setLockMode(LOCKMODE_NONE);
        tranlocal.owner = null;
        tranlocal.setDepartObligation(false);
    }

    public final void releaseAfterReading(final GammaRefTranlocal tranlocal, final GammaObjectPool pool) {
        if (type == TYPE_REF) {
            tranlocal.ref_value = null;
            tranlocal.ref_oldValue = null;
        }

        if (tranlocal.hasDepartObligation()) {
            if (tranlocal.getLockMode() != LOCKMODE_NONE) {
                departAfterReadingAndUnlock();
                tranlocal.setLockMode(LOCKMODE_NONE);
            } else {
                departAfterReading();
            }
            tranlocal.setDepartObligation(false);
        } else if (tranlocal.getLockMode() != LOCKMODE_NONE) {
            unlockByUnregistered();
            tranlocal.setLockMode(LOCKMODE_NONE);
        }

        tranlocal.owner = null;
    }

    public final boolean load(final GammaRefTranlocal tranlocal, final int lockMode, int spinCount, final boolean arriveNeeded) {
        if (lockMode == LOCKMODE_NONE) {
            while (true) {
                //JMM: nothing can jump behind the following statement
                long readLong = 0;
                Object readRef = null;
                if (type == TYPE_REF) {
                    readRef = ref_value;
                } else {
                    readLong = long_value;
                }
                final long readVersion = version;

                //JMM: the read for the arrive can't jump over the read of the active.

                int arriveStatus;
                if (arriveNeeded) {
                    arriveStatus = arrive(spinCount);
                } else {
                    arriveStatus = waitForExclusiveLockToBecomeFree(spinCount) ? ARRIVE_UNREGISTERED : ARRIVE_LOCK_NOT_FREE;
                }

                if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
                    return false;
                }

                //JMM safety:
                //The volatile read of active can't be reordered so that it jump in front of the volatile read of
                //the orec-value when the arrive method is called.
                //An instruction is allowed to jump in front of the write of orec-value, but it is not allowed to
                //jump in front of the read or orec-value (volatile read happens before rule).
                //This means that it isn't possible that a locked value illegally is seen as unlocked.

                if (type == TYPE_REF) {
                    //noinspection ObjectEquality
                    if (readVersion == version && readRef == ref_value) {
                        //at this point we are sure that the read was unlocked.
                        tranlocal.owner = this;
                        tranlocal.version = readVersion;
                        tranlocal.ref_value = readRef;
                        tranlocal.ref_oldValue = readRef;
                        tranlocal.setLockMode(LOCKMODE_NONE);
                        tranlocal.setDepartObligation(arriveStatus == ARRIVE_NORMAL);
                        return true;
                    }
                } else {
                    if (readVersion == version && readLong == long_value) {
                        //at this point we are sure that the read was unlocked.
                        tranlocal.owner = this;
                        tranlocal.version = readVersion;
                        tranlocal.long_value = readLong;
                        tranlocal.long_oldValue = readLong;
                        tranlocal.setLockMode(LOCKMODE_NONE);
                        tranlocal.setDepartObligation(arriveStatus == ARRIVE_NORMAL);
                        return true;
                    }
                }

                //we are not lucky, the value has changed. But before retrying, we need to depart if the arrive was normal
                if (arriveStatus == ARRIVE_NORMAL) {
                    departAfterFailure();
                }
            }
        } else {
            final int arriveStatus = tryLockAndArrive(spinCount, lockMode);

            if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
                return false;
            }

            tranlocal.owner = this;
            tranlocal.version = version;
            if (type == TYPE_REF) {
                final Object v = ref_value;
                tranlocal.ref_value = v;
                tranlocal.ref_oldValue = v;
            } else {
                final long v = long_value;
                tranlocal.long_value = v;
                tranlocal.long_oldValue = v;
            }
            tranlocal.setLockMode(lockMode);
            tranlocal.setDepartObligation(arriveStatus == ARRIVE_NORMAL);
            return true;
        }
    }

    public final boolean leanLoad(final GammaRefTranlocal tranlocal) {
        while (true) {
            //JMM: nothing can jump behind the following statement
            Object readRef = ref_value;
            final long readVersion = version;

            int spinCount = 64;
            for (; ;) {
                if (!hasExclusiveLock()) {
                    break;
                }
                spinCount--;

                if (spinCount < 0) {
                    return false;
                }
            }

            if (readVersion == version && readRef == ref_value) {
                //at this point we are sure that the read was unlocked.
                tranlocal.version = readVersion;
                tranlocal.ref_value = readRef;
                tranlocal.owner = this;
                tranlocal.setLockMode(LOCKMODE_NONE);
                tranlocal.setDepartObligation(false);
                return true;
            }
        }
    }

    @Override
    public final GammaRefTranlocal openForConstruction(GammaTransaction tx) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx instanceof FatMonoGammaTransaction) {
            return openForConstruction((FatMonoGammaTransaction) tx);
        } else if (tx instanceof FatFixedLengthGammaTransaction) {
            return openForConstruction((FatFixedLengthGammaTransaction) tx);
        } else {
            return openForConstruction((FatMapGammaTransaction) tx);
        }
    }

    private void initTranlocalForConstruction(final GammaRefTranlocal tranlocal) {
        tranlocal.isDirty = true;
        tranlocal.mode = TRANLOCAL_CONSTRUCTING;
        tranlocal.setLockMode(LOCKMODE_EXCLUSIVE);
        tranlocal.setDepartObligation(true);
        if (type == TYPE_REF) {
            tranlocal.ref_value = null;
            tranlocal.ref_oldValue = null;
        } else {
            tranlocal.long_value = 0;
            tranlocal.long_oldValue = 0;
        }
    }

    @Override
    public final GammaRefTranlocal openForConstruction(FatMonoGammaTransaction tx) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForConstructionOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForConstructionOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForConstructionOnReadonly(this);
        }

        final GammaRefTranlocal tranlocal = tx.tranlocal;

        //noinspection ObjectEquality
        if (tranlocal.owner == this) {
            if (!tranlocal.isConstructing()) {
                throw tx.abortOpenForConstructionOnBadReference(this);
            }

            return tranlocal;
        }

        if (tranlocal.owner != null) {
            throw tx.abortOnTooSmallSize(2);
        }

        tx.hasWrites = true;
        tranlocal.owner = this;
        initTranlocalForConstruction(tranlocal);
        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForConstruction(FatMapGammaTransaction tx) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForConstructionOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForConstructionOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForConstructionOnReadonly(this);
        }

        final int identityHash = identityHashCode();
        final int indexOf = tx.indexOf(this, identityHash);

        if (indexOf > -1) {
            final GammaRefTranlocal tranlocal = tx.array[indexOf];

            if (!tranlocal.isConstructing()) {
                throw tx.abortOpenForConstructionOnBadReference(this);
            }

            return tranlocal;
        }

        final GammaRefTranlocal tranlocal = tx.pool.take(this);
        tranlocal.owner = this;
        initTranlocalForConstruction(tranlocal);
        tx.hasWrites = true;
        tx.attach(tranlocal, identityHash);
        tx.size++;

        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForConstruction(FatFixedLengthGammaTransaction tx) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForConstructionOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForConstructionOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForConstructionOnReadonly(this);
        }

        GammaRefTranlocal found = null;
        GammaRefTranlocal newNode = null;
        GammaRefTranlocal node = tx.head;
        while (true) {
            if (node == null) {
                break;
            } else if (node.owner == this) {
                found = node;
                break;
            } else if (node.owner == null) {
                newNode = node;
                break;
            } else {
                node = node.next;
            }
        }

        if (found != null) {
            if (!found.isConstructing()) {
                throw tx.abortOpenForConstructionOnBadReference(this);
            }

            tx.shiftInFront(found);
            return found;
        }

        if (newNode == null) {
            throw tx.abortOnTooSmallSize(config.arrayTransactionSize + 1);
        }

        newNode.owner = this;
        initTranlocalForConstruction(newNode);
        tx.size++;
        tx.shiftInFront(newNode);
        tx.hasWrites = true;
        return newNode;
    }
    // ============================================================================================
    // =============================== open for read ==============================================
    // ============================================================================================

    @Override
    public final GammaRefTranlocal openForRead(final GammaTransaction tx, final int lockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx instanceof FatMonoGammaTransaction) {
            return openForRead((FatMonoGammaTransaction) tx, lockMode);
        } else if (tx instanceof FatFixedLengthGammaTransaction) {
            return openForRead((FatFixedLengthGammaTransaction) tx, lockMode);
        } else {
            return openForRead((FatMapGammaTransaction) tx, lockMode);
        }
    }

    private static void initTranlocalForRead(final GammaTransactionConfiguration config, final GammaRefTranlocal tranlocal) {
        tranlocal.isDirty = false;
        tranlocal.mode = TRANLOCAL_READ;
        tranlocal.writeSkewCheck = config.isolationLevel == IsolationLevel.Serializable;
    }

    @Override
    public final GammaRefTranlocal openForRead(final FatMonoGammaTransaction tx, int lockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForReadOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForReadOnBadStm(this);
        }

        lockMode = config.readLockModeAsInt <= lockMode ? lockMode : config.readLockModeAsInt;

        final GammaRefTranlocal tranlocal = tx.tranlocal;

        //noinspection ObjectEquality
        if (tranlocal.owner == this) {
            int mode = tranlocal.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return tranlocal;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, tranlocal, lockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }

                return tranlocal;
            }

            if (lockMode > tranlocal.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, tranlocal, lockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            return tranlocal;
        }

        if (tranlocal.owner != null) {
            throw tx.abortOnTooSmallSize(2);
        }

        initTranlocalForRead(config, tranlocal);
        if (!load(tranlocal, lockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForRead(final FatFixedLengthGammaTransaction tx, int desiredLockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForReadOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForReadOnBadStm(this);
        }

        GammaRefTranlocal found = null;
        GammaRefTranlocal newNode = null;
        GammaRefTranlocal node = tx.head;
        while (true) {
            if (node == null) {
                break;
            } else if (node.owner == this) {
                found = node;
                break;
            } else if (node.owner == null) {
                newNode = node;
                break;
            } else {
                node = node.next;
            }
        }

        desiredLockMode = config.readLockModeAsInt <= desiredLockMode ? desiredLockMode : config.readLockModeAsInt;

        if (found != null) {
            final int mode = found.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return found;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, found, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }

                return found;
            }

            if (desiredLockMode > found.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, found, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            tx.shiftInFront(found);
            return found;
        }

        if (newNode == null) {
            throw tx.abortOnTooSmallSize(config.arrayTransactionSize + 1);
        }

        initTranlocalForRead(config, newNode);
        if (!load(newNode, desiredLockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        tx.size++;
        tx.shiftInFront(newNode);

        if (tx.hasReads) {
            if (!tx.isReadConsistent(newNode)) {
                throw tx.abortOnReadWriteConflict();
            }
        } else {
            tx.hasReads = true;
        }

        return newNode;
    }

    @Override
    public final GammaRefTranlocal openForRead(final FatMapGammaTransaction tx, int desiredLockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForReadOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForReadOnBadStm(this);
        }

        desiredLockMode = config.readLockModeAsInt <= desiredLockMode ? desiredLockMode : config.readLockModeAsInt;

        final int identityHash = identityHashCode();
        final int indexOf = tx.indexOf(this, identityHash);

        if (indexOf > -1) {
            final GammaRefTranlocal tranlocal = tx.array[indexOf];
            final int mode = tranlocal.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return tranlocal;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }

                return tranlocal;
            }

            if (desiredLockMode > tranlocal.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            return tranlocal;
        }

        final GammaRefTranlocal tranlocal = tx.pool.take(this);
        initTranlocalForRead(config, tranlocal);
        tx.attach(tranlocal, identityHash);
        tx.size++;

        if (!load(tranlocal, desiredLockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        if (tx.hasReads) {
            if (!tx.isReadConsistent(tranlocal)) {
                throw tx.abortOnReadWriteConflict();
            }
        } else {
            tx.hasReads = true;
        }

        return tranlocal;
    }

    public final GammaRefTranlocal openForRead(final GammaTransaction tx) {
        if (tx instanceof LeanFixedLengthGammaTransaction) {
            return openForRead((LeanFixedLengthGammaTransaction) tx);
        } else {
            return openForRead((LeanMonoGammaTransaction) tx);
        }
    }

    public final GammaRefTranlocal openForRead(final LeanFixedLengthGammaTransaction tx) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForReadOnBadStatus(this);
        }

        if (tx.head.owner == this) {
            return tx.head;
        }

        //look inside the transaction if it already is opened for read or otherwise look for an empty spot to
        //place the read.
        GammaRefTranlocal found = null;
        GammaRefTranlocal newNode = null;
        GammaRefTranlocal node = tx.head;
        while (true) {
            if (node == null) {
                break;
            } else if (node.owner == this) {
                found = node;
                break;
            } else if (node.owner == null) {
                newNode = node;
                break;
            } else {
                node = node.next;
            }
        }

        //we have found it.
        if (found != null) {
            tx.shiftInFront(found);
            return found;
        }

        //we have not found it, but there also is no spot available.
        if (newNode == null) {
            throw tx.abortOnTooSmallSize(tx.config.arrayTransactionSize + 1);
        }

        if (type != TYPE_REF) {
            throw tx.abortOpenForWriteOnNonRefType(this);
        }

        //load it
        newNode.mode = TRANLOCAL_READ;
        newNode.isDirty = false;
        newNode.owner = this;
        while (true) {
            //JMM: nothing can jump behind the following statement
            Object readRef = ref_value;
            final long readVersion = version;

            //wait for the exclusive lock to come available.
            int spinCount = 64;
            for (; ;) {
                if (!hasExclusiveLock()) {
                    break;
                }
                spinCount--;
                if (spinCount < 0) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            //check if the version and value we read are still the same, if they are not, we have read illegal memory,
            //so we are going to try again.
            if (readVersion == version && readRef == ref_value) {
                //at this point we are sure that the read was unlocked.
                newNode.version = readVersion;
                newNode.ref_value = readRef;
                break;
            }
        }

        tx.size++;
        //lets put it in the front it isn't the first one that is opened.
        if (tx.size > 1) {
            tx.shiftInFront(newNode);
        }

        //check if the transaction still is read consistent.
        if (tx.hasReads) {
            node = tx.head;
            do {
                //if we are at the end, we are done.
                final AbstractGammaRef owner = node.owner;

                if (owner == null) {
                    break;
                }

                if (node != newNode && (owner.hasExclusiveLock() || owner.version != node.version)) {
                    throw tx.abortOnReadWriteConflict();
                }

                node = node.next;
            } while (node != null);
        } else {
            tx.hasReads = true;
        }

        //we are done, the load was correct and the transaction still is read consistent.
        return newNode;
    }

    public final GammaRefTranlocal openForRead(final LeanMonoGammaTransaction tx) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForReadOnBadStatus(this);
        }

        final GammaRefTranlocal tranlocal = tx.tranlocal;

        //noinspection ObjectEquality
        if (tranlocal.owner == this) {
            return tranlocal;
        }

        if (tranlocal.owner != null) {
            throw tx.abortOnTooSmallSize(2);
        }

        if (type != TYPE_REF) {
            throw tx.abortOpenForWriteOnNonRefType(this);
        }

        tranlocal.mode = TRANLOCAL_READ;
        tranlocal.owner = this;
        for (; ;) {
            //JMM: nothing can jump behind the following statement
            final Object readRef = ref_value;
            final long readVersion = version;

            //wait for the exclusive lock to come available.
            int spinCount = 64;
            for (; ;) {
                if (!hasExclusiveLock()) {
                    break;
                }
                spinCount--;
                if (spinCount < 0) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            //check if the version and value we read are still the same, if they are not, we have read illegal memory,
            //so we are going to try again.
            if (readVersion == version) {
                //at this point we are sure that the read was unlocked.
                tranlocal.version = readVersion;
                tranlocal.ref_value = readRef;
                break;
            }
        }

        return tranlocal;
    }


    // ============================================================================================
    // =============================== open for write =============================================
    // ============================================================================================

    public final GammaRefTranlocal openForWrite(final GammaTransaction tx) {
        if (tx instanceof LeanMonoGammaTransaction) {
            return openForWrite((LeanMonoGammaTransaction) tx);
        } else {
            return openForWrite((LeanFixedLengthGammaTransaction) tx);
        }
    }

    public final GammaRefTranlocal openForWrite(final LeanMonoGammaTransaction tx) {
        final GammaRefTranlocal tranlocal = openForRead(tx);

        if (!tx.hasWrites) {
            tx.hasWrites = true;
        }

        if (tranlocal.mode == TRANLOCAL_READ) {
            tranlocal.mode = TRANLOCAL_WRITE;
        }

        return tranlocal;
    }

    public final GammaRefTranlocal openForWrite(final LeanFixedLengthGammaTransaction tx) {
        final GammaRefTranlocal tranlocal = openForRead(tx);
        if (!tx.hasWrites) {
            tx.hasWrites = true;
        }

        if (tranlocal.mode == TRANLOCAL_READ) {
            tranlocal.mode = TRANLOCAL_WRITE;
        }

        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForWrite(final GammaTransaction tx, final int lockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx instanceof FatMonoGammaTransaction) {
            return openForWrite((FatMonoGammaTransaction) tx, lockMode);
        } else if (tx instanceof FatFixedLengthGammaTransaction) {
            return openForWrite((FatFixedLengthGammaTransaction) tx, lockMode);
        } else {
            return openForWrite((FatMapGammaTransaction) tx, lockMode);
        }
    }

    private static void initTranlocalForWrite(final GammaTransactionConfiguration config, final GammaRefTranlocal tranlocal) {
        tranlocal.isDirty = !config.dirtyCheck;
        tranlocal.mode = TRANLOCAL_WRITE;
        tranlocal.writeSkewCheck = config.isolationLevel == IsolationLevel.Serializable;
    }

    @Override
    public final GammaRefTranlocal openForWrite(final FatMapGammaTransaction tx, int desiredLockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForWriteOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForWriteOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForWriteOnReadonly(this);
        }

        desiredLockMode = config.writeLockModeAsInt <= desiredLockMode ? desiredLockMode : config.writeLockModeAsInt;

        final int identityHash = identityHashCode();

        final int indexOf = tx.indexOf(this, identityHash);
        if (indexOf > -1) {
            final GammaRefTranlocal tranlocal = tx.array[indexOf];
            final int mode = tranlocal.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return tranlocal;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
                return tranlocal;
            }

            if (desiredLockMode > tranlocal.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            tx.hasWrites = true;
            tranlocal.setDirty(!config.dirtyCheck);
            tranlocal.mode = TRANLOCAL_WRITE;
            return tranlocal;
        }

        final GammaRefTranlocal tranlocal = tx.pool.take(this);
        tx.attach(tranlocal, identityHash);
        tx.size++;
        tx.hasWrites = true;

        initTranlocalForWrite(config, tranlocal);
        if (!load(tranlocal, desiredLockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        if (tx.hasReads) {
            if (!tx.isReadConsistent(tranlocal)) {
                throw tx.abortOnReadWriteConflict();
            }
        } else {
            tx.hasReads = true;
        }

        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForWrite(final FatMonoGammaTransaction tx, int desiredLockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForWriteOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForWriteOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForWriteOnReadonly(this);
        }

        desiredLockMode = config.writeLockModeAsInt <= desiredLockMode ? desiredLockMode : config.writeLockModeAsInt;

        final GammaRefTranlocal tranlocal = tx.tranlocal;

        //noinspection ObjectEquality
        if (tranlocal.owner == this) {
            final int mode = tranlocal.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return tranlocal;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
                return tranlocal;
            }

            if (desiredLockMode > tranlocal.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, tranlocal, desiredLockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            tx.hasWrites = true;
            tranlocal.setDirty(!config.dirtyCheck);
            tranlocal.mode = TRANLOCAL_WRITE;
            return tranlocal;
        }

        if (tranlocal.owner != null) {
            throw tx.abortOnTooSmallSize(2);
        }

        initTranlocalForWrite(config, tranlocal);
        if (!load(tranlocal, desiredLockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        tx.hasWrites = true;
        return tranlocal;
    }

    @Override
    public final GammaRefTranlocal openForWrite(final FatFixedLengthGammaTransaction tx, int lockMode) {
        if (tx.status != TX_ACTIVE) {
            throw tx.abortOpenForWriteOnBadStatus(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortOpenForWriteOnBadStm(this);
        }

        if (config.readonly) {
            throw tx.abortOpenForWriteOnReadonly(this);
        }

        GammaRefTranlocal found = null;
        GammaRefTranlocal newNode = null;
        GammaRefTranlocal node = tx.head;
        while (true) {
            if (node == null) {
                break;
            } else if (node.owner == this) {
                found = node;
                break;
            } else if (node.owner == null) {
                newNode = node;
                break;
            } else {
                node = node.next;
            }
        }

        lockMode = config.writeLockModeAsInt > lockMode ? config.writeLockModeAsInt : lockMode;

        if (found != null) {
            tx.shiftInFront(found);

            final int mode = found.mode;

            if (mode == TRANLOCAL_CONSTRUCTING) {
                return found;
            }

            if (mode == TRANLOCAL_COMMUTING) {
                if (!flattenCommute(tx, found, lockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
                return found;
            }

            if (lockMode > found.getLockMode()) {
                if (!tryLockAndCheckConflict(config.spinCount, found, lockMode)) {
                    throw tx.abortOnReadWriteConflict();
                }
            }

            found.mode = TRANLOCAL_WRITE;
            found.setDirty(!config.dirtyCheck);
            tx.hasWrites = true;
            return found;
        }

        if (newNode == null) {
            throw tx.abortOnTooSmallSize(config.arrayTransactionSize + 1);
        }

        initTranlocalForWrite(config, newNode);
        if (!load(newNode, lockMode, config.spinCount, tx.arriveEnabled)) {
            throw tx.abortOnReadWriteConflict();
        }

        if (tx.hasReads) {
            if (!tx.isReadConsistent(newNode)) {
                throw tx.abortOnReadWriteConflict();
            }
        } else {
            tx.hasReads = true;
        }

        tx.hasReads = true;
        tx.hasWrites = true;
        tx.size++;
        tx.shiftInFront(newNode);
        return newNode;
    }

    // ============================================================================================
    // ================================= open for commute =========================================
    // ============================================================================================

    public final void openForCommute(final GammaTransaction tx, final Function function) {
        if (tx == null) {
            throw new NullPointerException("tx can't be null");
        }

        if (tx instanceof FatMonoGammaTransaction) {
            openForCommute((FatMonoGammaTransaction) tx, function);
        } else if (tx instanceof FatFixedLengthGammaTransaction) {
            openForCommute((FatFixedLengthGammaTransaction) tx, function);
        } else {
            openForCommute((FatMapGammaTransaction) tx, function);
        }
    }

    private void initTranlocalForCommute(final GammaTransactionConfiguration config, final GammaRefTranlocal tranlocal) {
        tranlocal.owner = this;
        tranlocal.mode = TRANLOCAL_COMMUTING;
        tranlocal.isDirty = !config.dirtyCheck;
        tranlocal.writeSkewCheck = false;
    }

    public final void openForCommute(final FatMonoGammaTransaction tx, final Function function) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortCommuteOnBadStatus(this, function);
        }

        if (function == null) {
            throw tx.abortCommuteOnNullFunction(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortCommuteOnBadStm(this);
        }

        if (config.isReadonly()) {
            throw tx.abortCommuteOnReadonly(this);
        }

        final GammaRefTranlocal tranlocal = tx.tranlocal;

        //noinspection ObjectEquality
        if (tranlocal.owner == this) {
            if (tranlocal.isCommuting()) {
                tranlocal.addCommutingFunction(tx.pool, function);
                return;
            }

            if (tranlocal.isRead()) {
                tranlocal.mode = TRANLOCAL_WRITE;
                tx.hasWrites = true;
            }

            boolean abort = true;
            try {
                evaluate(tranlocal, function);
                abort = false;
            } finally {
                if (abort) {
                    tx.abort();
                }
            }
            return;
        }

        if (tranlocal.owner != null) {
            throw tx.abortOnTooSmallSize(2);
        }

        tx.hasWrites = true;
        initTranlocalForCommute(config, tranlocal);
        tranlocal.addCommutingFunction(tx.pool, function);
    }

    public final void openForCommute(final FatFixedLengthGammaTransaction tx, final Function function) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortCommuteOnBadStatus(this, function);
        }

        if (function == null) {
            throw tx.abortCommuteOnNullFunction(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortCommuteOnBadStm(this);
        }

        if (config.isReadonly()) {
            throw tx.abortCommuteOnReadonly(this);
        }

        GammaRefTranlocal found = null;
        GammaRefTranlocal newNode = null;
        GammaRefTranlocal node = tx.head;
        while (true) {
            if (node == null) {
                break;
            } else //noinspection ObjectEquality
                if (node.owner == this) {
                    found = node;
                    break;
                } else if (node.owner == null) {
                    newNode = node;
                    break;
                } else {
                    node = node.next;
                }
        }

        if (found != null) {
            if (found.isCommuting()) {
                found.addCommutingFunction(tx.pool, function);
                return;
            }

            //todo: write lock should be applied?
            if (found.isRead()) {
                found.mode = TRANLOCAL_WRITE;
                tx.hasWrites = true;
            }

            boolean abort = true;
            try {
                evaluate(found, function);
                abort = false;
            } finally {
                if (abort) {
                    tx.abort();
                }
            }
            return;
        }

        if (newNode == null) {
            throw tx.abortOnTooSmallSize(config.arrayTransactionSize + 1);
        }

        tx.size++;
        tx.shiftInFront(newNode);
        tx.hasWrites = true;
        initTranlocalForCommute(config, newNode);
        newNode.addCommutingFunction(tx.pool, function);
    }

    public final void openForCommute(final FatMapGammaTransaction tx, final Function function) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortCommuteOnBadStatus(this, function);
        }

        if (function == null) {
            throw tx.abortCommuteOnNullFunction(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        //noinspection ObjectEquality
        if (config.stm != stm) {
            throw tx.abortCommuteOnBadStm(this);
        }

        if (config.isReadonly()) {
            throw tx.abortCommuteOnReadonly(this);
        }

        final int identityHash = identityHashCode();
        final int indexOf = tx.indexOf(this, identityHash);

        if (indexOf > -1) {
            final GammaRefTranlocal tranlocal = tx.array[indexOf];

            if (tranlocal.isCommuting()) {
                tranlocal.addCommutingFunction(tx.pool, function);
                return;
            }

            if (tranlocal.isRead()) {
                tranlocal.mode = TRANLOCAL_WRITE;
                tx.hasWrites = true;
            }

            boolean abort = true;
            try {
                evaluate(tranlocal, function);
                abort = false;
            } finally {
                if (abort) {
                    tx.abort();
                }
            }
            return;
        }

        final GammaRefTranlocal tranlocal = tx.pool.take(this);
        initTranlocalForCommute(config, tranlocal);
        tx.hasWrites = true;
        tx.attach(tranlocal, identityHash);
        tx.size++;
        tranlocal.addCommutingFunction(tx.pool, function);
    }

    // ============================================================================================
    // ================================= try acquire =========================================
    // ============================================================================================

    @Override
    public final boolean tryAcquire(final LockMode desiredLockMode) {
        final GammaTransaction tx = (GammaTransaction) getThreadLocalTransaction();

        if (tx == null) {
            throw new TransactionRequiredException();
        }

        return tryAcquire(tx, desiredLockMode);
    }

    public final boolean tryAcquire(final GammaTransaction tx, final LockMode desiredLockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx instanceof FatMonoGammaTransaction) {
            return tryAcquire((FatMonoGammaTransaction) tx, desiredLockMode);
        } else if (tx instanceof FatFixedLengthGammaTransaction) {
            return tryAcquire((FatFixedLengthGammaTransaction) tx, desiredLockMode);
        } else {
            return tryAcquire((FatMapGammaTransaction) tx, desiredLockMode);
        }
    }

    @Override
    public final boolean tryAcquire(final Transaction tx, final LockMode desiredLockMode) {
        return tryAcquire((GammaTransaction) tx, desiredLockMode);
    }

    public final boolean tryAcquire(final FatMonoGammaTransaction tx, final LockMode desiredLockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortTryAcquireOnBadStatus(this);
        }

        if (desiredLockMode == null) {
            throw tx.abortTryAcquireOnNullLockMode(this);
        }

        throw new TodoException();
    }

    public final boolean tryAcquire(final FatFixedLengthGammaTransaction tx, final LockMode desiredLockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortTryAcquireOnBadStatus(this);
        }

        if (desiredLockMode == null) {
            throw tx.abortTryAcquireOnNullLockMode(this);
        }

        throw new TodoException();
    }

    public final boolean tryAcquire(final FatMapGammaTransaction tx, final LockMode desiredLockMode) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortTryAcquireOnBadStatus(this);
        }

        if (desiredLockMode == null) {
            throw tx.abortTryAcquireOnNullLockMode(this);
        }

        final GammaTransactionConfiguration config = tx.config;

        final GammaRefTranlocal tranlocal = tx.locate(this);
        if (tranlocal != null) {
            return tryLockAndCheckConflict(config.spinCount, tranlocal, desiredLockMode.asInt());
        }

        throw new TodoException();
    }

    public final void ensure() {
        ensure(getRequiredThreadLocalGammaTransaction());
    }

    public final void ensure(final Transaction self) {
        ensure(asGammaTransaction(self));
    }

    public final void ensure(final GammaTransaction tx) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (tx.status != TX_ACTIVE) {
            throw tx.abortEnsureOnBadStatus(this);
        }

        if (tx.config.readonly) {
            return;
        }

        GammaRefTranlocal tranlocal = openForRead(tx, LOCKMODE_NONE);
        tranlocal.writeSkewCheck = true;
    }

    public final long atomicGetLong() {
        assert type != TYPE_REF;

        int attempt = 1;
        do {
            if (!hasExclusiveLock()) {
                long read = long_value;

                if (!hasExclusiveLock()) {
                    return read;
                }
            }
            stm.defaultBackoffPolicy.delayedUninterruptible(attempt);
            attempt++;
        } while (attempt <= stm.spinCount);

        throw new LockedException();
    }

    public final Object atomicObjectGet() {
        assert type == TYPE_REF;

        int attempt = 1;
        do {
            if (!hasExclusiveLock()) {
                Object read = ref_value;
                if (!hasExclusiveLock()) {
                    return read;
                }
            }
            stm.defaultBackoffPolicy.delayedUninterruptible(attempt);
            attempt++;
        } while (attempt <= stm.spinCount);

        throw new LockedException();
    }

    public final long atomicSetLong(final long newValue, boolean returnOld) {
        final int arriveStatus = arriveAndAcquireExclusiveLockOrBackoff();

        if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
            throw new LockedException();
        }

        final long oldValue = long_value;

        if (oldValue == newValue) {
            if (arriveStatus == ARRIVE_UNREGISTERED) {
                unlockByUnregistered();
            } else {
                departAfterReadingAndUnlock();
            }

            return newValue;
        }

        long_value = newValue;
        //noinspection NonAtomicOperationOnVolatileField
        version++;

        final Listeners listeners = ___removeListenersAfterWrite();

        departAfterUpdateAndUnlock();

        if (listeners != null) {
            final GammaObjectPool pool = getThreadLocalGammaObjectPool();
            listeners.openAll(pool);
        }

        return returnOld ? oldValue : newValue;
    }

    public final boolean atomicCompareAndSetLong(final long expectedValue, final long newValue) {
        final int arriveStatus = arriveAndAcquireExclusiveLockOrBackoff();

        if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
            throw new LockedException();
        }

        final long currentValue = long_value;

        if (currentValue != expectedValue) {
            departAfterFailureAndUnlock();
            return false;
        }

        if (expectedValue == newValue) {
            if (arriveStatus == ARRIVE_UNREGISTERED) {
                unlockByUnregistered();
            } else {
                departAfterReadingAndUnlock();
            }

            return true;
        }

        long_value = newValue;
        //noinspection NonAtomicOperationOnVolatileField
        version++;
        final Listeners listeners = ___removeListenersAfterWrite();

        departAfterUpdateAndUnlock();

        if (listeners != null) {
            listeners.openAll(getThreadLocalGammaObjectPool());
        }

        return true;
    }
}
