package com.platform.identity.phone;

/** No text-message provider is set up, so phone numbers can't be verified yet (answered 503). */
public class PhoneUnavailableException extends RuntimeException {

    public PhoneUnavailableException() {
        super("Phone numbers can't be verified yet: text messages aren't set up");
    }

}
