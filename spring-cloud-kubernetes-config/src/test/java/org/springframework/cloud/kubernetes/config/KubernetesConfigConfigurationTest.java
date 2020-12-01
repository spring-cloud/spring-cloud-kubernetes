/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Dawson
 */
public class KubernetesConfigConfigurationTest extends KubernetesConfigTestBase {

	@Test
	public void kubernetesWhenKubernetesDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isTrue();
	}

	@Test
	public void kubernetesWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isFalse();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigAndSecretDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isFalse();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigEnabledButSecretDisabled()
			throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigDisabledButSecretEnabled()
			throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isFalse();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isTrue();
	}

	@Test
	public void kubernetesConfigwhenKubenretesEnabledAndKubernetsConfigEnabled()
			throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isTrue();
		assertThat(this.getContext().containsBean("secretsPropertySourceLocator"))
				.isTrue();
	}

	@Test
	public void kubernetesConfigwhenKubenretesEnabledAndKubernetsConfigDisabled()
			throws Exception {
		setup("spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false");
		assertThat(this.getContext().containsBean("configMapPropertySourceLocator"))
				.isFalse();
	}

}
