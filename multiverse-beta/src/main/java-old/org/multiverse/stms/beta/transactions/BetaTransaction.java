package org.multiverse.stms.beta.transactions;

import org.multiverse.stms.beta.*;
import org.multiverse.stms.beta.transactionalobjects.*;

import static org.multiverse.stms.beta.BetaStmUtils.toDebugString;

/**
 * @author Peter Veentjer
 */
public abstract class BetaTransaction implements Transaction, BetaStmConstants {

    public final static int POOL_TRANSACTIONTYPE_LEAN_MONO = 0;
    public final static int POOL_TRANSACTIONTYPE_FAT_MONO = 1;
    public final static int POOL_TRANSACTIONTYPE_LEAN_ARRAY = 2;
    public final static int POOL_TRANSACTIONTYPE_FAT_ARRAY = 3;
    public final static int POOL_TRANSACTIONTYPE_LEAN_ARRAYTREE = 4;
    public final static int POOL_TRANSACTIONTYPE_FAT_ARRAYTREE = 5;

    public final static int NEW = 0;
    public final static int ACTIVE = 1;
    public final static int PREPARED = 2;
    public final static int ABORTED = 3;
    public final static int COMMITTED = 4;

    public final RetryLatch listener = new DefaultRetryLatch();
    public final BetaObjectPool pool = new BetaObjectPool();
    public final int poolTransactionType;
    public int status = ACTIVE;
    public int attempt = 1;
    public long remainingTimeoutNs;
    public BetaTransactionConfiguration config;
    public boolean abortOnly;
    public boolean hasUpdates;

    public BetaTransaction(int poolTransactionType, BetaTransactionConfiguration config) {
        this.poolTransactionType = poolTransactionType;
        this.config = config;
    }

    public abstract LocalConflictCounter getLocalConflictCounter();

    public final boolean isAlive() {
        return status == ACTIVE || status == PREPARED;
    }

    public final BetaObjectPool getPool() {
        return pool;
    }

    public final int getPoolTransactionType() {
        return poolTransactionType;
    }

    @Override
    public final BetaTransactionConfiguration getConfiguration() {
        return config;
    }

    @Override
    public final int getAttempt() {
        return attempt;
    }

    @Override
    public final TransactionStatus getStatus() {
        switch (status) {
            case NEW:
                return TransactionStatus.Undefined;
            case ACTIVE:
                return TransactionStatus.Active;
            case ABORTED:
                return TransactionStatus.Aborted;
            case COMMITTED:
                return TransactionStatus.Committed;
            case PREPARED:
                return TransactionStatus.Prepared;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public final long getRemainingTimeoutNs() {
        return remainingTimeoutNs;
    }

    /**
     * Sets the remaining timeout in nanoseconds. Long.MAX_VALUE indicates that no timeout should be used. When
     * the Transaction is used for the first attempt, the remaining timeout is getAndSet to the
     * {@link org.multiverse.api.TransactionConfiguration#getTimeoutNs()}.
     * <p/>
     * This normally isn't called from the user code, it is task of the stm internals and the
     * transaction management to use the timeout.
     *
     * @param timeoutNs the timeout.
     * @throws IllegalArgumentException if timeout smaller than 0 or when the timeout is larger than the previous
     *                                  remaining timeout. This is done to prevent that the timeout is increased
     *                                  to a value that is in conflict with the {@link TransactionConfiguration}.
     */
    public final void setRemainingTimeoutNs(long timeoutNs) {
        if (timeoutNs > remainingTimeoutNs) {
            throw new IllegalArgumentException();
        }
        this.remainingTimeoutNs = timeoutNs;
    }

    /**
     * Returns the tranlocal that belongs to the given transactional object.
     *
     * @return the found tranlocal, or null if not found.
     */
    public abstract BetaTranlocal get(BetaTransactionalObject object);

    public abstract BetaTranlocal locate(BetaTransactionalObject object);

    /**
     * Returns a list containing the normal TransactionLifecycleListeners. The returned list
     * can be null (essentially the same as an empty list).
     */
    public abstract ArrayList<TransactionLifecycleListener> getNormalListeners();

    public final SpeculativeConfigurationError abortOnTooSmallSize(int minimalSize) {
        config.needsMinimalTransactionLength(minimalSize);
        abort();
        return SpeculativeConfigurationError.INSTANCE;
    }

    public final ReadWriteConflict abortOnReadConflict() {
        abort();
        return ReadWriteConflict.INSTANCE;
    }

    public final ReadWriteConflict abortOnWriteConflict() {
        abort();
        return ReadWriteConflict.INSTANCE;
    }

    public final void materializeConflict(BetaTransactionalObject ref) {
        BetaTranlocal tranlocal = openForRead(ref, LOCKMODE_NONE);
        tranlocal.setIsConflictCheckNeeded(true);
    }

    public final IllegalTransactionStateException abortRead(BetaTransactionalObject owner) {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.read, reason: the transaction is prepared",
                                config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.read, reason: the transaction is aborted",
                                config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.read, reason: the transaction is committed",
                                config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final NullPointerException abortReadOnNull() {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.read, reason: the reference is null",
                        config.familyName));
    }

    public final StmMismatchException abortReadOnStmMismatch(BetaTransactionalObject ref) {
        abort();
        return new StmMismatchException(
                format("[%s] Failed to execute Transaction.read, reason: The transaction belongs to a different stm than the stm that created ref '%s'",
                        config.familyName, toDebugString(ref)));
    }

    public final IllegalTransactionStateException abortLocate(BetaTransactionalObject owner) {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.locate, reason: the transaction is prepared",
                                config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.locate, reason: the transaction is aborted",
                                config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.locate, reason: the transaction is committed",
                                config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final NullPointerException abortLocateWhenNullReference() {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.locate, reason: the reference is null",
                        config.familyName));
    }

    @Override
    public boolean isAbortOnly() {
        throw new TodoException();
    }

    @Override
    public final void setAbortOnly() {
        switch (status) {
            case NEW:
                throw new TodoException();
            case ACTIVE:
                abortOnly = true;
                break;
            case PREPARED:
                throw new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.setAbortOnly, reason: the transaction is prepared",
                                config.familyName));
            case COMMITTED:
                throw new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.setAbortOnly, reason: the transaction is committed",
                                config.familyName));
            case ABORTED:
                throw new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.setAbortOnly, reason: the transaction is aborted",
                                config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final NullPointerException abortCommuteOnNullFunction(final TransactionalObject ref) {
        abort();
        throw new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.commute, reason: the function is null",
                        config.familyName));
    }

    public final RetryNotPossibleException abortOnNoRetryPossible() {
        abort();
        throw new RetryNotPossibleException(
                format("[%s] Failed to execute BetaTransaction.retry, reason: there are no tracked reads",
                        config.familyName));
    }

    public final RetryNotAllowedException abortOnNoBlockingAllowed() {
        abort();
        return new RetryNotAllowedException(
                format("[%s] Failed to execute BetaTransaction.retry, reason: the transaction doesn't allow blocking",
                        config.familyName));
    }

    public final IllegalTransactionStateException abortOnFaultyStatusOfRetry() {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.retry, reason: the transaction is prepared",
                                config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.retry, reason: the transaction is aborted",
                                config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.retry, reason: the transaction is committed",
                                config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final StmMismatchException abortOnStmMismatch(final BetaTransactionalObject ref) {
        abort();
        return new StmMismatchException(
                format("[%s] The transaction belongs to a different stm than the stm that created ref '%s'",
                        config.familyName, toDebugString(ref)));
    }

    public final NullPointerException abortOpenOnNull() {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.open, reason: the reference is null",
                        config.familyName));
    }

    public final RuntimeException abortOnOpenForReadWhileEvaluatingCommute(
            final BetaTransactionalObject ref) {

        abort();
        return new IllegalTransactionStateException(
                format("[%s] Failed to execute BetaTransaction.openForRead '%s', reason: a commuting function is being evaluated",
                        config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOnOpenForWriteWhileEvaluatingCommute(
            final BetaTransactionalObject ref) {

        abort();
        return new IllegalTransactionStateException(
                format("[%s] Failed to execute BetaTransaction.openForWrite '%s', reason: a commuting function is being evaluated",
                        config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOpen(final BetaTransactionalObject ref) {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.open '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(ref)));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.open '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(ref)));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.open '%s', reason: the transaction is committed",
                                config.familyName, toDebugString(ref)));
            default:
                throw new IllegalStateException();
        }
    }

    public final RuntimeException abortOnOpenForConstructionWhileEvaluatingCommute(
            final BetaTransactionalObject ref) {

        abort();
        return new IllegalTransactionStateException(
                format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: a commuting function is being evaluated",
                        config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOnCommuteWhileEvaluatingCommute(
            BetaTransactionalObject ref) {

        abort();
        return new IllegalTransactionStateException(
                format("[%s] Failed to execute BetaTransaction.commute '%s', reason: a commuting function is being evaluated",
                        config.familyName, toDebugString(ref)));
    }

    public final IllegalArgumentException abortOpenForConstructionWithBadReference(
            final BetaTransactionalObject ref) {

        abort();
        return new IllegalArgumentException(
                format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: the object is not new and has previous commits",
                        config.familyName, toDebugString(ref)));
    }

    public final ReadonlyException abortOpenForWriteWhenReadonly(
            final BetaTransactionalObject object) {

        abort();
        return new ReadonlyException(
                format("[%s] Failed to execute BetaTransaction.openForWrite '%s', reason: the transaction is readonly",
                        config.familyName, toDebugString(object)));
    }

    public final NullPointerException abortOpenForWriteWhenNullReference() {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction BetaTransaction.openForWrite 'null', reason: the reference is null",
                        config.familyName));
    }

    public final NullPointerException abortOpenForConstructionWhenNullReference() {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.openForConstruction 'null', reason the reference is null",
                        config.familyName));
    }

    public final NullPointerException abortTryLockWhenNullReference(final TransactionalObject object) {
        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.tryLock 'null', reason: the reference is null",
                        config.familyName));
    }

    public final NullPointerException abortCommuteWhenNullReference(
            final Function function) {

        abort();
        return new NullPointerException(
                format("[%s] Failed to execute BetaTransaction.commute 'null' and function '%s', reason: the reference is null",
                        config.familyName, function));
    }

    public final ReadonlyException abortOpenForConstructionWhenReadonly(
            final BetaTransactionalObject object) {

        abort();
        return new ReadonlyException(
                format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: the transaction is readonly",
                        config.familyName, toDebugString(object)));
    }

    public final ReadonlyException abortCommuteWhenReadonly(
            final BetaTransactionalObject object, final Function function) {

        abort();
        return new ReadonlyException(
                format("[%s] Failed to execute BetaTransaction.commute '%s' with function '%s', reason: the transaction is readonly",
                        config.familyName, toDebugString(object), function));
    }

    public final IllegalTransactionStateException abortEnsureWrites() {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.ensureWrites, reason: the transaction is prepared",
                                config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.ensureWrites, reason: the transaction is aborted",
                                config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.ensureWrites, reason: the transaction is committed",
                                config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final IllegalTransactionStateException abortTryLock(final BetaTransactionalObject object) {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.tryLock '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.tryLock '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.tryLock '%s', reason: the transaction is committed",
                                config.familyName, toDebugString(object)));
            default:
                throw new IllegalStateException();
        }
    }

    public final IllegalTransactionStateException abortOpenForRead(final BetaTransactionalObject object) {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForRead '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForRead '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForRead '%s', reason: the transaction is committed",
                                config.familyName, toDebugString(object)));
            default:
                throw new IllegalStateException();
        }
    }

    public final IllegalTransactionStateException abortOpenForWrite(
            final BetaTransactionalObject object) {

        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForWrite '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForWrite '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForWrite '%s', reason: the transaction is committed",
                                config.familyName, toDebugString(object)));
            default:
                throw new IllegalStateException();
        }
    }

    public final IllegalTransactionStateException abortOpenForConstruction(
            final BetaTransactionalObject object) {

        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.openForConstruction '%s', reason: the transaction is committed",
                                config.familyName, toDebugString(object)));
            default:
                throw new IllegalStateException();
        }
    }

    public final IllegalTransactionStateException abortCommute(
            final BetaTransactionalObject object, final Function function) {

        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                        format("[%s] Failed to execute BetaTransaction.commute '%s' with reference '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object), function));
            case ABORTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.commute '%s' with reference '%s', reason: the transaction is aborted",
                                config.familyName, toDebugString(object), function));
            case COMMITTED:
                return new DeadTransactionException(
                        format("[%s] Failed to execute BetaTransaction.commute '%s' with reference '%s', reason: the transaction is prepared",
                                config.familyName, toDebugString(object), function));
            default:
                throw new IllegalStateException();
        }
    }

    public abstract void copyForSpeculativeFailure(BetaTransaction tx);

    public abstract boolean softReset();

    public abstract void hardReset();

    public final void awaitUpdate() {
        final long lockEra = listener.getEra();

        if (config.timeoutNs == Long.MAX_VALUE) {
            if (config.isInterruptible()) {
                listener.await(lockEra, config.familyName);
            } else {
                listener.awaitUninterruptible(lockEra);
            }
        } else {
            if (config.isInterruptible()) {
                remainingTimeoutNs = listener.awaitNanos(lockEra, remainingTimeoutNs, config.familyName);
            } else {
                remainingTimeoutNs = listener.awaitNanosUninterruptible(lockEra, remainingTimeoutNs);
            }

            if (remainingTimeoutNs < 0) {
                throw new RetryTimeoutException(
                        format("[%s] Transaction has timed with a total timeout of %s ns",
                                config.getFamilyName(), config.getTimeoutNs()));
            }
        }
    }

    public abstract void startEitherBranch();

    public abstract void endEitherBranch();

    public abstract void startOrElseBranch();

    public abstract void init(BetaTransactionConfiguration transactionConfig);

    public abstract boolean tryLock(BetaTransactionalObject ref, int lockMode);


    public abstract <E> E read(BetaRef<E> ref);

    public abstract <E> BetaRefTranlocal<E> openForRead(BetaRef<E> ref, int lockMode);

    public abstract <E> BetaRefTranlocal<E> openForWrite(BetaRef<E> ref, int lockMode);

    public abstract <E> BetaRefTranlocal<E> openForConstruction(BetaRef<E> ref);

    public abstract <E> void commute(BetaRef<E> ref, final Function<E> function);

    public abstract int read(BetaIntRef ref);

    public abstract BetaIntRefTranlocal openForRead(BetaIntRef ref, int lockMode);

    public abstract BetaIntRefTranlocal openForWrite(BetaIntRef ref, int lockMode);

    public abstract BetaIntRefTranlocal openForConstruction(BetaIntRef ref);

    public abstract void commute(BetaIntRef ref, final IntFunction function);

    public abstract boolean read(BetaBooleanRef ref);

    public abstract BetaBooleanRefTranlocal openForRead(BetaBooleanRef ref, int lockMode);

    public abstract BetaBooleanRefTranlocal openForWrite(BetaBooleanRef ref, int lockMode);

    public abstract BetaBooleanRefTranlocal openForConstruction(BetaBooleanRef ref);

    public abstract void commute(BetaBooleanRef ref, final BooleanFunction function);

    public abstract double read(BetaDoubleRef ref);

    public abstract BetaDoubleRefTranlocal openForRead(BetaDoubleRef ref, int lockMode);

    public abstract BetaDoubleRefTranlocal openForWrite(BetaDoubleRef ref, int lockMode);

    public abstract BetaDoubleRefTranlocal openForConstruction(BetaDoubleRef ref);

    public abstract void commute(BetaDoubleRef ref, final DoubleFunction function);

    public abstract long read(BetaLongRef ref);

    public abstract BetaLongRefTranlocal openForRead(BetaLongRef ref, int lockMode);

    public abstract BetaLongRefTranlocal openForWrite(BetaLongRef ref, int lockMode);

    public abstract BetaLongRefTranlocal openForConstruction(BetaLongRef ref);

    public abstract void commute(BetaLongRef ref, final LongFunction function);

    public abstract BetaTranlocal openForRead(BetaTransactionalObject ref, int lockMode);

    public abstract BetaTranlocal openForWrite(BetaTransactionalObject ref, int lockMode);

    public abstract BetaTranlocal openForConstruction(BetaTransactionalObject ref);

    public abstract void commute(BetaTransactionalObject ref, final Function function);
}
