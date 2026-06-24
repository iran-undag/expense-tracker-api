package com.example.expensetracker.exception;

import java.util.Collection;

public class InvalidSortPropertyException extends RuntimeException {

    public InvalidSortPropertyException(String property, Collection<String> allowedProperties) {
        super("Invalid sort property '" + property + "'. Allowed properties: " + allowedProperties);
    }
}
