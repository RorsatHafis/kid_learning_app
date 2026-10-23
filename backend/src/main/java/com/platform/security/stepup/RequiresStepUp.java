package com.platform.security.stepup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * M8: this action needs a recent re-entry of the password on this sign-in (StepUpInterceptor): payments confirmed,
 * rejected or refunded by hand, and plans given or changed by an administrator - actions that move money or access.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresStepUp {
}
