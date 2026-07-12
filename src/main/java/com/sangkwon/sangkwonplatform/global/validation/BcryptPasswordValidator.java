package com.sangkwon.sangkwonplatform.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class BcryptPasswordValidator implements ConstraintValidator<BcryptPassword, String> {

    private int min;

    @Override
    public void initialize(BcryptPassword annotation) {
        this.min = annotation.min();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int characters = value.codePointCount(0, value.length());
        return characters >= min && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
