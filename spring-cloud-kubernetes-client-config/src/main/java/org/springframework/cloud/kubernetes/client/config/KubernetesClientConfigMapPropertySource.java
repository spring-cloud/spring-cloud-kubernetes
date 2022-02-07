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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.getApplicationNamespace;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientConfigMapPropertySource extends ConfigMapPropertySource {
	public KubernetesClientConfigMapPropertySource(SourceData sourceData) {
		super(sourceData);
	}

//	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigMapPropertySource.class);
//
//	public KubernetesClientConfigMapPropertySource(KubernetesClientConfigContext context) {
//		super(getName(name, getApplicationNamespace(namespace, "Config Map", null)),
//				getData(coreV1Api, name, getApplicationNamespace(namespace, "Config Map", null), environment, prefix,
//						includeProfileSpecificSources, failFast));
//	}
//
//	private static SourceData getData(KubernetesClientConfigContext context) {
//	}

}
