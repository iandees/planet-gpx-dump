package org.openstreetmap.util.exceptions;

public class NoUsersFoundException extends DatabaseException {
    public NoUsersFoundException() {
        super("No users could be found. Something is wrong! (i.e. MAX(id) doesn't return a result)");
    }
}
