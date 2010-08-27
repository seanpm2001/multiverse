package org.multiverse.stms.beta.transactions;

import java.util.*;

import org.multiverse.api.*;
import org.multiverse.api.blocking.*;
import org.multiverse.api.exceptions.*;
import org.multiverse.api.functions.*;
import org.multiverse.api.lifecycle.*;
import org.multiverse.stms.beta.*;
import org.multiverse.stms.beta.transactionalobjects.*;
import org.multiverse.stms.beta.conflictcounters.*;

import static org.multiverse.stms.beta.ThreadLocalBetaObjectPool.*;

import java.util.concurrent.atomic.AtomicLong;
import static java.lang.String.format;


/**
 * A {@link BetaTransaction} for arbitrary size transactions.
 *
 * @author Peter Veentjer.
 */
public final class LeanArrayTreeBetaTransaction extends AbstractLeanBetaTransaction {

    public final static AtomicLong conflictScan = new AtomicLong();

    private LocalConflictCounter localConflictCounter;
    private Tranlocal[] array;
    private int size;
    private boolean hasReads;
    private boolean hasUntrackedReads;
    private boolean hasUpdates;

    public LeanArrayTreeBetaTransaction(BetaStm stm) {
        this(new BetaTransactionConfiguration(stm));
    }

    public LeanArrayTreeBetaTransaction(BetaTransactionConfiguration config) {
        super(POOL_TRANSACTIONTYPE_LEAN_ARRAYTREE, config);
        this.localConflictCounter = config.globalConflictCounter.createLocalConflictCounter();
        this.array = new Tranlocal[config.minimalArrayTreeSize];
        this.remainingTimeoutNs = config.timeoutNs;
    }

    public final LocalConflictCounter getLocalConflictCounter() {
        return localConflictCounter;
    }

    public int size(){
        return size;
    }

    public float getUsage(){
        return (size * 1.0f)/array.length;
    }


    @Override
    public <E> RefTranlocal<E> openForRead(
        final Ref<E> ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForRead(pool, ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        if (index > -1) {
            //we are lucky, at already is attached to the session
            RefTranlocal<E> found = (RefTranlocal<E>)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,found)){
                throw abortOnReadConflict(pool);
            }

            return found;
        }

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //none is found in this transaction, lets load it.
        final RefTranlocal<E> read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if (read.isLocked) {
            throw abortOnReadConflict(pool);
        }

        //make sure that there are no conflicts.
        if (hasReadConflict()) {
            ref.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        if(lock || !read.isPermanent || config.trackReads){
            attach(ref, read, identityHashCode, pool);
            size++;
        }else{
            hasUntrackedReads = true;
        }

        return read;
    }

    @Override
    public <E> RefTranlocal<E> openForWrite(
        final Ref<E>  ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);
        }

        //lets find the tranlocal
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        lock = lock || config.lockWrites;

        if(index >- 1){
            RefTranlocal<E> result = (RefTranlocal<E>)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,result)){
                throw abortOnReadConflict(pool);
            }

            if(!result.isCommitted){
                return result;
            }

            //it was opened for reading so we need to open it for writing.
            result = result.openForWrite(pool);
            hasUpdates = true;
            array[index]=result;
            return result;
        }

        //it was not previously attached to this transaction

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //the tranlocal was not loaded before in this transaction, now load it.
        final RefTranlocal<E> read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if(read.isLocked){
           throw abortOnReadConflict(pool);
        }

        if (hasReadConflict()) {
            read.owner.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        //open the tranlocal for writing.
        RefTranlocal<E> result =  pool.take(ref);
        if(result == null){
            result = new RefTranlocal<E>(ref);
        }
        result.read = read;
        result.value = read.value;
        hasUpdates = true;
        attach(ref, result, identityHashCode, pool);
        size++;        
        return result;
    }

    @Override
    public final <E> RefTranlocal<E> openForConstruction(
        final Ref<E> ref, final BetaObjectPool pool) {
        assert pool!=null;

        if (status != ACTIVE) {
            throw abortOpenForConstruction(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);            
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);               
        }

        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);

        if(index >- 1){
            final RefTranlocal<E> result = (RefTranlocal<E>)array[index];
            if(result.isCommitted || result.read!=null){
                throw abortOpenForConstructionWithBadReference(pool, ref);
            }

            return result;
        }

        if(ref.___unsafeLoad()!=null){
            abort(pool);
            throw abortOpenForConstructionWithBadReference(pool, ref);
        }

        RefTranlocal<E> result =  pool.take(ref);
        if(result == null){
            result = new RefTranlocal<E>(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attach(ref, result, identityHashCode, pool);
        size++;
        return result;
    }

    public <E> void commute(
        final Ref<E> ref, final BetaObjectPool pool, final Function<E> function){

        if (status != ACTIVE) {
            throw abortCommute(pool, ref, function);
        }

        config.needsCommute();
        abort(pool);
        throw SpeculativeConfigurationError.INSTANCE;
     }



    @Override
    public  IntRefTranlocal openForRead(
        final IntRef ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForRead(pool, ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        if (index > -1) {
            //we are lucky, at already is attached to the session
            IntRefTranlocal found = (IntRefTranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,found)){
                throw abortOnReadConflict(pool);
            }

            return found;
        }

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //none is found in this transaction, lets load it.
        final IntRefTranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if (read.isLocked) {
            throw abortOnReadConflict(pool);
        }

        //make sure that there are no conflicts.
        if (hasReadConflict()) {
            ref.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        if(lock || !read.isPermanent || config.trackReads){
            attach(ref, read, identityHashCode, pool);
            size++;
        }else{
            hasUntrackedReads = true;
        }

        return read;
    }

    @Override
    public  IntRefTranlocal openForWrite(
        final IntRef  ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);
        }

        //lets find the tranlocal
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        lock = lock || config.lockWrites;

        if(index >- 1){
            IntRefTranlocal result = (IntRefTranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,result)){
                throw abortOnReadConflict(pool);
            }

            if(!result.isCommitted){
                return result;
            }

            //it was opened for reading so we need to open it for writing.
            result = result.openForWrite(pool);
            hasUpdates = true;
            array[index]=result;
            return result;
        }

        //it was not previously attached to this transaction

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //the tranlocal was not loaded before in this transaction, now load it.
        final IntRefTranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if(read.isLocked){
           throw abortOnReadConflict(pool);
        }

        if (hasReadConflict()) {
            read.owner.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        //open the tranlocal for writing.
        IntRefTranlocal result =  pool.take(ref);
        if(result == null){
            result = new IntRefTranlocal(ref);
        }
        result.read = read;
        result.value = read.value;
        hasUpdates = true;
        attach(ref, result, identityHashCode, pool);
        size++;        
        return result;
    }

    @Override
    public final  IntRefTranlocal openForConstruction(
        final IntRef ref, final BetaObjectPool pool) {
        assert pool!=null;

        if (status != ACTIVE) {
            throw abortOpenForConstruction(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);            
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);               
        }

        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);

        if(index >- 1){
            final IntRefTranlocal result = (IntRefTranlocal)array[index];
            if(result.isCommitted || result.read!=null){
                throw abortOpenForConstructionWithBadReference(pool, ref);
            }

            return result;
        }

        if(ref.___unsafeLoad()!=null){
            abort(pool);
            throw abortOpenForConstructionWithBadReference(pool, ref);
        }

        IntRefTranlocal result =  pool.take(ref);
        if(result == null){
            result = new IntRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attach(ref, result, identityHashCode, pool);
        size++;
        return result;
    }

    public  void commute(
        final IntRef ref, final BetaObjectPool pool, final IntFunction function){

        if (status != ACTIVE) {
            throw abortCommute(pool, ref, function);
        }

        config.needsCommute();
        abort(pool);
        throw SpeculativeConfigurationError.INSTANCE;
     }



    @Override
    public  LongRefTranlocal openForRead(
        final LongRef ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForRead(pool, ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        if (index > -1) {
            //we are lucky, at already is attached to the session
            LongRefTranlocal found = (LongRefTranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,found)){
                throw abortOnReadConflict(pool);
            }

            return found;
        }

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //none is found in this transaction, lets load it.
        final LongRefTranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if (read.isLocked) {
            throw abortOnReadConflict(pool);
        }

        //make sure that there are no conflicts.
        if (hasReadConflict()) {
            ref.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        if(lock || !read.isPermanent || config.trackReads){
            attach(ref, read, identityHashCode, pool);
            size++;
        }else{
            hasUntrackedReads = true;
        }

        return read;
    }

    @Override
    public  LongRefTranlocal openForWrite(
        final LongRef  ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);
        }

        //lets find the tranlocal
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        lock = lock || config.lockWrites;

        if(index >- 1){
            LongRefTranlocal result = (LongRefTranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,result)){
                throw abortOnReadConflict(pool);
            }

            if(!result.isCommitted){
                return result;
            }

            //it was opened for reading so we need to open it for writing.
            result = result.openForWrite(pool);
            hasUpdates = true;
            array[index]=result;
            return result;
        }

        //it was not previously attached to this transaction

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //the tranlocal was not loaded before in this transaction, now load it.
        final LongRefTranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if(read.isLocked){
           throw abortOnReadConflict(pool);
        }

        if (hasReadConflict()) {
            read.owner.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        //open the tranlocal for writing.
        LongRefTranlocal result =  pool.take(ref);
        if(result == null){
            result = new LongRefTranlocal(ref);
        }
        result.read = read;
        result.value = read.value;
        hasUpdates = true;
        attach(ref, result, identityHashCode, pool);
        size++;        
        return result;
    }

    @Override
    public final  LongRefTranlocal openForConstruction(
        final LongRef ref, final BetaObjectPool pool) {
        assert pool!=null;

        if (status != ACTIVE) {
            throw abortOpenForConstruction(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);            
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);               
        }

        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);

        if(index >- 1){
            final LongRefTranlocal result = (LongRefTranlocal)array[index];
            if(result.isCommitted || result.read!=null){
                throw abortOpenForConstructionWithBadReference(pool, ref);
            }

            return result;
        }

        if(ref.___unsafeLoad()!=null){
            abort(pool);
            throw abortOpenForConstructionWithBadReference(pool, ref);
        }

        LongRefTranlocal result =  pool.take(ref);
        if(result == null){
            result = new LongRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attach(ref, result, identityHashCode, pool);
        size++;
        return result;
    }

    public  void commute(
        final LongRef ref, final BetaObjectPool pool, final LongFunction function){

        if (status != ACTIVE) {
            throw abortCommute(pool, ref, function);
        }

        config.needsCommute();
        abort(pool);
        throw SpeculativeConfigurationError.INSTANCE;
     }



    @Override
    public  Tranlocal openForRead(
        final BetaTransactionalObject ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForRead(pool, ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        if (index > -1) {
            //we are lucky, at already is attached to the session
            Tranlocal found = (Tranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,found)){
                throw abortOnReadConflict(pool);
            }

            return found;
        }

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //none is found in this transaction, lets load it.
        final Tranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if (read.isLocked) {
            throw abortOnReadConflict(pool);
        }

        //make sure that there are no conflicts.
        if (hasReadConflict()) {
            ref.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        if(lock || !read.isPermanent || config.trackReads){
            attach(ref, read, identityHashCode, pool);
            size++;
        }else{
            hasUntrackedReads = true;
        }

        return read;
    }

    @Override
    public  Tranlocal openForWrite(
        final BetaTransactionalObject  ref, boolean lock, final BetaObjectPool pool) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);
        }

        //lets find the tranlocal
        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);
        lock = lock || config.lockWrites;

        if(index >- 1){
            Tranlocal result = (Tranlocal)array[index];

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount,result)){
                throw abortOnReadConflict(pool);
            }

            if(!result.isCommitted){
                return result;
            }

            //it was opened for reading so we need to open it for writing.
            result = result.openForWrite(pool);
            hasUpdates = true;
            array[index]=result;
            return result;
        }

        //it was not previously attached to this transaction

        if(!hasReads){
            localConflictCounter.reset();
            hasReads = true;
        }

        //the tranlocal was not loaded before in this transaction, now load it.
        final Tranlocal read = lock
            ? ref.___lockAndLoad(config.spinCount, this)
            : ref.___load(config.spinCount);

        if(read.isLocked){
           throw abortOnReadConflict(pool);
        }

        if (hasReadConflict()) {
            read.owner.___abort(this, read, pool);
            throw abortOnReadConflict(pool);
        }

        //open the tranlocal for writing.
        Tranlocal result = read.openForWrite(pool);
        hasUpdates = true;
        attach(ref, result, identityHashCode, pool);
        size++;        
        return result;
    }

    @Override
    public final  Tranlocal openForConstruction(
        final BetaTransactionalObject ref, final BetaObjectPool pool) {
        assert pool!=null;

        if (status != ACTIVE) {
            throw abortOpenForConstruction(pool, ref);
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(pool, ref);            
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference(pool);               
        }

        final int identityHashCode = ref.___identityHashCode();
        final int index = findAttachedIndex(ref, identityHashCode);

        if(index >- 1){
            final Tranlocal result = (Tranlocal)array[index];
            if(result.isCommitted || result.read!=null){
                throw abortOpenForConstructionWithBadReference(pool, ref);
            }

            return result;
        }

        if(ref.___unsafeLoad()!=null){
            abort(pool);
            throw abortOpenForConstructionWithBadReference(pool, ref);
        }

        final Tranlocal result = ref.___openForConstruction(pool);
        result.isDirty = DIRTY_TRUE;
        attach(ref, result, identityHashCode, pool);
        size++;
        return result;
    }

    public  void commute(
        final BetaTransactionalObject ref, final BetaObjectPool pool, final Function function){

        if (status != ACTIVE) {
            throw abortCommute(pool, ref, function);
        }

        config.needsCommute();
        abort(pool);
        throw SpeculativeConfigurationError.INSTANCE;
     }


 
    @Override
    public Tranlocal get(BetaTransactionalObject ref){
        final int indexOf = findAttachedIndex(ref, ref.___identityHashCode());
        if(indexOf == -1){
            return null;
        }

        return array[indexOf];
    }

    public int findAttachedIndex(final BetaTransactionalObject ref, final int hash){
        int jump = 0;
        boolean goLeft = true;

        do{
            final int offset = goLeft?-jump:jump;
            final int index = (hash + offset) % array.length;

            final Tranlocal current = array[index];
            if(current == null){
                return -1;
            }

            if(current.owner == ref){
                return index;
            }

            final int currentHash = current.owner.___identityHashCode();
            goLeft = currentHash > hash;
            jump = jump == 0 ? 1 : jump*2;
        }while(jump < array.length);

        return -1;
    }

    private void attach(final BetaTransactionalObject ref, final Tranlocal tranlocal, final int hash, final BetaObjectPool pool){
        int jump = 0;
        boolean goLeft = true;

        do{
            final int offset = goLeft?-jump:jump;
            final int index = (hash + offset) % array.length;

            Tranlocal current = array[index];
            if(current == null){
                array[index] = tranlocal;
                return;
            }

            final int currentHash = current.owner.___identityHashCode();
            goLeft = currentHash > hash;
            jump = jump == 0?1:jump*2;
        }while(jump < array.length);

        expand(pool);
        attach(ref, tranlocal, hash, pool);
    }

    private void expand(final BetaObjectPool pool){
        Tranlocal[] oldArray = array;
        int newSize = oldArray.length*2;
        array = pool.takeTranlocalArray(newSize);
        if(array == null){
            array = new Tranlocal[newSize];
        }

        for(int k=0; k < oldArray.length; k++){
            final Tranlocal tranlocal = oldArray[k];

            if(tranlocal != null){
               attach(tranlocal.owner, tranlocal, tranlocal.owner.___identityHashCode(),pool);
            }
        }

        pool.putTranlocalArray(oldArray);
    }

    private boolean hasReadConflict() {
        if(config.lockReads) {
            return false;
        }

        if(hasUntrackedReads){
            return localConflictCounter.syncAndCheckConflict();
        }

        if(size == 0){
            return false;
        }

        if (!localConflictCounter.syncAndCheckConflict()) {
            return false;
        }

        for (int k = 0; k < array.length; k++) {
            final Tranlocal tranlocal = array[k];
            if (tranlocal != null && tranlocal.owner.___hasReadConflict(tranlocal, this)) {
                return true;
            }
        }

        return false;
    }

    // ============================= addWatch ===================================

    public void addWatch(BetaTransactionalObject object, Watch watch){
        throw new TodoException();
    }

    // ============================== abort ===================================================

    @Override
    public void abort() {
        abort(getThreadLocalBetaObjectPool());
    }

    @Override
    public void abort(final BetaObjectPool pool) {
        switch (status) {
            case ACTIVE:
                //fall through
            case PREPARED:
                status = ABORTED;
                if(size>0){
                    for (int k = 0; k < array.length; k++) {
                        final Tranlocal tranlocal = array[k];
                        if(tranlocal != null){
                            tranlocal.owner.___abort(this, tranlocal, pool);
                        }
                    }
                }

              break;
          case ABORTED:
              break;
            case COMMITTED:
                throw new DeadTransactionException(
                    format("[%s] Can't abort an already committed transaction",config.familyName));
            default:
                throw new IllegalStateException();
        }
    }

    // ============================== commit ==================================================

    @Override
    public void commit() {
        commit(getThreadLocalBetaObjectPool());
    }

    @Override
    public void commit(final BetaObjectPool pool) {
        if (status != ACTIVE) {
            switch (status) {
                case PREPARED:
                    break;
                case ABORTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't commit an already aborted transaction",config.familyName));
                case COMMITTED:
                    return;
                default:
                    throw new IllegalStateException();
            }
        }

        if(abortOnly){
            throw abortOnWriteConflict(pool);
        }

        Listeners[] listenersArray = null;
        if(size>0){
            final boolean needsPrepare = status == ACTIVE && hasUpdates;
            if(config.dirtyCheck){
                if(needsPrepare && !doPrepareDirty(pool)){
                    throw abortOnWriteConflict(pool);
                }

                listenersArray = commitDirty(pool);
            }else{
                if(needsPrepare && !doPrepareAll(pool)){
                    throw abortOnWriteConflict(pool);
                }

                listenersArray = commitAll(pool);
            }
        }
        status = COMMITTED;

        if(listenersArray != null){
            Listeners.openAll(listenersArray, pool);
        }
    }

    private Listeners[] commitAll(final BetaObjectPool pool) {
        Listeners[] listenersArray = null;

        int listenersArrayIndex = 0;
        for (int k = 0; k < array.length; k++) {
            Tranlocal tranlocal = array[k];
            if(tranlocal != null){
                final Listeners listeners = tranlocal.owner.___commitAll(tranlocal, this, pool, config.globalConflictCounter);

                if(listeners != null){
                    if(listenersArray == null){
                        int length = array.length - k;
                        listenersArray = pool.takeListenersArray(length);
                        if(listenersArray == null){
                            listenersArray = new Listeners[length];
                        }
                    }
                    listenersArray[listenersArrayIndex]=listeners;
                    listenersArrayIndex++;
                }
            }
        }

        return listenersArray;
    }

    private Listeners[] commitDirty(final BetaObjectPool pool) {
        Listeners[] listenersArray = null;

        int listenersArrayIndex = 0;
        for (int k = 0; k < array.length; k++) {
            Tranlocal tranlocal = array[k];
            if(tranlocal == null){
                continue;
            }

            if(tranlocal.isDirty == DIRTY_UNKNOWN){
                tranlocal.calculateIsDirty();
            }
            
            final Listeners listeners = tranlocal.owner.___commitDirty(tranlocal, this, pool, config.globalConflictCounter);

            if(listeners != null){
                if(listenersArray == null){
                    int length = array.length - k;
                    listenersArray = pool.takeListenersArray(length);
                    if(listenersArray == null){
                        listenersArray = new Listeners[length];
                    }
                }
                listenersArray[listenersArrayIndex]=listeners;
                listenersArrayIndex++;
            }
        }

        return listenersArray;
    }

    // ============================== prepare ==================================================

    @Override
    public void prepare() {
        prepare(getThreadLocalBetaObjectPool());
    }

    @Override
    public void prepare(BetaObjectPool pool) {
        if (status != ACTIVE) {
            switch (status) {
                case PREPARED:
                     return;
                case ABORTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't prepare an already aborted transaction",config.familyName));
                case COMMITTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't prepare already committed transaction",config.familyName));
                default:
                    throw new IllegalStateException();

            }
        }

        if(abortOnly){
            throw abortOnWriteConflict(pool);
        }

        if(hasUpdates){
            if(config.dirtyCheck){
                if(!doPrepareDirty(pool)){
                    throw abortOnWriteConflict(pool);
                }
            }else{
                if(!doPrepareAll(pool)){
                    throw abortOnWriteConflict(pool);
                }
            }
        }

        status = PREPARED;
    }

    private boolean doPrepareAll(final BetaObjectPool pool) {
        if(config.lockWrites){
            return true;
        }

        final int spinCount = config.spinCount;

        for (int k = 0; k < array.length; k++) {
            final Tranlocal tranlocal = array[k];

            if (tranlocal==null || tranlocal.isCommitted){
                continue;
            }

            if(!tranlocal.owner.___tryLockAndCheckConflict(this, spinCount, tranlocal)){
               return false;
            }
        }

        return true;
    }

    private boolean doPrepareDirty(final BetaObjectPool pool) {
        if(config.lockWrites){
            return true;
        }

        final int spinCount = config.spinCount;

        for (int k = 0; k < array.length; k++) {
            final Tranlocal tranlocal = array[k];

            if(tranlocal == null || tranlocal.isCommitted){
                continue;
            }

            if (tranlocal.calculateIsDirty()) {
                if(!tranlocal.owner.___tryLockAndCheckConflict(this, spinCount, tranlocal)){
                    return false;
                }
            }
        }

        return true;
    }

    // ============================ registerChangeListener ===============================

    @Override
    public void registerChangeListenerAndAbort(final Latch listener){
        registerChangeListenerAndAbort(listener, getThreadLocalBetaObjectPool());
    }

    @Override
    public void registerChangeListenerAndAbort(final Latch listener, final BetaObjectPool pool) {
        if (status != ACTIVE) {
            throw abortOnFaultyStatusOfRegisterChangeListenerAndAbort(pool);
        }

        if(!config.blockingAllowed){
            throw abortOnNoBlockingAllowed(pool);
        }

        if( size == 0){
            throw abortOnNoRetryPossible(pool);
        }

        final long listenerEra = listener.getEra();
        boolean furtherRegistrationNeeded = true;
        boolean atLeastOneRegistration = false;
        if(size>0){
            for(int k=0; k < array.length; k++){
                final Tranlocal tranlocal = array[k];

                if(tranlocal != null){
                    final BetaTransactionalObject owner = tranlocal.owner;

                    if(furtherRegistrationNeeded){
                        switch(owner.___registerChangeListener(listener, tranlocal, pool, listenerEra)){
                            case REGISTRATION_DONE:
                                atLeastOneRegistration = true;
                                break;
                            case REGISTRATION_NOT_NEEDED:
                                furtherRegistrationNeeded = false;
                                atLeastOneRegistration = true;
                                break;
                            case REGISTRATION_NONE:
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                    }

                    owner.___abort(this, tranlocal, pool);
                }
            }
        }

        status = ABORTED;
        if(!atLeastOneRegistration){
            throw abortOnNoRetryPossible(pool);
        }
    }

    // ============================== reset ========================================

    @Override
    public boolean softReset(){
        return softReset(getThreadLocalBetaObjectPool());
    }

    @Override
    public boolean softReset(final BetaObjectPool pool) {
        if (status == ACTIVE || status == PREPARED) {
            abort(pool);
        }

        if(attempt>=config.getMaxRetries()){
            return false;
        }

        if(array.length>config.minimalArrayTreeSize){
            pool.putTranlocalArray(array);
            array = pool.takeTranlocalArray(config.minimalArrayTreeSize);
            if(array == null){
                array = new Tranlocal[config.minimalArrayTreeSize];
            }
        }else{
            Arrays.fill(array, null);
        }

        status = ACTIVE;
        abortOnly = false;
        hasReads = false;
        hasUpdates = false;
        hasUntrackedReads = false;
        size = 0;
        attempt++;
        return true;
    }

    @Override
    public void hardReset(){
        hardReset(getThreadLocalBetaObjectPool());
    }

    @Override
    public void hardReset(final BetaObjectPool pool){
        if (status == ACTIVE || status == PREPARED) {
            abort(pool);
        }

        if(array.length>config.minimalArrayTreeSize){
            pool.putTranlocalArray(array);
            array = pool.takeTranlocalArray(config.minimalArrayTreeSize);
            if(array == null){
                array = new Tranlocal[config.minimalArrayTreeSize];
            }
        }else{
            Arrays.fill(array, null);
        }

        status = ACTIVE;
        abortOnly = false;
        hasUpdates = false;
        hasReads = false;
        hasUntrackedReads = false;
        attempt = 1;
        remainingTimeoutNs = config.timeoutNs;
        size = 0;
    }

    // ============================== init =======================================

    @Override
    public void init(BetaTransactionConfiguration transactionConfig){
        init(transactionConfig, getThreadLocalBetaObjectPool());
    }

    @Override
    public void init(BetaTransactionConfiguration transactionConfig, BetaObjectPool pool){
        if(transactionConfig == null){
            abort(pool);
            throw new NullPointerException();
        }

        if(status == ACTIVE || status == PREPARED){
            abort(pool);
        }

        this.config = transactionConfig;
        hardReset(pool);
    }

    // ================== orelse ============================

    // ================== orelse ============================

    @Override
    public final void startEitherBranch(){
        startEitherBranch(getThreadLocalBetaObjectPool());
    }

    @Override
    public final void startEitherBranch(BetaObjectPool pool){
        config.needsOrelse();
        abort(pool);
        throw SpeculativeConfigurationError.INSTANCE;
    }

    @Override
    public final void endEitherBranch(){
        endEitherBranch(getThreadLocalBetaObjectPool());
    }

    @Override
    public final void endEitherBranch(BetaObjectPool pool){
        abort(pool);
        throw new IllegalStateException();
    }

    @Override
    public final void startOrElseBranch(){
        startOrElseBranch(getThreadLocalBetaObjectPool());
    }

    @Override
    public final void startOrElseBranch(BetaObjectPool pool){
        abort(pool);
        throw new IllegalStateException();
    }
}
