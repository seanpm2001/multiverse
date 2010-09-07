package org.multiverse.stms.beta.transactions;

import org.multiverse.api.Watch;
import org.multiverse.api.blocking.Latch;
import org.multiverse.api.exceptions.DeadTransactionException;
import org.multiverse.api.exceptions.SpeculativeConfigurationError;
import org.multiverse.api.exceptions.TodoException;
import org.multiverse.api.functions.*;
import org.multiverse.stms.beta.BetaStm;
import org.multiverse.stms.beta.Listeners;
import org.multiverse.stms.beta.transactionalobjects.*;

import static java.lang.String.format;

/**
 * A BetaTransaction tailored for dealing with 1 transactional object.
 *
 * This class is generated.
 *
 * @author Peter Veentjer
 */
public final class LeanMonoBetaTransaction extends AbstractLeanBetaTransaction {

    private Tranlocal attached;
    private boolean hasUpdates;

    public LeanMonoBetaTransaction(final BetaStm stm){
        this(new BetaTransactionConfiguration(stm).init());
    }

    public LeanMonoBetaTransaction(final BetaTransactionConfiguration config) {
        super(POOL_TRANSACTIONTYPE_LEAN_MONO, config);
        this.remainingTimeoutNs = config.timeoutNs;
    }



    @Override
    public final <E> RefTranlocal<E> openForRead(
        final BetaRef<E> ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                RefTranlocal<E> read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            RefTranlocal<E> read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            RefTranlocal<E> result = (RefTranlocal<E>)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        RefTranlocal<E> read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final <E> RefTranlocal<E> openForWrite(
        final BetaRef<E> ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            RefTranlocal<E> read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            RefTranlocal<E> result = pool.take(ref);
            if (result == null) {
                result = new RefTranlocal<E>(ref);
            }
            result.value = read.value;
            result.read = read;

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        RefTranlocal<E> result = (RefTranlocal<E>)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final RefTranlocal<E> read = result;
        result = pool.take(ref);
        if (result == null) {
            result = new RefTranlocal<E>(ref);
        }
        result.value = read.value;
        result.read = read;
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final <E> RefTranlocal<E> openForConstruction(
        final BetaRef<E> ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        RefTranlocal<E> result = (attached == null || attached.owner != ref) ? null : (RefTranlocal<E>)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result =  pool.take(ref);
        if(result == null){
            result = new RefTranlocal<E>(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public <E> void commute(
        BetaRef<E> ref, Function<E> function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }


    @Override
    public final  IntRefTranlocal openForRead(
        final BetaIntRef ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                IntRefTranlocal read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            IntRefTranlocal read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            IntRefTranlocal result = (IntRefTranlocal)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        IntRefTranlocal read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final  IntRefTranlocal openForWrite(
        final BetaIntRef ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            IntRefTranlocal read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            IntRefTranlocal result = pool.take(ref);
            if (result == null) {
                result = new IntRefTranlocal(ref);
            }
            result.value = read.value;
            result.read = read;

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        IntRefTranlocal result = (IntRefTranlocal)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final IntRefTranlocal read = result;
        result = pool.take(ref);
        if (result == null) {
            result = new IntRefTranlocal(ref);
        }
        result.value = read.value;
        result.read = read;
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final  IntRefTranlocal openForConstruction(
        final BetaIntRef ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        IntRefTranlocal result = (attached == null || attached.owner != ref) ? null : (IntRefTranlocal)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result =  pool.take(ref);
        if(result == null){
            result = new IntRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public  void commute(
        BetaIntRef ref, IntFunction function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }


    @Override
    public final  BooleanRefTranlocal openForRead(
        final BetaBooleanRef ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                BooleanRefTranlocal read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            BooleanRefTranlocal read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            BooleanRefTranlocal result = (BooleanRefTranlocal)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        BooleanRefTranlocal read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final  BooleanRefTranlocal openForWrite(
        final BetaBooleanRef ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            BooleanRefTranlocal read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            BooleanRefTranlocal result = pool.take(ref);
            if (result == null) {
                result = new BooleanRefTranlocal(ref);
            }
            result.value = read.value;
            result.read = read;

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        BooleanRefTranlocal result = (BooleanRefTranlocal)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final BooleanRefTranlocal read = result;
        result = pool.take(ref);
        if (result == null) {
            result = new BooleanRefTranlocal(ref);
        }
        result.value = read.value;
        result.read = read;
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final  BooleanRefTranlocal openForConstruction(
        final BetaBooleanRef ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        BooleanRefTranlocal result = (attached == null || attached.owner != ref) ? null : (BooleanRefTranlocal)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result =  pool.take(ref);
        if(result == null){
            result = new BooleanRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public  void commute(
        BetaBooleanRef ref, BooleanFunction function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }


    @Override
    public final  DoubleRefTranlocal openForRead(
        final BetaDoubleRef ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                DoubleRefTranlocal read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            DoubleRefTranlocal read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            DoubleRefTranlocal result = (DoubleRefTranlocal)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        DoubleRefTranlocal read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final  DoubleRefTranlocal openForWrite(
        final BetaDoubleRef ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            DoubleRefTranlocal read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            DoubleRefTranlocal result = pool.take(ref);
            if (result == null) {
                result = new DoubleRefTranlocal(ref);
            }
            result.value = read.value;
            result.read = read;

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        DoubleRefTranlocal result = (DoubleRefTranlocal)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final DoubleRefTranlocal read = result;
        result = pool.take(ref);
        if (result == null) {
            result = new DoubleRefTranlocal(ref);
        }
        result.value = read.value;
        result.read = read;
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final  DoubleRefTranlocal openForConstruction(
        final BetaDoubleRef ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        DoubleRefTranlocal result = (attached == null || attached.owner != ref) ? null : (DoubleRefTranlocal)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result =  pool.take(ref);
        if(result == null){
            result = new DoubleRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public  void commute(
        BetaDoubleRef ref, DoubleFunction function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }


    @Override
    public final  LongRefTranlocal openForRead(
        final BetaLongRef ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                LongRefTranlocal read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            LongRefTranlocal read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            LongRefTranlocal result = (LongRefTranlocal)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        LongRefTranlocal read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final  LongRefTranlocal openForWrite(
        final BetaLongRef ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            LongRefTranlocal read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            LongRefTranlocal result = pool.take(ref);
            if (result == null) {
                result = new LongRefTranlocal(ref);
            }
            result.value = read.value;
            result.read = read;

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        LongRefTranlocal result = (LongRefTranlocal)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final LongRefTranlocal read = result;
        result = pool.take(ref);
        if (result == null) {
            result = new LongRefTranlocal(ref);
        }
        result.value = read.value;
        result.read = read;
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final  LongRefTranlocal openForConstruction(
        final BetaLongRef ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        LongRefTranlocal result = (attached == null || attached.owner != ref) ? null : (LongRefTranlocal)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result =  pool.take(ref);
        if(result == null){
            result = new LongRefTranlocal(ref);
        }
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public  void commute(
        BetaLongRef ref, LongFunction function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }


    @Override
    public final  Tranlocal openForRead(
        final BetaTransactionalObject ref,
        boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForRead(ref);
        }

        if (ref == null) {
            return null;
        }

        lock = lock || config.lockReads;

        if(attached == null){
            //the transaction has no previous attached references.

            if(lock){
                Tranlocal read = ref.___lockAndLoad(config.spinCount, this);

                //if it was locked, lets abort.
                if (read.isLocked) {
                    throw abortOnReadConflict();
                }

                attached = read;
                return read;
            }

            Tranlocal read = ref.___load(config.spinCount);

            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            if(!read.isPermanent || config.trackReads){
                attached = read;
            }else{
                throw abortOnTooSmallSize(2);
            }

            return read;
        }

        //the transaction has a previous attached reference
        if(attached.owner == ref){
            //the reference is the one we are looking for.
            Tranlocal result = (Tranlocal)attached;

            if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
                throw abortOnReadConflict();
            }

            return result;
        }

        if(lock || config.trackReads){
            throw abortOnTooSmallSize(2);
        }

        Tranlocal read = ref.___load(config.spinCount);

        //if it was locked, lets abort.
        if (read.isLocked) {
            throw abortOnReadConflict();
        }

        throw abortOnTooSmallSize(2);
    }

    @Override
    public final  Tranlocal openForWrite(
        final BetaTransactionalObject ref, boolean lock) {

        if (status != ACTIVE) {
            throw abortOpenForWrite(ref);
        }

        if (ref == null) {
            throw abortOpenForWriteWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForWriteWhenReadonly(ref);
        }

        lock = lock || config.lockWrites;

        if(attached == null){
            //the transaction has no previous attached references.

            Tranlocal read = lock
                ? ref.___lockAndLoad(config.spinCount, this)
                : ref.___load(config.spinCount);

            //if it was locked, lets abort.
            if (read.isLocked) {
                throw abortOnReadConflict();
            }

            Tranlocal result = read.openForWrite(pool);

            hasUpdates = true;
            attached = result;
            return result;
        }

        //the transaction has a previous attached reference

        if(attached.owner != ref){
            throw abortOnTooSmallSize(2);
        }

        //the reference is the one we are looking for.
        Tranlocal result = (Tranlocal)attached;

        if(lock && !ref.___tryLockAndCheckConflict(this, config.spinCount, result)){
            throw abortOnReadConflict();
        }

        if(!result.isCommitted){
            return result;
        }

        final Tranlocal read = result;
        result = read.openForWrite(pool);
        hasUpdates = true;    
        attached = result;
        return result;
    }

    @Override
    public final  Tranlocal openForConstruction(
        final BetaTransactionalObject ref) {

        if (status != ACTIVE) {
           throw abortOpenForConstruction(ref);
        }

        if (ref == null) {
            throw abortOpenForConstructionWhenNullReference();
        }

        if (config.readonly) {
            throw abortOpenForConstructionWhenReadonly(ref);
        }

        Tranlocal result = (attached == null || attached.owner != ref) ? null : (Tranlocal)attached;

        if(result != null){
            if(result.isCommitted || result.read != null){
               throw abortOpenForConstructionWithBadReference(ref);
            }

            return result;
        }

        //check if there is room
        if (attached != null) {
            throw abortOnTooSmallSize(2);
        }

        if(ref.___unsafeLoad()!=null){
            throw abortOpenForConstructionWithBadReference(ref);
        }

        result = ref.___openForConstruction(pool);
        result.isDirty = DIRTY_TRUE;
        attached = result;
        return result;
    }

    public  void commute(
        BetaTransactionalObject ref, Function function){

        if (status != ACTIVE) {
            throw abortCommute(ref, function);
        }

        if(function == null){
            throw abortCommuteOnNullFunction(ref);
        }
    
        config.needsCommute();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
     }

 
    @Override
    public Tranlocal get(BetaTransactionalObject object){
        return attached == null || attached.owner!= object? null: attached;
    }

    // ============================= addWatch ===================================

    public void addWatch(BetaTransactionalObject object, Watch watch){
        throw new TodoException();
    }


    // ======================= abort =======================================

    @Override
    public final void abort() {
        if (status != ACTIVE && status != PREPARED) {
            switch (status) {
                case ABORTED:
                    return;
                case COMMITTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't abort an already aborted transaction",config.familyName));
                default:
                    throw new IllegalStateException();
            }
        }

        if (attached != null) {
            attached.owner.___abort(this, attached, pool);
        }

        status = ABORTED;

    }

    // ================== commit ===========================================

    @Override
    public final void commit() {
        if(status == COMMITTED){
            return;
        }

        if (status != ACTIVE && status != PREPARED) {
            switch (status) {
                case ABORTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't commit an already aborted transaction", config.familyName));
                case COMMITTED:
                    return;
                default:
                    throw new IllegalStateException();
            }
        }

        if(abortOnly){
            throw abortOnWriteConflict();
        }
            
        Listeners listeners = null;
        if(attached!=null){
            final boolean needsPrepare = status == ACTIVE && hasUpdates;
            if(config.dirtyCheck){
                if(needsPrepare && !doPrepareDirty()){
                    throw abortOnWriteConflict();
                }

                listeners = attached.owner.___commitDirty(attached, this, pool);
            }else{
                if(needsPrepare && !doPrepareAll()){
                    throw abortOnWriteConflict();
                }

                listeners = attached.owner.___commitAll(attached, this, pool);
            }
        }

        status = COMMITTED;

        if(listeners != null){
            listeners.openAll(pool);
        }
    }

    // ======================= prepare ============================

    @Override
    public final void prepare() {
        if(status == PREPARED){
            return;
        }

        if(status != ACTIVE){
            switch (status) {
                case PREPARED:
                    return;
                case ABORTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't prepare already aborted transaction", config.familyName));
                case COMMITTED:
                    throw new DeadTransactionException(
                        format("[%s] Can't prepare already committed transaction", config.familyName));
                default:
                    throw new IllegalStateException();
            }
        }

        if(abortOnly){
            throw abortOnWriteConflict();
        }

        if(hasUpdates){
            if(config.dirtyCheck){
                if(!doPrepareDirty()){
                    throw abortOnWriteConflict();
                }
            }else{
                if(!doPrepareAll()){
                    throw abortOnWriteConflict();
                }
            }
        }

        status = PREPARED;
    }

    private boolean doPrepareDirty(){
        if(config.lockWrites){
            return true;
        }

        if(attached.isCommitted){
            return true;
        }

        if (attached.calculateIsDirty()
                    && !attached.owner.___tryLockAndCheckConflict(this, config.spinCount, attached)){
            return false;
        }

        return true;
    }

    private boolean doPrepareAll(){
        if(config.lockWrites){
            return true;
        }
        
        if(attached.isCommitted){
            return true;
        }

        if(!attached.owner.___tryLockAndCheckConflict(this, config.spinCount, attached)){
            return false;
        }

        return true;
    }

    // ============================ registerChangeListenerAndAbort ===================

    @Override
    public final void registerChangeListenerAndAbort(final Latch listener) {
        if (status != ACTIVE) {
            throw abortOnFaultyStatusOfRegisterChangeListenerAndAbort();
        }

        if(!config.blockingAllowed){
            throw abortOnNoBlockingAllowed();
        }

        if( attached == null){
            throw abortOnNoRetryPossible();
        }

        final long listenerEra = listener.getEra();
        final BetaTransactionalObject owner = attached.owner;

        final boolean failure = owner.___registerChangeListener(listener, attached, pool, listenerEra)
                    == REGISTRATION_NONE;
        owner.___abort(this, attached, pool);
        status = ABORTED;

        if(failure){
            throw abortOnNoRetryPossible();
        }
    }

    // =========================== init ================================

    @Override
    public void init(BetaTransactionConfiguration transactionConfig){
        if(transactionConfig == null){
            abort();
            throw new NullPointerException();
        }

        if(status == ACTIVE || status == PREPARED){
            abort();
        }

        this.config = transactionConfig;
        hardReset();
    }

    // ========================= reset ===============================

    @Override
    public boolean softReset() {
        if (status == ACTIVE || status == PREPARED) {
            if(attached!=null){
                attached.owner.___abort(this, attached, pool);
            }
        }

        if(attempt >= config.getMaxRetries()){
            return false;
        }

        status = ACTIVE;
        hasUpdates = false;
        attempt++;
        abortOnly = false;
        attached = null;
        return true;
    }

    @Override
    public void hardReset(){
        if (status == ACTIVE || status == PREPARED) {
            if(attached!=null){
                attached.owner.___abort(this, attached, pool);
            }
        }

        hasUpdates = false;
        status = ACTIVE;
        abortOnly = false;        
        remainingTimeoutNs = config.timeoutNs;
        attached = null;
        attempt = 1;
    }

    // ================== orelse ============================

    @Override
    public final void startEitherBranch(){
        config.needsOrelse();
        abort();
        throw SpeculativeConfigurationError.INSTANCE;
    }

    @Override
    public final void endEitherBranch(){
        abort();
        throw new IllegalStateException();
    }

    @Override
    public final void startOrElseBranch(){
        abort();
        throw new IllegalStateException();
    }
}

