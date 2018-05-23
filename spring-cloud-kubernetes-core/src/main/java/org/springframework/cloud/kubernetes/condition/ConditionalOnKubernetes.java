package org.springframework.cloud.kubernetes.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnKubernetesCondition.class)
public @interface ConditionalOnKubernetes {

	/**
	 * If set, KubernetesClient must be adaptable to this class
	 * if the Condition is to pass
	 */
	Class<?> classClientMustAdaptTo() default Void.class;

	/**
	 * Configures whether the value configured version shall be considered the
	 * upper exclusive, lower inclusive boundary or exact version
	 */
	Range range() default Range.EQUAL_OR_NEWER;

	/**
	 * The Kubernetes version to check for (major.minor). Use range to specify whether the
	 * configured value is an upper-exclusive or lower-inclusive boundary.
	 */
	String version() default "";

	enum Range {

		EXACT,

		EQUAL_OR_NEWER,

		OLDER_THAN
	}
}
