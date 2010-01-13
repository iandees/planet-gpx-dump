package org.openstreetmap.util.exceptions;

public class DatabaseException extends Exception {
    public DatabaseException(Throwable cause) {
        super(cause);
    }

    public DatabaseException(String cause) {
        super(cause);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}