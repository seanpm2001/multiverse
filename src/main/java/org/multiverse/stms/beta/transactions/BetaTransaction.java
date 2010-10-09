package org.multiverse.stms.beta.transactions;

import org.multiverse.api.*;
import org.multiverse.api.blocking.Latch;
import org.multiverse.api.exceptions.*;
import org.multiverse.api.functions.*;
import org.multiverse.api.lifecycle.TransactionLifecycleListener;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStmConstants;
import org.multiverse.stms.beta.conflictcounters.LocalConflictCounter;
import org.multiverse.stms.beta.transactionalobjects.*;

import java.util.ArrayList;

import static java.lang.String.format;
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

    private final int poolTransactionType;
    protected int status = ACTIVE;
    protected int attempt = 1;
    protected long remainingTimeoutNs;
    protected BetaTransactionConfiguration config;
    protected boolean abortOnly;
    protected final BetaObjectPool pool = new BetaObjectPool();
 
    public BetaTransaction(int poolTransactionType, BetaTransactionConfiguration config) {
        this.poolTransactionType = poolTransactionType;
        this.config = config;
    }

    public abstract LocalConflictCounter getLocalConflictCounter();

    /**
     * Checks if this Transaction is alive. The same information can be retrieved by calling the getStatus,
     * but this is a bit better performing.
     *
     * @return true if alive (so active or prepared), false otherwise.
     */    
    public final boolean isAlive(){
        return status == ACTIVE || status == PREPARED;
    }

    public final BetaObjectPool getPool(){
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
    * @returns the found tranlocal, or null if not found.
    */
    public abstract Tranlocal get(BetaTransactionalObject object);

    /**
    * Returns a list containing the normal TransactionLifecycleListeners. The returned list
    * can be null (essentially the same as an empty list).
    *
    */
    public abstract ArrayList<TransactionLifecycleListener> getNormalListeners();

    protected final SpeculativeConfigurationError abortOnTooSmallSize(int minimalSize) {
        config.needsMinimalTransactionLength(minimalSize);
        abort();
        return SpeculativeConfigurationError.INSTANCE;
    }

    protected final ReadWriteConflict abortOnReadConflict() {
        abort();
        return ReadWriteConflict.INSTANCE;
    }

    protected final ReadWriteConflict abortOnWriteConflict() {
        abort();
        return ReadWriteConflict.INSTANCE;
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
                    format("[%s] Can't setAbortOnly on an already prepared transaction",
                        config.familyName));
            case COMMITTED:
                throw new DeadTransactionException(
                    format("[%s] Can't setAbortOnly on an already committed transaction",
                        config.familyName));
            case ABORTED:
                throw new DeadTransactionException(
                    format("[%s] Can't setAbortOnly on an already aborted transaction",
                        config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final NullPointerException abortCommuteOnNullFunction(final TransactionalObject ref) {
        abort();
        throw new NullPointerException(
            format("[%s] Commute function can't be null",
                config.familyName));
    }

    public final NoRetryPossibleException abortOnNoRetryPossible(){
        abort();
        throw new NoRetryPossibleException(
            format("[%s] Can't block transaction since there are no tracked reads",
                config.familyName));
    }

    public final NoRetryPossibleException abortOnNoBlockingAllowed(){
        abort();
        return new NoRetryPossibleException(
            format("[%s] Can't block transaction since it doesn't allow blocking",
                config.familyName));
    }

    public final IllegalTransactionStateException abortOnFaultyStatusOfRegisterChangeListenerAndAbort(){
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                    format("[%s] Can't block on an already prepared transaction",
                        config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                    format("[%s] Can't block on already aborted transaction",
                        config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                    format("[%s] Can't block on already committed transaction",
                        config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    public final RuntimeException abortOnOpenForReadWhileEvaluatingCommute(
        final BetaTransactionalObject ref){

        abort();
        throw new IllegalTransactionStateException(
            format("[%s] Can't openForRead '%s' while evaluating a commuting function",
                config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOnOpenForWriteWhileEvaluatingCommute(
        final BetaTransactionalObject ref){

        abort();
        throw new IllegalTransactionStateException(
            format("[%s] Can't openForWrite '%s' while evaluating a commuting function",
                config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOnOpenForConstructionWhileEvaluatingCommute(
        final BetaTransactionalObject ref){

        abort();
        throw new IllegalTransactionStateException(
            format("[%s] Can't openForConstruction '%s' while evaluating a commuting function",
            config.familyName, toDebugString(ref)));
    }

    public final RuntimeException abortOnCommuteWhileEvaluatingCommute(
        BetaTransactionalObject ref){

        abort();
        throw new IllegalTransactionStateException(
            format("[%s] Can't add a commuting function to '%s' while evaluating a commuting function",
                config.familyName, toDebugString(ref)));
    }

    public final IllegalArgumentException abortOpenForConstructionWithBadReference(
        final BetaTransactionalObject ref){

        abort();
        return new IllegalArgumentException(
            format("[%s] Can't openForConstruction a previous committed object or an object '%s'",
                config.familyName, toDebugString(ref)));
    }

    public final ReadonlyException abortOpenForWriteWhenReadonly(
        final BetaTransactionalObject object){

        abort();
        return new ReadonlyException(
            format("[%s] Can't openForWrite '%s' on a readonly transaction",
                config.familyName, toDebugString(object)));
    }

    public final NullPointerException abortOpenForWriteWhenNullReference(){
        abort();
        return new NullPointerException(
            format("[%s] Can't openForWrite a null reference",
                config.familyName));
    }

    public final NullPointerException abortOpenForConstructionWhenNullReference(){
        abort();
        return new NullPointerException(
            format("[%s] Can't openForConstruction a null reference",
                config.familyName));
    }

    public final NullPointerException abortTryLockWhenNullReference(final TransactionalObject object){
        abort();
        return new NullPointerException(
            format("[%s] Can't tryLock with a null reference",
                config.familyName));
    }

    public final NullPointerException abortCommuteWhenNullReference(
        final Function function){

        abort();
        return new NullPointerException(
            format("[%s] Can't commute with a null reference and function '%s'",
                config.familyName, function));
    }

    public final ReadonlyException abortOpenForConstructionWhenReadonly(
        final BetaTransactionalObject object){

        abort();
        return new ReadonlyException(
            format("[%s] Can't openForConstruction '%s' using a readonly transaction",
                config.familyName, toDebugString(object)));
    }

    public final ReadonlyException abortCommuteWhenReadonly(
            final BetaTransactionalObject object, final Function function){

        abort();
        return new ReadonlyException(
            format("[%s] Can't commute on '%s' with function '%s' and a readonly transaction ''",
                 config.familyName, toDebugString(object), function));
    }

    public final IllegalTransactionStateException abortEnsureWrites() {
        switch (status) {
            case PREPARED:
                abort();
                return new PreparedTransactionException(
                    format("[%s] Can't ensureWrites using an already prepared transaction",
                        config.familyName));
            case ABORTED:
                return new DeadTransactionException(
                    format("[%s] Can't ensureWrites using an already aborted transaction",
                        config.familyName));
            case COMMITTED:
                return new DeadTransactionException(
                    format("[%s] Can't ensureWrites using already committed transaction",
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
                   format("[%s] Can't tryLock '%s' using an already prepared transaction",
                       config.familyName, toDebugString(object)));
           case ABORTED:
               return new DeadTransactionException(
                   format("[%s] Can't tryLock '%s' using an already aborted transaction",
                       config.familyName, toDebugString(object)));
           case COMMITTED:
               return new DeadTransactionException(
                   format("[%s] Can't tryLock '%s' using already committed transaction",
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
                    format("[%s] Can't openForRead '%s' using an already prepared transaction",
                        config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForRead '%s' using an already aborted transaction",
                        config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForRead '%s' using already committed transaction",
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
                    format("[%s] Can't openForWrite '%s' using an already prepared transaction",
                        config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForWrite '%s' using an already aborted transaction",
                        config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForWrite '%s' using an already committed transaction",
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
                    format("[%s] Can't openForConstruction '%s' using an already prepared transaction",
                        config.familyName, toDebugString(object)));
            case ABORTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForConstruction '%s' using an already aborted transaction",
                        config.familyName, toDebugString(object)));
            case COMMITTED:
                return new DeadTransactionException(
                    format("[%s] Can't openForConstruction '%s' using an already committed transaction",
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
                    format("[%s] Can't commuting '%s' with reference '%s' using an already prepared transaction",
                        config.familyName, toDebugString(object), function));
           case ABORTED:
               return new DeadTransactionException(
                    format("[%s] Can't commuting '%s' with reference '%s' using an already aborted transaction",
                        config.familyName, toDebugString(object), function));
           case COMMITTED:
               return new DeadTransactionException(
                    format("[%s] Can't commuting '%s' with reference '%s' using an already committed transaction",
                        config.familyName, toDebugString(object), function));
           default:
               throw new IllegalStateException();
       }
    }

    public abstract void copyForSpeculativeFailure(BetaTransaction tx);

    public abstract boolean softReset();

    public abstract void hardReset();

   /**
    * Registers the changeListener to all reads done by the transaction and aborts the transaction. This functionality
    * is needed for creating blocking transactions; transactions that are able to wait for change. In the STM literature
    * this is know as the 'retry' and 'orelse' functionality.
    *
    * @param changeListener the Latch the is notified when a change happens on one of the reads.
    * @throws NullPointerException if changeListener is null (will also ___abort the transaction).
    * @throws org.multiverse.api.exceptions.RetryException
    *                              if the retry fails
    * @throws org.multiverse.api.exceptions.IllegalTransactionStateException
    *                              if the transaction isn't in the correct
    *                              state for this transaction. When this exception happens, the transaction will also
    *                              be aborted (if it isn't committed).
    */
    public abstract void registerChangeListenerAndAbort(Latch changeListener);

    public abstract void startEitherBranch();

    public abstract void endEitherBranch();

    public abstract void startOrElseBranch();

    public abstract void init(BetaTransactionConfiguration transactionConfig);

    public abstract boolean tryLock(BetaTransactionalObject ref, int lockMode);

    public abstract void addWatch(BetaTransactionalObject object, Watch watch);

    public abstract <E> E read(BetaRef<E> ref);
    
    public abstract <E> RefTranlocal<E> openForRead(BetaRef<E> ref, int lockMode);

    public abstract <E> RefTranlocal<E> openForWrite(BetaRef<E> ref, int lockMode);

    public abstract <E> RefTranlocal<E> openForConstruction(BetaRef<E> ref);

    public abstract <E> void commute(BetaRef<E> ref, final Function<E> function);

    public abstract  int read(BetaIntRef ref);
    
    public abstract  IntRefTranlocal openForRead(BetaIntRef ref, int lockMode);

    public abstract  IntRefTranlocal openForWrite(BetaIntRef ref, int lockMode);

    public abstract  IntRefTranlocal openForConstruction(BetaIntRef ref);

    public abstract  void commute(BetaIntRef ref, final IntFunction function);

    public abstract  boolean read(BetaBooleanRef ref);
    
    public abstract  BooleanRefTranlocal openForRead(BetaBooleanRef ref, int lockMode);

    public abstract  BooleanRefTranlocal openForWrite(BetaBooleanRef ref, int lockMode);

    public abstract  BooleanRefTranlocal openForConstruction(BetaBooleanRef ref);

    public abstract  void commute(BetaBooleanRef ref, final BooleanFunction function);

    public abstract  double read(BetaDoubleRef ref);
    
    public abstract  DoubleRefTranlocal openForRead(BetaDoubleRef ref, int lockMode);

    public abstract  DoubleRefTranlocal openForWrite(BetaDoubleRef ref, int lockMode);

    public abstract  DoubleRefTranlocal openForConstruction(BetaDoubleRef ref);

    public abstract  void commute(BetaDoubleRef ref, final DoubleFunction function);

    public abstract  long read(BetaLongRef ref);
    
    public abstract  LongRefTranlocal openForRead(BetaLongRef ref, int lockMode);

    public abstract  LongRefTranlocal openForWrite(BetaLongRef ref, int lockMode);

    public abstract  LongRefTranlocal openForConstruction(BetaLongRef ref);

    public abstract  void commute(BetaLongRef ref, final LongFunction function);

    public abstract  Tranlocal openForRead(BetaTransactionalObject ref, int lockMode);

    public abstract  Tranlocal openForWrite(BetaTransactionalObject ref, int lockMode);

    public abstract  Tranlocal openForConstruction(BetaTransactionalObject ref);

    public abstract  void commute(BetaTransactionalObject ref, final Function function);
}
