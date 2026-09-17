package com.platform.identity.dto;

public record LoginCommand(String email, String rawPassword) {
    
}
