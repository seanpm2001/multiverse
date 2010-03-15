package org.multiverse.transactional.arrays;

import org.multiverse.annotations.Exclude;
import org.multiverse.annotations.TransactionalMethod;
import org.multiverse.annotations.TransactionalObject;
import org.multiverse.transactional.DefaultTransactionalReference;
import org.multiverse.transactional.TransactionalReference;

/**
 * An Transactional array. The elements in the array are of type {@link TransactionalReference}.
 * The elements are created eagerly. In the future some kind of lazy creation mechanism will be added
 * as well, but for the time being this is the simplest solution.
 * <p/>
 * Just as with normal arrays, the length of the TransactionalReferenceArray can't change after
 * is is created. See the {@link org.multiverse.transactional.collections.TransactionalArrayList} for
 * an alternative.
 *
 * @author Peter Veentjer.
 * @param <E>
 */
@TransactionalObject
public final class TransactionalReferenceArray<E> {

    private final TransactionalReference<E>[] array;

    /**
     * Creates a new TransactionalReferenceArray with the specified length.
     *
     * @param length the length of the TransactionalReferenceArray.
     * @throws IllegalArgumentException if length smaller than 0.
     */
    public TransactionalReferenceArray(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }

        array = new TransactionalReference[length];
        for (int k = 0; k < length; k++) {
            array[k] = new DefaultTransactionalReference<E>();
        }
    }

    private TransactionalReferenceArray(TransactionalReference<E>[] array) {
        if (array == null) {
            throw new NullPointerException();
        }

        this.array = array;
    }

    /**
     * Gets the element at the specified index.
     *
     * @param index the index of the element to get.
     * @return the element at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @TransactionalMethod(readonly = true)
    public E get(int index) {
        return array[index].get();
    }

    /**
     * Sets the element at the specified index
     *
     * @param index the index of the element to set.
     * @return the previous content of element at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    public E set(int index, E item) {
        return array[index].set(item);
    }

    /**
     * Returns the length of this TransactionalReferenceArray .
     *
     * @return the length of this TransactionalReferenceArray .
     */
    @Exclude
    public int length() {
        return array.length;
    }

    /**
     * Remove elements.
     *
     * @param firstIndex
     * @param lastIndex
     */
    public void shiftLeft(int firstIndex, int lastIndex) {
        if (firstIndex < 1 || firstIndex >= array.length) {
            throw new IndexOutOfBoundsException();
        }

        //[a,b,c
        //[b,c,null
        for (int k = firstIndex; k <= lastIndex; k++) {
            E right = array[k].get();
            array[k - 1].set(right);
        }

        array[lastIndex].set(null);
    }

    /**
     * Create room
     *
     * @param firstIndex
     * @param lastIndex
     */
    public void shiftRight(int firstIndex, int lastIndex) {
        if (firstIndex < 0 || firstIndex >= array.length - 1) {
            throw new IndexOutOfBoundsException();
        }

        //[a   , b, c
        //[null, a, b, c


        //endIndex not checked
        for (int k = lastIndex; k >= firstIndex; k--) {
            E left = array[k].get();
            array[k + 1].set(left);
        }

        array[firstIndex].set(null);
    }

    /**
     * Copies the content of this TransactionalReferenceArray to a bigger TransactionalReferenceArray.
     *
     * @param newLength the new capacity of the bigger TransactionalReferenceArray
     * @return the new TransactionalReferenceArray
     * @throws IllegalArgumentException if newCapacity is smaller than the current capacity.
     */
    public TransactionalReferenceArray<E> copyToBiggerArray(int newLength) {
        if (newLength < array.length) {
            throw new IllegalArgumentException();
        }

        TransactionalReference[] newArray = new TransactionalReference[newLength];
        System.arraycopy(array, 0, newArray, 0, array.length);

        for (int k = array.length; k < newLength; k++) {
            newArray[k] = new DefaultTransactionalReference();
        }

        return new TransactionalReferenceArray<E>(newArray);
    }

    @Override
    @TransactionalMethod(readonly = true)
    public String toString() {
        int length = length();
        if (length == 0) {
            return "[]";
        }

        StringBuffer sb = new StringBuffer("[");
        for (int k = 0; k < length; k++) {
            sb.append(array[k].get());

            if (k < length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Sends back an array containing the value of the references in this TransactionalReferenceArray.
     *
     * @param size the number of items to copy. If the size is smaller than the array length, it will
     *             only copy till that length.
     * @return the copied array.
     * @throws IllegalArgumentException if size smaller than 0 or larger than the length
     */
    @TransactionalMethod(readonly = true)
    public Object[] toArray(int size) {
        if (size < 0 || size > array.length) {
            throw new IllegalArgumentException();
        }

        Object[] result = new Object[size];
        for (int k = 0; k < size; k++) {
            result[k] = array[k].get();
        }
        return result;
    }
}
