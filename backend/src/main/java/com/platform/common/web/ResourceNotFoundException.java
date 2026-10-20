package com.platform.common.web;

/**
 * A requested resource doesn't exist (answered as 404). Also used where saying 'forbidden' would reveal that
 * something exists.
 */
public class ResourceNotFoundException extends RuntimeException {
 
    public ResourceNotFoundException(String resourceType, Object id) {

        super("%s %s not found".formatted(resourceType, id));
        
    }
 
}
