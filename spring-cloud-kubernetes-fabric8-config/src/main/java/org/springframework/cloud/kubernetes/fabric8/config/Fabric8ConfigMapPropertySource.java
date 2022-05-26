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

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSourceType;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.MapPropertySource;

/**
 * A {@link MapPropertySource} that uses Kubernetes config maps.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 * @author Michael Moudatsos
 * @author Isik Erhan
 */
public final class Fabric8ConfigMapPropertySource extends ConfigMapPropertySource {

	private static final EnumMap<NormalizedSourceType, Fabric8ContextToSourceData> STRATEGIES = new EnumMap<>(
			NormalizedSourceType.class);

	// there is a single strategy here at the moment (unlike secrets),
	// but this can change.
	// to be on par with secrets implementation, I am keeping it the same
	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_CONFIG_MAP, namedConfigMap());
	}

	Fabric8ConfigMapPropertySource(Fabric8ConfigContext context) {
		super(getSourceData(context));
	}

	private static SourceData getSourceData(Fabric8ConfigContext context) {
		NormalizedSourceType type = context.normalizedSource().type();
		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
				.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
	}

	// we need to pass various functions because the code we are interested in
	// is protected in ConfigMapPropertySource, and must stay that way.
	private static Fabric8ContextToSourceData namedConfigMap() {
		return NamedConfigMapContextToSourceDataProvider.of(ConfigMapPropertySource::processAllEntries).get();
	}

}
