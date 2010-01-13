package org.openstreetmap.util.exceptions;

public class XMLException extends Exception {
    public XMLException(Throwable cause) {
        super(cause);
    }

    public XMLException(String message, Throwable cause) {
        super(message, cause);
    }
}
