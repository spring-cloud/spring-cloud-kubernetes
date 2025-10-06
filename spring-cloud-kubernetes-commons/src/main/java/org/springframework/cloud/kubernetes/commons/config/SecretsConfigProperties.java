/*
 * Copyright 2013-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;

/**
 * Properties for configuring Kubernetes secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
@ConfigurationProperties(SecretsConfigProperties.PREFIX)
public record SecretsConfigProperties(@DefaultValue("false") boolean enabled,
	@DefaultValue List<SourceConfigProperties.Source> sources, @DefaultValue Map<String, String> labels,
	String name, String namespace, boolean useNameAsPrefix,
	@DefaultValue("true") boolean includeProfileSpecificSources, boolean failFast,
	@DefaultValue RetryProperties retry, @DefaultValue("BATCH") ReadType readType) {

	/**
	 * Prefix for Kubernetes secrets configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.secrets";

}
