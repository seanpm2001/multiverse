package org.multiverse.transactional.collections;

import org.multiverse.annotations.TransactionalMethod;

import java.util.concurrent.BlockingQueue;

/**
 * A transactional {@link BlockingQueue} interface.
 *
 * @author Peter Veentjer.
 * @param <E>
 */
public interface TransactionalQueue<E> extends BlockingQueue<E>, TransactionalCollection<E> {

    @Override
    @TransactionalMethod(readonly = true)
    int remainingCapacity();

    @Override
    @TransactionalMethod(readonly = true)
    E element();

    @Override
    @TransactionalMethod(readonly = true)
    E peek();
}