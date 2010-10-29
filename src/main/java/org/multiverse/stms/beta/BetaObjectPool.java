package org.multiverse.stms.beta;

import org.multiverse.api.blocking.DefaultRetryLatch;
import org.multiverse.stms.beta.transactionalobjects.*;

import java.util.ArrayList;

/**
 * A pool for tranlocals. The pool is not threadsafe and should be connected to a thread (can
 * be stored in a threadlocal). Eventually the performance of the stm will be limited to the rate
 * of cleanup, and using a pool seriously improves scalability.
 * <p/>
 * Improvement: atm there is only one single type of tranlocal. If there are more types of tranlocals,
 * each class needs to have an index. This index can be used to determine the type of ref. If the pool
 * contains an array of arrays, where the first array is index based on the type of the ref, finding the
 * second array (that contains pooled tranlocals) can be found easily.
 * <p/>
 * ObjectPool is not thread safe and should not be shared between threads.
 *
 * This class is generated.
 *
 * @author Peter Veentjer
 */
public final class BetaObjectPool {

    private final static boolean ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm,beta.BetaObjectPool.enabled","true"));

    private final static boolean TRANLOCAL_POOLING_ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.tranlocalPooling",""+ENABLED));

    private final static boolean TRANLOCALARRAY_POOLING_ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.tranlocalArrayPooling",""+ENABLED));

    private final static boolean LATCH_POOLING_ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.latchPooling",""+ENABLED));

    private final static boolean LISTENER_POOLING_ENABLED  = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.listenersPooling",""+ENABLED));

    private final static boolean LISTENERSARRAY_POOLING_ENABLED  = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.listenersArrayPooling",""+ENABLED));

    private final static boolean ARRAYLIST_POOLING_ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.arrayListPooling",""+ENABLED));

    private final static boolean CALLABLENODE_POOLING_ENABLED = Boolean.parseBoolean(
        System.getProperty("org.multiverse.stm.beta.BetaObjectPool.callableNodePooling",""+ENABLED));

    private final boolean tranlocalPoolingEnabled;
    private final boolean tranlocalArrayPoolingEnabled;
    private final boolean latchPoolingEnabled;
    private final boolean listenersPoolingEnabled;
    private final boolean listenersArrayPoolingEnabled;
    private final boolean arrayListPoolingEnabled;
    private final boolean callableNodePoolingEnabled;

    private final BetaRefTranlocal[] tranlocalsBetaRef = new BetaRefTranlocal[100];
    private int lastUsedBetaRef = -1;
    private final BetaIntRefTranlocal[] tranlocalsBetaIntRef = new BetaIntRefTranlocal[100];
    private int lastUsedBetaIntRef = -1;
    private final BetaBooleanRefTranlocal[] tranlocalsBetaBooleanRef = new BetaBooleanRefTranlocal[100];
    private int lastUsedBetaBooleanRef = -1;
    private final BetaDoubleRefTranlocal[] tranlocalsBetaDoubleRef = new BetaDoubleRefTranlocal[100];
    private int lastUsedBetaDoubleRef = -1;
    private final BetaLongRefTranlocal[] tranlocalsBetaLongRef = new BetaLongRefTranlocal[100];
    private int lastUsedBetaLongRef = -1;
    private TranlocalPool[] pools = new TranlocalPool[100];

    private DefaultRetryLatch[] defaultRetryLatchPool = new DefaultRetryLatch[10];
    private int defaultRetryLatchPoolIndex = -1;

    private Listeners[] listenersPool = new Listeners[100];
    private int listenersPoolIndex = -1;

    private ArrayList[] arrayListPool = new ArrayList[10];
    private int arrayListPoolIndex = -1;

    private CallableNode[] callableNodePool = new CallableNode[10];
    private int callableNodePoolIndex = -1;

    public BetaObjectPool() {
        arrayListPoolingEnabled = ARRAYLIST_POOLING_ENABLED;
        tranlocalArrayPoolingEnabled = TRANLOCALARRAY_POOLING_ENABLED;
        tranlocalPoolingEnabled = TRANLOCAL_POOLING_ENABLED;
        latchPoolingEnabled = LATCH_POOLING_ENABLED;
        listenersPoolingEnabled = LISTENER_POOLING_ENABLED;
        listenersArrayPoolingEnabled = LISTENERSARRAY_POOLING_ENABLED;
        callableNodePoolingEnabled = CALLABLENODE_POOLING_ENABLED;
    }

    /**
     * Takes a BetaRefTranlocal from the pool for the specified BetaRef.
     *
     * @param owner the BetaRef to get the BetaRefTranlocal for.
     * @return the pooled tranlocal, or null if none is found.
     * @throws NullPointerException if owner is null.
     */
    public BetaRefTranlocal take(final BetaRef owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        if (lastUsedBetaRef == -1) {
            return new BetaRefTranlocal(owner);
        }

        BetaRefTranlocal tranlocal = tranlocalsBetaRef[lastUsedBetaRef];
        tranlocal.owner = owner;
        tranlocalsBetaRef[lastUsedBetaRef] = null;
        lastUsedBetaRef--;
        return tranlocal;
    }

    /**
     * Puts an old BetaRefTranlocal in this pool. If the tranlocal is allowed to be null,
     * the call is ignored. The same goes for when the tranlocal is permanent, since you
     * can't now how many transactions are still using it.
     *
     * @param tranlocal the BetaRefTranlocal to pool.
     */
    public void put(final BetaRefTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled) {
            return;
        }

        if (lastUsedBetaRef == tranlocalsBetaRef.length - 1) {
            return;
        }

        tranlocal.prepareForPooling(this);
        lastUsedBetaRef++;
        tranlocalsBetaRef[lastUsedBetaRef] = tranlocal;
    }

    /**
     * Takes a BetaIntRefTranlocal from the pool for the specified BetaIntRef.
     *
     * @param owner the BetaIntRef to get the BetaIntRefTranlocal for.
     * @return the pooled tranlocal, or null if none is found.
     * @throws NullPointerException if owner is null.
     */
    public BetaIntRefTranlocal take(final BetaIntRef owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        if (lastUsedBetaIntRef == -1) {
            return new BetaIntRefTranlocal(owner);
        }

        BetaIntRefTranlocal tranlocal = tranlocalsBetaIntRef[lastUsedBetaIntRef];
        tranlocal.owner = owner;
        tranlocalsBetaIntRef[lastUsedBetaIntRef] = null;
        lastUsedBetaIntRef--;
        return tranlocal;
    }

    /**
     * Puts an old BetaIntRefTranlocal in this pool. If the tranlocal is allowed to be null,
     * the call is ignored. The same goes for when the tranlocal is permanent, since you
     * can't now how many transactions are still using it.
     *
     * @param tranlocal the BetaIntRefTranlocal to pool.
     */
    public void put(final BetaIntRefTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled) {
            return;
        }

        if (lastUsedBetaIntRef == tranlocalsBetaIntRef.length - 1) {
            return;
        }

        tranlocal.prepareForPooling(this);
        lastUsedBetaIntRef++;
        tranlocalsBetaIntRef[lastUsedBetaIntRef] = tranlocal;
    }

    /**
     * Takes a BetaBooleanRefTranlocal from the pool for the specified BetaBooleanRef.
     *
     * @param owner the BetaBooleanRef to get the BetaBooleanRefTranlocal for.
     * @return the pooled tranlocal, or null if none is found.
     * @throws NullPointerException if owner is null.
     */
    public BetaBooleanRefTranlocal take(final BetaBooleanRef owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        if (lastUsedBetaBooleanRef == -1) {
            return new BetaBooleanRefTranlocal(owner);
        }

        BetaBooleanRefTranlocal tranlocal = tranlocalsBetaBooleanRef[lastUsedBetaBooleanRef];
        tranlocal.owner = owner;
        tranlocalsBetaBooleanRef[lastUsedBetaBooleanRef] = null;
        lastUsedBetaBooleanRef--;
        return tranlocal;
    }

    /**
     * Puts an old BetaBooleanRefTranlocal in this pool. If the tranlocal is allowed to be null,
     * the call is ignored. The same goes for when the tranlocal is permanent, since you
     * can't now how many transactions are still using it.
     *
     * @param tranlocal the BetaBooleanRefTranlocal to pool.
     */
    public void put(final BetaBooleanRefTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled) {
            return;
        }

        if (lastUsedBetaBooleanRef == tranlocalsBetaBooleanRef.length - 1) {
            return;
        }

        tranlocal.prepareForPooling(this);
        lastUsedBetaBooleanRef++;
        tranlocalsBetaBooleanRef[lastUsedBetaBooleanRef] = tranlocal;
    }

    /**
     * Takes a BetaDoubleRefTranlocal from the pool for the specified BetaDoubleRef.
     *
     * @param owner the BetaDoubleRef to get the BetaDoubleRefTranlocal for.
     * @return the pooled tranlocal, or null if none is found.
     * @throws NullPointerException if owner is null.
     */
    public BetaDoubleRefTranlocal take(final BetaDoubleRef owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        if (lastUsedBetaDoubleRef == -1) {
            return new BetaDoubleRefTranlocal(owner);
        }

        BetaDoubleRefTranlocal tranlocal = tranlocalsBetaDoubleRef[lastUsedBetaDoubleRef];
        tranlocal.owner = owner;
        tranlocalsBetaDoubleRef[lastUsedBetaDoubleRef] = null;
        lastUsedBetaDoubleRef--;
        return tranlocal;
    }

    /**
     * Puts an old BetaDoubleRefTranlocal in this pool. If the tranlocal is allowed to be null,
     * the call is ignored. The same goes for when the tranlocal is permanent, since you
     * can't now how many transactions are still using it.
     *
     * @param tranlocal the BetaDoubleRefTranlocal to pool.
     */
    public void put(final BetaDoubleRefTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled) {
            return;
        }

        if (lastUsedBetaDoubleRef == tranlocalsBetaDoubleRef.length - 1) {
            return;
        }

        tranlocal.prepareForPooling(this);
        lastUsedBetaDoubleRef++;
        tranlocalsBetaDoubleRef[lastUsedBetaDoubleRef] = tranlocal;
    }

    /**
     * Takes a BetaLongRefTranlocal from the pool for the specified BetaLongRef.
     *
     * @param owner the BetaLongRef to get the BetaLongRefTranlocal for.
     * @return the pooled tranlocal, or null if none is found.
     * @throws NullPointerException if owner is null.
     */
    public BetaLongRefTranlocal take(final BetaLongRef owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        if (lastUsedBetaLongRef == -1) {
            return new BetaLongRefTranlocal(owner);
        }

        BetaLongRefTranlocal tranlocal = tranlocalsBetaLongRef[lastUsedBetaLongRef];
        tranlocal.owner = owner;
        tranlocalsBetaLongRef[lastUsedBetaLongRef] = null;
        lastUsedBetaLongRef--;
        return tranlocal;
    }

    /**
     * Puts an old BetaLongRefTranlocal in this pool. If the tranlocal is allowed to be null,
     * the call is ignored. The same goes for when the tranlocal is permanent, since you
     * can't now how many transactions are still using it.
     *
     * @param tranlocal the BetaLongRefTranlocal to pool.
     */
    public void put(final BetaLongRefTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled) {
            return;
        }

        if (lastUsedBetaLongRef == tranlocalsBetaLongRef.length - 1) {
            return;
        }

        tranlocal.prepareForPooling(this);
        lastUsedBetaLongRef++;
        tranlocalsBetaLongRef[lastUsedBetaLongRef] = tranlocal;
    }

    public BetaTranlocal take(final BetaTransactionalObject owner) {
        if (owner == null) {
            throw new NullPointerException();
        }

        int classIndex = owner.___getClassIndex();

        if(classIndex == -1){
            return owner.___newTranlocal();
        }

        switch(classIndex){
            case 0:
                return take((BetaRef)owner);
            case 1:
                return take((BetaIntRef)owner);
            case 2:
                return take((BetaBooleanRef)owner);
            case 3:
                return take((BetaDoubleRef)owner);
            case 4:
                return take((BetaLongRef)owner);
        }

        if(classIndex >= pools.length){
            return owner.___newTranlocal();
        }

        TranlocalPool pool = pools[classIndex];
        if(pool.lastUsed == -1){
            return owner.___newTranlocal();
        }

        BetaTranlocal tranlocal = pool.tranlocals[pool.lastUsed];
        tranlocal.owner = owner;
        pool.tranlocals[pool.lastUsed] = null;
        pool.lastUsed--;
        return tranlocal;
    }

    /**
     * Puts a BetaTranlocal in the pool.
     *
     */
    public void put(final BetaTranlocal tranlocal) {
        if (!tranlocalPoolingEnabled || tranlocal == null) {
            return;
        }

        BetaTransactionalObject owner = tranlocal.owner;
        int classIndex = owner.___getClassIndex();

        if(classIndex == -1){
            return;
        }

        switch(classIndex){
            case 0:
                put((BetaRefTranlocal)tranlocal);
                return;
            case 1:
                put((BetaIntRefTranlocal)tranlocal);
                return;
            case 2:
                put((BetaBooleanRefTranlocal)tranlocal);
                return;
            case 3:
                put((BetaDoubleRefTranlocal)tranlocal);
                return;
            case 4:
                put((BetaLongRefTranlocal)tranlocal);
                return;
        }

        if(classIndex >= pools.length){
            TranlocalPool[] newPools = new TranlocalPool[pools.length * 2];
            System.arraycopy(pools, 0, newPools, 0, pools.length);
            pools = newPools;
        }

        TranlocalPool pool = pools[classIndex];
        if(pool == null){
            pool = new TranlocalPool();
            pools[classIndex]=pool;
        }

        if(pool.lastUsed == pool.tranlocals.length - 1){
            return;
        }

        tranlocal.prepareForPooling(this);
        pool.lastUsed++;
        pool.tranlocals[pool.lastUsed] = tranlocal;
    }

    static class TranlocalPool{
        int lastUsed = -1;
        BetaTranlocal[] tranlocals = new BetaTranlocal[100];
    }

    private BetaTranlocal[][] tranlocalArrayPool = new BetaTranlocal[8193][];

    /**
     * Puts a BetaTranlocal array in the pool.
     *
     * @param array the BetaTranlocal array to put in the pool.
     * @throws NullPointerException is array is null.
     */
    public void putTranlocalArray(final BetaTranlocal[] array){
        if(array == null){
            throw new NullPointerException();
        }

        if(!tranlocalArrayPoolingEnabled){
            return;
        }

        if(array.length-1>tranlocalArrayPool.length){
            return;
        }

        int index = array.length;

        if(tranlocalArrayPool[index]!=null){
            return;
        }

        //lets clean the array
        for(int k=0;k < array.length;k++){
            array[k]=null;
        }

        tranlocalArrayPool[index]=array;
    }

    /**
     * Takes a tranlocal array from the pool with the given size.
     *
     * @param size the size of the array to take
     * @return the BetaTranlocal array taken from the pool, or null if none available.
     * @throws IllegalArgumentException if size smaller than 0.
     */
    public BetaTranlocal[] takeTranlocalArray(final int size){
        if(size<0){
            throw new IllegalArgumentException();
        }

        if(!tranlocalArrayPoolingEnabled){
            return new BetaTranlocal[size];
        }

        int index = size;

        if(index >= tranlocalArrayPool.length){
            return new BetaTranlocal[size];
        }

        if(tranlocalArrayPool[index]==null){
            return new BetaTranlocal[size];
        }

        BetaTranlocal[] array = tranlocalArrayPool[index];
        tranlocalArrayPool[index]=null;
        return array;
    }

  /**
     * Takes a CallableNode from the pool, or null if none is available.
     *
     * @return the CallableNode from the pool, or null if none available.
     */
    public CallableNode takeCallableNode(){
        if(!callableNodePoolingEnabled || callableNodePoolIndex == -1){
            return new CallableNode();
        }

        CallableNode node = callableNodePool[callableNodePoolIndex];
        callableNodePool[callableNodePoolIndex]=null;
        callableNodePoolIndex--;
        return node;
    }

    /**
     * Puts a CallableNode in the pool.
     *
     * @param node the CallableNode to pool.
     * @throws NullPointerException if node is null.
     */
    public void putCallableNode(CallableNode node){
        if(node == null){
            throw new NullPointerException();
        }

        if(!callableNodePoolingEnabled || callableNodePoolIndex == callableNodePool.length-1){
            return;
        }

        node.prepareForPooling();
        callableNodePoolIndex++;
        callableNodePool[callableNodePoolIndex]=node;
    }

    /**
     * Takes a DefaultRetryLatch from the pool, or null if none is available.
     *
     * @return the DefaultRetryLatch from the pool, or null if none available.
     */
    public DefaultRetryLatch takeDefaultRetryLatch(){
        if(!latchPoolingEnabled || defaultRetryLatchPoolIndex == -1){
            return new DefaultRetryLatch();
        }

        DefaultRetryLatch latch = defaultRetryLatchPool[defaultRetryLatchPoolIndex];
        defaultRetryLatchPool[defaultRetryLatchPoolIndex]=null;
        defaultRetryLatchPoolIndex--;
        return latch;
    }

    /**
     * Puts a CheapLatch in the pool. Before the latch is put in the pool, it is prepared for pooling.
     *
     * @param latch the CheapLatch to pool.
     * @throws NullPointerException if latch is null.
     */
    public void putDefaultRetryLatch(DefaultRetryLatch latch){
        if(latch == null){
            throw new NullPointerException();
        }

        if(!latchPoolingEnabled || defaultRetryLatchPoolIndex == defaultRetryLatchPool.length-1){
            return;
        }

        latch.prepareForPooling();
        defaultRetryLatchPoolIndex++;
        defaultRetryLatchPool[defaultRetryLatchPoolIndex]=latch;
    }

    // ====================== array list ===================================

    /**
     * Takes an ArrayList from the pool, The returned ArrayList is cleared.
     *
     * @return the ArrayList from the pool, or null of none is found.
     */
    public ArrayList takeArrayList(){
        if(!arrayListPoolingEnabled || arrayListPoolIndex == -1){
            return new ArrayList();
        }

        ArrayList list = arrayListPool[arrayListPoolIndex];
        arrayListPool[arrayListPoolIndex]=null;
        arrayListPoolIndex--;
        return list;
    }

    /**
     * Puts an ArrayList in this pool. The ArrayList will be cleared before being placed
     * in the pool.
     *
     * @param list the ArrayList to place in the pool.
     * @throws NullPointerException if list is null.
     */
    public void putArrayList(ArrayList list){
        if(list == null){
            throw new NullPointerException();
        }

        if(!arrayListPoolingEnabled || arrayListPoolIndex == arrayListPool.length-1){
            return;
        }

        list.clear();
        arrayListPoolIndex++;
        arrayListPool[arrayListPoolIndex]=list;
    }


    // ============================ listeners ==================================

    /**
     * Takes a Listeners object from the pool.
     *
     * @return the Listeners object taken from the pool. or null if none is taken.
     */
    public Listeners takeListeners(){
        if(!listenersPoolingEnabled || listenersPoolIndex == -1){
            return new Listeners();
        }

        Listeners listeners = listenersPool[listenersPoolIndex];
        listenersPool[listenersPoolIndex]=null;
        listenersPoolIndex--;
        return listeners;
    }

    /**
     * Puts a Listeners object in the pool. The Listeners object is preparedForPooling before
     * it is put in the pool. The next Listeners object is ignored (the next field itself is ignored).
     *
     * @param listeners the Listeners object to pool.
     * @throws NullPointerException is listeners is null.
     */
    public void putListeners(Listeners listeners){
        if(listeners == null){
            throw new NullPointerException();
        }

        if(!listenersPoolingEnabled || listenersPoolIndex == listenersPool.length-1){
            return;
        }

        listeners.prepareForPooling();
        listenersPoolIndex++;
        listenersPool[listenersPoolIndex]=listeners;
    }

    // ============================= listeners array =============================

    private Listeners[] listenersArray = new Listeners[100000];

    /**
     * Takes a Listeners array from the pool. If an array is returned, it is completely nulled.
     *
     * @param minimalSize the minimalSize of the Listeners array.
     * @return the found Listeners array, or null if none is taken from the pool.
     * @throws IllegalArgumentException if minimalSize is smaller than 0.
     */
    public Listeners[] takeListenersArray(int minimalSize){
        if( minimalSize < 0 ){
            throw new IllegalArgumentException();
        }

        if(!listenersArrayPoolingEnabled){
            return new Listeners[minimalSize];
        }

        if(listenersArray == null || listenersArray.length < minimalSize){
            return new Listeners[minimalSize];
        }

        Listeners[] result = listenersArray;
        listenersArray = null;
        return result;
    }

    /**
     * Puts a Listeners array in the pool.
     *
     * Listeners array should be nulled before being put in the pool. It is not going to be done by this
     * BetaObjectPool but should be done when the listeners on the listeners array are notified.
     *
     * @param listenersArray the array to pool.
     * @throws NullPointerException if listenersArray is null.
     */
    public void putListenersArray(Listeners[] listenersArray){
        if(listenersArray == null){
            throw new NullPointerException();
        }

        if(!listenersArrayPoolingEnabled){
            return;
        }

        if(this.listenersArray!=listenersArray){
            return;
        }

        this.listenersArray = listenersArray;
    }
}
