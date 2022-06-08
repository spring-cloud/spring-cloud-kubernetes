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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.EnumMap;
import java.util.Optional;

import org.springframework.cloud.kubernetes.commons.config.NormalizedSourceType;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.cloud.kubernetes.commons.config.SourceDataEntriesProcessor;
import org.springframework.core.env.MapPropertySource;

/**
 * A {@link MapPropertySource} that uses Kubernetes config maps.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 * @author Michael Moudatsos
 * @author Isik Erhan
 */
public final class Fabric8ConfigMapPropertySource extends SourceDataEntriesProcessor {

	private static final EnumMap<NormalizedSourceType, Fabric8ContextToSourceData> STRATEGIES = new EnumMap<>(
			NormalizedSourceType.class);

	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_CONFIG_MAP, namedConfigMap());
		STRATEGIES.put(NormalizedSourceType.LABELED_CONFIG_MAP, labeledConfigMap());
	}

	Fabric8ConfigMapPropertySource(Fabric8ConfigContext context) {
		super(getSourceData(context));
	}

	private static SourceData getSourceData(Fabric8ConfigContext context) {
		NormalizedSourceType type = context.normalizedSource().type();
		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
				.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
	}

	private static Fabric8ContextToSourceData namedConfigMap() {
		return new NamedConfigMapContextToSourceDataProvider().get();
	}

	private static Fabric8ContextToSourceData labeledConfigMap() {
		return new LabeledConfigMapContextToSourceDataProvider().get();
	}

}
