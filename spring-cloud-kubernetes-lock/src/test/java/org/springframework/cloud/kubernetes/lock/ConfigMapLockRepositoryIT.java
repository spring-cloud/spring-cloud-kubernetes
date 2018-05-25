package org.springframework.cloud.kubernetes.lock;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.CREATED_AT_KEY;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.HOLDER_KEY;
import static org.springframework.cloud.kubernetes.lock.KubernetesLock.DEFAULT_TTL;

@RunWith(ArquillianConditionalRunner.class)
@RequiresKubernetes
public class ConfigMapLockRepositoryIT {

	private static final String NAME = "test-name";

	private static final String HOLDER = "test-holder";

	@ArquillianResource
	private KubernetesClient kubernetesClient;

	@ArquillianResource
	private Session session;

	private ConfigMapLockRepository repository;

	@Before
	public void before() {
		repository = new ConfigMapLockRepository(kubernetesClient, session.getNamespace());
	}

	@After
	public void after() {
		repository.delete(NAME);
	}

	@Test
	public void shouldCreate() {
		assertThat(repository.create(NAME, HOLDER, 1000)).isTrue();

		Optional<ConfigMap> optionalConfigMap = repository.get(NAME);
		assertThat(optionalConfigMap.isPresent()).isTrue();

		Map<String, String> data = optionalConfigMap.get().getData();
		assertThat(data).containsEntry(HOLDER_KEY, HOLDER);
		assertThat(data).containsEntry(CREATED_AT_KEY, String.valueOf(1000));
	}

	@Test
	public void shouldNotOverwrite() {
		assertThat(repository.create(NAME, HOLDER, 0)).isTrue();
		assertThat(repository.create(NAME, HOLDER, 0)).isFalse();
	}

	@Test
	public void shouldDelete() {
		repository.create(NAME, HOLDER, 0);
		repository.delete(NAME);
		assertThat(repository.get(NAME).isPresent()).isFalse();
	}

	@Test
	public void shouldDeleteExpired() {
		repository.create(NAME, HOLDER, 0);
		repository.deleteIfOlderThan(NAME, DEFAULT_TTL);
		assertThat(repository.get(NAME).isPresent()).isFalse();
	}

	@Test
	public void shouldKeepNotExpired() {
		repository.create(NAME, HOLDER, System.currentTimeMillis());
		repository.deleteIfOlderThan(NAME, DEFAULT_TTL);
		assertThat(repository.get(NAME).isPresent()).isTrue();
	}

}
