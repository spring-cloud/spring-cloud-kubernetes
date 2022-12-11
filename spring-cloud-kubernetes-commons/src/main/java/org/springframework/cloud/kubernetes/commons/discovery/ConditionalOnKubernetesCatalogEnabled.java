package org.springframework.cloud.kubernetes.commons.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides a more succinct conditional
 * <code>spring.cloud.kubernetes.discovery.catalog-services-watch.enabled</code>.
 *
 * @author wind57
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ConditionalOnProperty(value = "spring.cloud.kubernetes.discovery.catalog-services-watch.enabled", matchIfMissing = true)
public @interface ConditionalOnKubernetesCatalogEnabled {
}
