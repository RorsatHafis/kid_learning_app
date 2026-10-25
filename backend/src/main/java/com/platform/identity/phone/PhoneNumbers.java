package com.platform.identity.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

/**
 * M9b: phone numbers as typed by a person, normalized to E.164 (libphonenumber, D-12). Cambodia first: a number
 * without a country code is read as Cambodian ("012 345 678" is +85512345678); any country's number is accepted
 * with its code ("+66 81 234 5678").
 */
public final class PhoneNumbers {

    static final String DEFAULT_REGION = "KH";
    static final String NOT_A_NUMBER = "Enter a phone number that can receive a text message, with its country code if it isn't Cambodian";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /** The E.164 form of a valid mobile (or mobile-capable) number; refused otherwise. */
    public static String normalize(String typed) {
        if (typed == null || typed.isBlank() || typed.length() > 40) {
            throw new IllegalArgumentException(NOT_A_NUMBER);
        }
        try {
            Phonenumber.PhoneNumber number = UTIL.parse(typed.strip(), DEFAULT_REGION);
            PhoneNumberUtil.PhoneNumberType type = UTIL.getNumberType(number);
            boolean textable = type == PhoneNumberUtil.PhoneNumberType.MOBILE
                    || type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
            if (!UTIL.isValidNumber(number) || !textable) {
                throw new IllegalArgumentException(NOT_A_NUMBER);
            }
            return UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new IllegalArgumentException(NOT_A_NUMBER);
        }
    }

    /** The number as people write it ("+855 12 345 678"); null for none. */
    public static String display(String e164) {
        if (e164 == null) {
            return null;
        }
        try {
            return UTIL.format(UTIL.parse(e164, DEFAULT_REGION), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            return e164;
        }
    }

    /** The number with only its last three digits showing, for messages and audit ("+855 ••• ••• 678"). */
    public static String masked(String e164) {
        String last = e164.substring(Math.max(0, e164.length() - 3));
        try {
            int code = UTIL.parse(e164, DEFAULT_REGION).getCountryCode();
            return "+" + code + " ••• ••• " + last;
        } catch (NumberParseException e) {
            return "••• " + last;
        }
    }

}
