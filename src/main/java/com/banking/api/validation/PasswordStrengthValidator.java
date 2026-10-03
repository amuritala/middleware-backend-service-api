package com.banking.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordStrengthValidator implements ConstraintValidator<PasswordValidator, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        int score = calculateStrength(password);

        if (score < 3) {
            context.disableDefaultConstraintViolation();
            String message = getStrengthMessage(score);
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            return false;
        }

        return true;
    }

    private int calculateStrength(String password) {
        int score = 0;

        // Length check
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;

        // Character variety checks
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[@#$%^&+=!*()_+{}|:<>?,.~`-].*")) score++;

        // No common patterns
        if (!password.toLowerCase().contains("password")) score++;
        if (!password.toLowerCase().contains("123456")) score++;

        return score;
    }

    private String getStrengthMessage(int score) {
        if (score < 3) {
            return "Password is too weak. Please use at least 8 characters with a mix of uppercase, lowercase, numbers, and special characters.";
        } else if (score < 5) {
            return "Password is medium strength. Consider adding more variety for better security.";
        } else {
            return "Password is strong.";
        }
    }
}