package org.multiverse.stms.beta.transactionalobjects;

import org.multiverse.functions.Function;
import org.multiverse.functions.IntFunction;
import org.multiverse.stms.beta.BetaObjectPool;

/**
 * The {@link Tranlocal} for the {@link IntRef).
 *
 * This class is generated.
 *
 * @author Peter Veentjer
 */
public final class IntRefTranlocal extends Tranlocal{

    public final static IntRefTranlocal LOCKED = new IntRefTranlocal(null,true);

    public int value;
    public CallableNode headCallable;

    public IntRefTranlocal(IntRef ref){
        super(ref, false);
    }

    public IntRefTranlocal(IntRef ref, boolean locked){
        super(ref, locked);
    }

    public IntRefTranlocal openForWrite(final BetaObjectPool pool) {
        assert isCommitted;

        IntRef _ref = (IntRef)owner;
        IntRefTranlocal tranlocal = pool.take(_ref);
        if (tranlocal == null) {
            tranlocal = new IntRefTranlocal(_ref);
        }

        tranlocal.read = this;
        tranlocal.value = value;
        return tranlocal;
    }

    public void evaluateCommutingFunctions(final BetaObjectPool  pool){
        assert isCommuting;

        IntRefTranlocal tranlocal = (IntRefTranlocal)read;
        value = tranlocal.value;

        CallableNode current = headCallable;
        do{
            value = current.callable.call(value);
            current = current.next;
        }while(current!=null);

        isDirty = tranlocal.value != value;
        isCommuting = false;
        headCallable = null;
    }

    public void addCommutingFunction(final IntFunction function, final BetaObjectPool pool){
        assert isCommuting;

        headCallable = new CallableNode(function, headCallable);
    }

    public void addCommutingFunction(final Function function, final BetaObjectPool pool){
        assert isCommuting;

        headCallable = new CallableNode(
            (IntFunction)function,
            headCallable);
    }

    public IntRefTranlocal openForCommute(final BetaObjectPool pool) {
        assert isCommitted;

        IntRef _ref = (IntRef)owner;
        IntRefTranlocal tranlocal = pool.take(_ref);
        if (tranlocal == null) {
            tranlocal = new IntRefTranlocal(_ref);
        }

        tranlocal.isCommuting = true;
        tranlocal.read = this;
        tranlocal.value = value;
        return tranlocal;
    }

    public void prepareForPooling(final BetaObjectPool pool) {
        owner = null;
        value = 0;
        read = null;
        isCommitted = false;
        isDirty = false;
        isCommuting = false;
        //todo: this should be pooled.
        headCallable = null;
    }

    public boolean calculateIsDirty() {
        //once committed, it never can become dirty (unless it is pooled and reused)
        if (isCommitted) {
            return false;
        }

        if (read == null) {
            //when the read is null, and it is an update, then is a tranlocal for a newly created
            //transactional object, since it certainly needs to be committed.
            isDirty = true;
            return true;
        }

        //check if it really is dirty.
        IntRefTranlocal _read = (IntRefTranlocal)read;
        isDirty = value != _read.value;
        return isDirty;
    }

    public static class CallableNode{
        public IntFunction callable;
        public CallableNode next;

        CallableNode(IntFunction callable, CallableNode next){
            this.callable = callable;
            this.next = next;
        }
    }
}