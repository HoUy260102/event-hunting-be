package com.example.event.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneValidator implements ConstraintValidator<Phone, String> {
    private static final String PHONE_REGEX = "^(?:0|\\+84)(?:3|5|7|8|9)\\d{8}$";
    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) return false;
        return value.matches(PHONE_REGEX);
    }
}
