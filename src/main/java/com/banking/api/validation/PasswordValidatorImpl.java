package com.banking.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PasswordValidatorImpl implements ConstraintValidator<PasswordValidator, String> {

    private int minLength;
    private int maxLength;
    private boolean requireDigit;
    private boolean requireLowercase;
    private boolean requireUppercase;
    private boolean requireSpecialChar;

    @Override
    public void initialize(PasswordValidator constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.maxLength = constraintAnnotation.maxLength();
        this.requireDigit = constraintAnnotation.requireDigit();
        this.requireLowercase = constraintAnnotation.requireLowercase();
        this.requireUppercase = constraintAnnotation.requireUppercase();
        this.requireSpecialChar = constraintAnnotation.requireSpecialChar();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        // Check length
        if (password.length() < minLength || password.length() > maxLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Password must be between " + minLength + " and " + maxLength + " characters"
            ).addConstraintViolation();
            return false;
        }

        StringBuilder errors = new StringBuilder();

        // Check for digit
        if (requireDigit && !Pattern.compile("[0-9]").matcher(password).find()) {
            errors.append("at least one digit, ");
        }

        // Check for lowercase
        if (requireLowercase && !Pattern.compile("[a-z]").matcher(password).find()) {
            errors.append("at least one lowercase letter, ");
        }

        // Check for uppercase
        if (requireUppercase && !Pattern.compile("[A-Z]").matcher(password).find()) {
            errors.append("at least one uppercase letter, ");
        }

        // Check for special character
        if (requireSpecialChar && !Pattern.compile("[@#$%^&+=!*()_+{}|:<>?,.~`-]").matcher(password).find()) {
            errors.append("at least one special character, ");
        }

        if (errors.length() > 0) {
            String errorMsg = "Password must contain: " + errors.substring(0, errors.length() - 2);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(errorMsg).addConstraintViolation();
            return false;
        }

        return true;
    }
}