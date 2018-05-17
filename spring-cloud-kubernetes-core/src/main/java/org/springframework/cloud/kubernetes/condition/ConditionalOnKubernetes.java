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
}
