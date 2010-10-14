package org.multiverse.stms.beta.transactionalobjects;

import org.multiverse.api.StmUtils;
import org.multiverse.api.Transaction;
import org.multiverse.api.exceptions.LockedException;
import org.multiverse.api.exceptions.PanicError;
import org.multiverse.api.exceptions.TodoException;
import org.multiverse.api.exceptions.TransactionRequiredException;
import org.multiverse.api.functions.BooleanFunction;
import org.multiverse.api.predicates.BooleanPredicate;
import org.multiverse.api.references.BooleanRef;
import org.multiverse.stms.beta.BetaObjectPool;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.Listeners;
import org.multiverse.stms.beta.transactions.BetaTransaction;

import static org.multiverse.api.ThreadLocalTransaction.getThreadLocalTransaction;
import static org.multiverse.stms.beta.ThreadLocalBetaObjectPool.getThreadLocalBetaObjectPool;

/**
 * The transactional object. Atm it is just a reference for an int, more complex stuff will be added again
 * once this project leaves the prototype stage.
 * <p/>
 * remember:
 * it could be that the lock is acquired, but the lockOwner has not been set yet.
 *
 * The whole idea of code generation is that once you are inside a concrete class,
 * polymorphism is needed anymore.
 *
 * This class is generated.
 *
 * @author Peter Veentjer
 */
public final class BetaBooleanRef
    extends VeryAbstractBetaTransactionalObject
    implements BooleanRef
{

    //Active needs to be volatile. If not, the both load statements in the load function, can be reordered
    //(the instruction above can jump below the orec.arrive if no write is done)
    private volatile boolean ___value;

     /**
     * Creates a uncommitted BetaBooleanRef that should be attached to the transaction (this
     * is not done)
     *
     * @param tx the transaction this BetaBooleanRef should be attached to.
     * @throws NullPointerException if tx is null.
     */
    public BetaBooleanRef(BetaTransaction tx){
        super(tx.getConfiguration().stm);
        ___tryLockAndArrive(0, true);
        this.___lockOwner = tx;
    }

    /**
     * Creates a committed BetaBooleanRef with false as initial value.
     *
     * @param stm the BetaStm this reference belongs to.
     * @throws NullPointerException if stm is null.
     */
    public BetaBooleanRef(BetaStm stm){
        this(stm, (boolean)false);
    }

    /**
     * Creates a committed BetaBooleanRef with the given initial value.
     *
     * @param stm the BetaStm this reference belongs to.
     * @param initialValue the initial value
     * @throws NullPointerException is stm is null.
     */
    public BetaBooleanRef(BetaStm stm, final boolean initialValue){
        super(stm);

        ___value = initialValue;
        ___version = VERSION_UNCOMMITTED+1;
    }


   @Override
    public final int ___getClassIndex(){
        return 2;
    }

    public final boolean ___weakRead(){
        return ___value;
    }

    @Override
    public final BooleanRefTranlocal ___newTranlocal(){
        return new BooleanRefTranlocal(this);
    }

    @Override
    public final boolean ___load(int spinCount, BetaTransaction newLockOwner, int lockMode, Tranlocal tranlocal){
        return ___load(
            spinCount,
            newLockOwner,
            lockMode,
            (BooleanRefTranlocal)tranlocal);
    }

    public final boolean ___load(int spinCount, BetaTransaction newLockOwner, int lockMode, BooleanRefTranlocal tranlocal){
        if(lockMode == LOCKMODE_NONE){
            while (true) {
                //JMM: nothing can jump behind the following statement
                final boolean firstValue = ___value;
                final long firstVersion = ___version;

                //JMM: the read for the arrive can't jump over the read of the active.
                final int arriveStatus = ___arrive(spinCount);

                if (arriveStatus == ARRIVE_LOCK_NOT_FREE) {
                    return false;
                }

                //JMM safety:
                //The volatile read of active can't be reordered so that it jump in front of the volatile read of
                //the orec-value when the arrive method is called.
                //An instruction is allowed to jump in front of the write of orec-value, but it is not allowed to
                //jump in front of the read or orec-value (volatile read happens before rule).
                //This means that it isn't possible that a locked value illegally is seen as unlocked.

                if (firstVersion == ___version && firstValue == ___value) {
                    //at this point we are sure that the read was unlocked.

                    tranlocal.version = firstVersion;
                    tranlocal.value = firstValue;
                    tranlocal.oldValue = firstValue;
                    tranlocal.setDepartObligation(arriveStatus == ARRIVE_NORMAL);
                    return true;
                }

                //we are not lucky, the value has changed. But before retrying, we need to depart if the arrive was
                //not permanent.
                if (arriveStatus == ARRIVE_NORMAL) {
                    ___departAfterFailure();
                }
            }
        }else{
            final boolean commitLock = lockMode == LOCKMODE_COMMIT;

            if(newLockOwner == null){
                throw new PanicError();
            }

            //JMM: no instructions will jump in front of a volatile read. So this stays on top.
            final int arriveStatus = ___tryLockAndArrive(___stm.spinCount, commitLock);
            if(arriveStatus == ARRIVE_LOCK_NOT_FREE){
                return false;
            }

            ___lockOwner = newLockOwner;
            final boolean value = ___value;

            tranlocal.version = ___version;
            tranlocal.value = value;
            tranlocal.oldValue = value;
            tranlocal.setLockMode(commitLock ? LOCKMODE_COMMIT: LOCKMODE_UPDATE);
            tranlocal.setDepartObligation(arriveStatus == ARRIVE_NORMAL);
            return true;
        }
   }

    @Override
    public final Listeners ___commitDirty(
            final Tranlocal tranlocal,
            final BetaTransaction expectedLockOwner,
            final BetaObjectPool pool) {

        if(!tranlocal.isDirty()){
            if(tranlocal.getLockMode() != LOCKMODE_NONE){
                ___lockOwner = null;

                if(tranlocal.hasDepartObligation()){
                    ___departAfterReadingAndUnlock();
                }else{
                    ___unlockByReadBiased();
                }
            }else{
                if(tranlocal.hasDepartObligation()){
                    ___departAfterReading();
                }
            }

            return null;
        }

        //it is a full blown update (so locked).

        final BooleanRefTranlocal specializedTranlocal = (BooleanRefTranlocal)tranlocal;

        ___value = specializedTranlocal.value;
        ___version = specializedTranlocal.version+1;
        ___lockOwner = null;
        Listeners listenersAfterWrite = ___listeners;

        if(listenersAfterWrite != null){
           listenersAfterWrite = ___removeListenersAfterWrite();
        }

        ___departAfterUpdateAndUnlock(___stm.globalConflictCounter, this);
        pool.put(specializedTranlocal);
        return listenersAfterWrite;
    }

    @Override
    public final Listeners ___commitAll(
            final Tranlocal tranlocal,
            final BetaTransaction expectedLockOwner,
            final BetaObjectPool pool) {

        if(tranlocal.isReadonly()){
            if(tranlocal.getLockMode() != LOCKMODE_NONE){
                ___lockOwner = null;

                if(tranlocal.hasDepartObligation()){
                    ___departAfterReadingAndUnlock();
                }else{
                    ___unlockByReadBiased();
                }
            }else{
                if(tranlocal.hasDepartObligation()){
                    ___departAfterReading();
                }
            }

            return null;
        }

        //it is a full blown update (so locked).

        final BooleanRefTranlocal specializedTranlocal = (BooleanRefTranlocal)tranlocal;

        ___value = specializedTranlocal.value;
        ___version = specializedTranlocal.version+1;
        ___lockOwner = null;
        Listeners listenersAfterWrite = ___listeners;

        if(listenersAfterWrite != null){
           listenersAfterWrite = ___removeListenersAfterWrite();
        }

        ___departAfterUpdateAndUnlock(___stm.globalConflictCounter, this);
        pool.put(specializedTranlocal);
        return listenersAfterWrite;
    }

    @Override
    public final void ___abort(
        final BetaTransaction transaction,
        final Tranlocal tranlocal,
        final BetaObjectPool pool) {

        if(tranlocal.getLockMode() != LOCKMODE_NONE){
            ___lockOwner = null;

            if(!tranlocal.isConstructing()){
                //depart and release the lock. This call is able to deal with readbiased and normal reads.
                ___departAfterFailureAndUnlock();
            }
        }else{
            if(tranlocal.hasDepartObligation()){
                ___departAfterFailure();
            }
        }

        pool.put((BooleanRefTranlocal)tranlocal);
    }

    @Override
    public void addDeferredValidator(BooleanPredicate validator){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"addDeferredValidator");
        }

        addDeferredValidator((BetaTransaction)tx, validator);
    }

    @Override
    public void addDeferredValidator(Transaction tx, BooleanPredicate validator){
        addDeferredValidator((BetaTransaction)tx, validator);
    }

    public void addDeferredValidator(BetaTransaction tx, BooleanPredicate validator){
        if(tx == null){
            throw new NullPointerException();
        }

        if(validator == null){
            tx.abort();
            throw new NullPointerException();
        }

        BooleanRefTranlocal write= tx.openForWrite(this, LOCKMODE_NONE);
        if(write.validators == null){
            write.validators = new BooleanPredicate[1];
            write.validators[0]=validator;
        }else{
            throw new TodoException();
        }
    }

    @Override
    public void atomicAddDeferredValidator(BooleanPredicate validator){
        if(validator == null){
            throw new NullPointerException();
        }
        throw new TodoException();
    }

    @Override
    public final void ensure(){
        Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"ensure");
        }

        ensure((BetaTransaction)tx);
    }

    @Override
    public final void ensure(Transaction tx){
        ensure((BetaTransaction)tx);
    }

    public final void ensure(BetaTransaction tx){
        tx.openForRead(this, LOCKMODE_UPDATE);
    }

    @Override
    public final boolean tryEnsure(){
        Transaction tx = getThreadLocalTransaction();

        if(tx!=null){
            throw new TransactionRequiredException(getClass(),"tryEnsure");
        }

        return tryEnsure((BetaTransaction)tx);
    }

    @Override
    public final boolean tryEnsure(final Transaction tx){
        return tryEnsure((BetaTransaction)tx);
    }

    public final boolean tryEnsure(BetaTransaction tx){
        return tx.tryLock(this, LOCKMODE_UPDATE);
    }

    @Override
    public final void deferredEnsure(){
        Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"deferredEnsure");
        }

        deferredEnsure((BetaTransaction)tx);
    }

    @Override
    public final void deferredEnsure(final Transaction tx){
        deferredEnsure((BetaTransaction)tx);
    }

    public final void deferredEnsure(final BetaTransaction tx){
        if(tx == null){
            throw new NullPointerException();
        }

        tx.materializeConflict(this);
    }

    @Override
    public final void privatize(){
        Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"privatize");
        }

        privatize((BetaTransaction)tx);
    }

    @Override
    public final void privatize(Transaction tx){
        privatize((BetaTransaction)tx);
    }

    public final void privatize(BetaTransaction tx){
        tx.openForRead(this, LOCKMODE_COMMIT);
    }

    @Override
    public final boolean tryPrivatize(){
        Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"tryPrivatize");
        }

        return tryPrivatize((BetaTransaction)tx);
    }

    @Override
    public final boolean tryPrivatize(Transaction tx){
        return tryPrivatize((BetaTransaction)tx);
    }

    public final boolean tryPrivatize(BetaTransaction tx){
        return tx.tryLock(this, LOCKMODE_COMMIT);
    }

    @Override
    public final void commute(BooleanFunction function){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"commute");
        }

        commute((BetaTransaction)tx, function);
    }

    @Override
    public final void commute(final Transaction tx, final BooleanFunction function){
        commute((BetaTransaction)tx, function);
    }

    public final void commute(BetaTransaction tx,BooleanFunction function){
        tx.commute(this, function);
    }

    @Override
    public final boolean atomicAlterAndGet(final BooleanFunction function){
        return atomicAlter(function, false);
    }

    @Override
    public final boolean alterAndGet(final BooleanFunction function){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"alterAndGet");
        }

        return alterAndGet((BetaTransaction)tx, function);
    }

    @Override
    public final boolean alterAndGet(final Transaction tx,final BooleanFunction function){
        return alterAndGet((BetaTransaction)tx, function);
    }

    public final boolean alterAndGet(final BetaTransaction tx,final BooleanFunction function){
        if(function == null){
            tx.abort();
            throw new NullPointerException("Function can't be null");
        }

        BooleanRefTranlocal write
            = (BooleanRefTranlocal)tx.openForWrite(this, LOCKMODE_NONE);

        boolean abort = true;
        try{
            write.value = function.call(write.value);
            abort = false;
        }finally{
            if(abort){
                tx.abort();
            }
        }
        return write.value;
    }

    @Override
    public final boolean atomicGetAndAlter(final BooleanFunction function){

        return atomicAlter(function,true);
    }

    private boolean atomicAlter(final BooleanFunction function,final boolean returnOld){
        if(function == null){
            throw new NullPointerException("Function can't be null");
        }

        final int arriveStatus = ___arriveAndLockOrBackoff();

        if(arriveStatus == ARRIVE_LOCK_NOT_FREE){
            throw new LockedException();
        }

        final boolean oldValue = ___value;
        boolean newValue;
        boolean abort = true;
        try{
            newValue = function.call(oldValue);
            abort = false;
        }finally{
            if(abort){
                ___departAfterFailureAndUnlock();
            }
        }

        if(oldValue == newValue){
            if(arriveStatus == ARRIVE_UNREGISTERED){
                ___unlockByReadBiased();
            } else{
                ___departAfterReadingAndUnlock();
            }

            return oldValue;
        }


        ___value = newValue;
        ___version++;

        Listeners listeners = ___removeListenersAfterWrite();

        ___departAfterUpdateAndUnlock(___stm.globalConflictCounter, this);

        if(listeners!=null){
           listeners.openAll(getThreadLocalBetaObjectPool());
        }

        return returnOld ? oldValue : newValue;
    }

    @Override
    public final boolean getAndAlter(final BooleanFunction function){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"getAndAlter");
        }

        return getAndAlter((BetaTransaction)tx, function);
    }

    @Override
    public final boolean getAndAlter(final Transaction tx,final BooleanFunction function){
        return getAndAlter((BetaTransaction)tx, function);
    }

    public final boolean getAndAlter(final BetaTransaction tx,final BooleanFunction function){
        if(function == null){
            tx.abort();
            throw new NullPointerException("Function can't be null");
        }

        BooleanRefTranlocal write
            = (BooleanRefTranlocal)tx.openForWrite(this, LOCKMODE_NONE);

        final boolean oldValue = write.value;
        boolean abort = true;
        try{
            write.value = function.call(write.value);
            abort  = false;
        }finally{
            if(abort){
                tx.abort();
            }
        }
        return oldValue;
    }

    @Override
    public final boolean atomicCompareAndSet(final boolean expectedValue,final boolean newValue){
        final int arriveStatus = ___arriveAndLockOrBackoff();

        if(arriveStatus == ARRIVE_LOCK_NOT_FREE){
            throw new LockedException();
        }

        final boolean currentValue = ___value;

        if(currentValue != expectedValue){
            ___departAfterFailureAndUnlock();
            return false;
        }

        if(expectedValue == newValue){
            if(arriveStatus == ARRIVE_UNREGISTERED){
                ___unlockByReadBiased();
            } else{
                ___departAfterReadingAndUnlock();
            }

            return true;
        }

        ___value = newValue;
        ___version++;
        Listeners listeners = ___removeListenersAfterWrite();

        ___departAfterUpdateAndUnlock(___stm.globalConflictCounter, this);

        if(listeners!=null){
            listeners.openAll(getThreadLocalBetaObjectPool());
        }

        return true;
    }

    @Override
    public final boolean getAndSet(final boolean value){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"getAndSet");
        }

        return getAndSet((BetaTransaction)tx, value);
    }

    public final boolean set(final boolean value){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"set");
        }

        return set((BetaTransaction)tx, value);
    }

    @Override
    public final boolean get(){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"get");
        }

        return get((BetaTransaction)tx);
    }

    @Override
    public final boolean get(final Transaction tx){
        return get((BetaTransaction)tx);
    }

    public final boolean get(final BetaTransaction transaction){
        return transaction.openForRead(this, LOCKMODE_NONE).value;
    }

    @Override
    public final boolean atomicGet(){
        int attempt = 1;
        do{
            if(!___hasCommitLock()){

                boolean read = ___value;

                if(!___hasCommitLock()){
                    return read;
                }
            }
            ___stm.defaultBackoffPolicy.delayedUninterruptible(attempt);
            attempt++;
        }while(attempt<=___stm.spinCount);

        throw new LockedException();
    }

    @Override
    public final boolean atomicWeakGet(){
       return ___value;
    }

    @Override
    public final boolean atomicSet(final boolean newValue){
        atomicGetAndSet(newValue);
        return newValue;
    }

    @Override
    public final boolean atomicGetAndSet(final boolean newValue){
        final int arriveStatus = ___arriveAndLockOrBackoff();

        if(arriveStatus == ARRIVE_LOCK_NOT_FREE){
            throw new LockedException();
        }

        final boolean oldValue = ___value;

        if(oldValue == newValue){
            if(arriveStatus == ARRIVE_UNREGISTERED){
                ___unlockByReadBiased();
            } else{
                ___departAfterReadingAndUnlock();
            }

            return newValue;
        }

        ___value = newValue;
        ___version++;

        Listeners listeners = ___removeListenersAfterWrite();

        ___departAfterUpdateAndUnlock(___stm.globalConflictCounter, this);

        if(listeners != null){
            BetaObjectPool pool = getThreadLocalBetaObjectPool();
            listeners.openAll(pool);
        }

        return oldValue;
    }

    @Override
    public final boolean set(Transaction tx, boolean value){
        return set((BetaTransaction)tx, value);
    }

    public final boolean set(final BetaTransaction tx,final boolean value){
        tx.openForWrite(this, LOCKMODE_NONE).value = value;
        return value;
    }

    @Override
    public final boolean getAndSet(final Transaction tx,final boolean value){
        return getAndSet((BetaTransaction)tx, value);
    }

    public final boolean getAndSet(final BetaTransaction tx,final boolean value){
        BooleanRefTranlocal write = tx.openForWrite(this, LOCKMODE_NONE);
        boolean oldValue = write.value;
        write.value = value;
        return oldValue;
    }

    @Override
    public final void await(boolean value){
        final Transaction tx = getThreadLocalTransaction();

        if(tx == null){
            throw new TransactionRequiredException(getClass(),"await");                                            
        }

        await((BetaTransaction)tx, value);
    }

    @Override
    public final void await(final Transaction tx,final boolean value){
        await((BetaTransaction)tx, value);
    }

    public final void await(final BetaTransaction tx,final boolean value){
        BooleanRefTranlocal read = tx.openForRead(this,LOCKMODE_NONE);
        if(read.value != value){
            StmUtils.retry();
        }
    }

    @Override
    public String toDebugString(){
        return String.format("Ref{orec=%s, version=%s, value=%s, hasListeners=%s)",
            ___toOrecString(),___version,___value, ___listeners!=null);
    }
}
