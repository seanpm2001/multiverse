package org.multiverse.api.exceptions;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;

/**
 * A {@link ControlFlowError} that indicates that current transaction implementation can't deal
 * with more transactional objects than it can handle. This Error is useful for the STM
 * to speculative selection of a better performing implementation. So it can start with a very
 * fast transaction that only is able to deal with one or a few transactional objects and it
 * able to grow to more advanced but slower transaction implementations
 *
 * @author Peter Veentjer.
 */
public class TransactionTooSmallError extends ControlFlowError {

    public final static boolean reuse = parseBoolean(getProperty(
            TransactionTooSmallError.class.getName() + ".reuse", "true"));

    public static final TransactionTooSmallError INSTANCE = new TransactionTooSmallError();

    public TransactionTooSmallError() {
    }

    public TransactionTooSmallError(String message) {
        super(message);
    }

    public TransactionTooSmallError(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionTooSmallError(Throwable cause) {
        super(cause);
    }
}
