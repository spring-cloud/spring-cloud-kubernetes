/*
 * Copyright © 2013 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2013-present the original author or authors.
 */

package org.springframework.cloud.kubernetes.commons;

import org.yaml.snakeyaml.Yaml;

import org.springframework.beans.factory.config.YamlProcessor;

/**
 * Exposes the construction of a {@link Yaml} object from Spring Frameworks
 * {@link YamlProcessor} to be reused by consumers.
 */
public final class KubernetesYamlProcessor extends YamlProcessor {

	private static final KubernetesYamlProcessor yamlProcessor = new KubernetesYamlProcessor();

	private KubernetesYamlProcessor() {
	}

	public static Yaml getYaml() {
		return yamlProcessor.createYaml();
	}

}
