package com.banking.api.util;

import java.security.SecureRandom;

/**
 * Generates the one-time credentials handed to a freshly approved account. The value is
 * produced at the moment of activation, emailed to the new user, and never returned through
 * any API response or persisted anywhere - the account must change it at first sign-in.
 */
public final class TemporaryPasswordGenerator {

    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*?-_=+";
    private static final String ALL = LOWER + UPPER + DIGITS + SPECIAL;
    private static final int LENGTH = 14;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswordGenerator() {
    }

    /** A 14-character password with at least one upper, lower, digit and special character. */
    public static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        // Guarantee one character from each class so the realm password policy always passes.
        password.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        password.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        for (int i = 4; i < LENGTH; i++) {
            password.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        // Fisher-Yates so the guaranteed characters are not positionally predictable.
        for (int i = password.length() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = password.charAt(i);
            password.setCharAt(i, password.charAt(j));
            password.setCharAt(j, tmp);
        }
        return password.toString();
    }
}
