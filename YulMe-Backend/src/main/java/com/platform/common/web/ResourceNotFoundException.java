package com.platform.common.web;

public class ResourceNotFoundException extends RuntimeException {
 
    public ResourceNotFoundException(String resourceType, Object id) {

        super("%s %s not found".formatted(resourceType, id));
        
    }
 
}
