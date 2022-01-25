/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesSecretsEnabled;

/**
 * {@link org.springframework.context.annotation.Conditional @Conditional} that only
 * matches when Spring Cloud Kubernetes, Kubernetes secrets, Kubernetes secrets fail-fast
 * and Kubernetes secrets retry are enabled.
 *
 * @author Isik Erhan
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnKubernetesSecretsEnabled
@ConditionalOnKubernetesSecretsFailFastEnabled
@ConditionalOnProperty(prefix = SecretsConfigProperties.PREFIX + ".retry", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public @interface ConditionalOnKubernetesSecretsRetryEnabled {

}
