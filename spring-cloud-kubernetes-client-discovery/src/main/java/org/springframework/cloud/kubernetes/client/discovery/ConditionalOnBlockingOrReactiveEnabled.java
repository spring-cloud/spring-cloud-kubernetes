/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.client.discovery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.context.annotation.Conditional;

/**
 * Conditional that is resolved to active when either
 * {@link ConditionalOnBlockingOrReactiveEnabled} or
 * {@link ConditionalOnReactiveDiscoveryEnabled} matches.
 *
 * @author wind57
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Conditional(ConditionalOnBlockingOrReactiveEnabled.OnBlockingOrReactiveEnabled.class)
public @interface ConditionalOnBlockingOrReactiveEnabled {

	class OnBlockingOrReactiveEnabled extends AnyNestedCondition {

		OnBlockingOrReactiveEnabled() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnBlockingDiscoveryEnabled
		static class OnBlockingEnabled {

		}

		@ConditionalOnReactiveDiscoveryEnabled
		static class OnReactiveEnabled {

		}

	}

}
