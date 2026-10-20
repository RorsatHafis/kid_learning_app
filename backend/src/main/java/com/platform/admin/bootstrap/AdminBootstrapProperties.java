package com.platform.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for creating the first platform administrator at startup (platform.bootstrap-admin). Off by default;
 * the email and password come only from the environment, never from the repository.
 */
@Component
@ConfigurationProperties(prefix = "platform.bootstrap-admin")
public class AdminBootstrapProperties {

    private boolean enabled;
    private String email;
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

}
