package dev.evoworld.persistence;

/**
 * A save could not be written or read.
 *
 * <p>Unchecked, because there is nothing a caller deep in a run can usefully
 * do about a full disk except report it - and the runner does.
 */
public final class SaveFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SaveFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
