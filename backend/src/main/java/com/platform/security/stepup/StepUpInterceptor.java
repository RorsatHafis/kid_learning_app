package com.platform.security.stepup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * M8: refuses an action marked {@link RequiresStepUp} unless the request carries a valid step-up proof
 * ({@code X-Step-Up}) for this account and this sign-in. Runs before the controller, so nothing is done first.
 */
@Configuration
public class StepUpInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    public static final String HEADER = "X-Step-Up";

    private final StepUpService stepUp;

    public StepUpInterceptor(@Lazy StepUpService stepUp) {
        this.stepUp = stepUp;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RequiresStepUp.class)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UUID accountId)
                    || !(authentication.getDetails() instanceof UUID sessionFamilyId)) {
                throw new StepUpRequiredException();
            }
            stepUp.require(accountId, sessionFamilyId, request.getHeader(HEADER));
        }
        return true;
    }

}
