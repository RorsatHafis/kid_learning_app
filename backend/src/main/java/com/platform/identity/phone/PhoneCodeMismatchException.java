package com.platform.identity.phone;

/** A wrong code for the phone being verified (answered 422 with a field error on {@code code}). */
public class PhoneCodeMismatchException extends RuntimeException {

    public PhoneCodeMismatchException(String message) {
        super(message);
    }

}
