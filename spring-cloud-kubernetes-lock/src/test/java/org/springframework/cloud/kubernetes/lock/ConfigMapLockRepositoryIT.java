package org.springframework.cloud.kubernetes.lock;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
@RequiresKubernetes
public class ConfigMapLockRepositoryIT {

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
		deleteConfigMap("test-name");
		deleteConfigMap("test-name-2");
	}

	@Test
	public void shouldCreateConfigMap() {
		long expiration = System.currentTimeMillis();
		boolean result = repository.create("test-name", "test-holder", expiration);
		assertThat(result).isTrue();

		ConfigMap configMap = repository.get("test-name");
		Map<String, String> data = configMap.getData();
		assertThat(data).containsEntry(ConfigMapLockRepository.HOLDER_KEY, "test-holder");
		assertThat(data).containsEntry(ConfigMapLockRepository.EXPIRATION_KEY, String.valueOf(expiration));
	}

	@Test
	public void shouldNotOverwriteConfigMap() {
		boolean firstResult = repository.create("test-name", "test-holder", 0);
		assertThat(firstResult).isTrue();

		boolean secondResult = repository.create("test-name", "test-holder", 0);
		assertThat(secondResult).isFalse();
	}

	@Test
	public void shouldDeleteAllConfigMaps() {
		repository.create("test-name", "test-holder", 0);
		repository.create("test-name-2", "test-holder-2", 0);
		repository.deleteAll();
		assertThat(repository.get("test-name")).isNull();
		assertThat(repository.get("test-name-2")).isNull();
	}

	@Test
	public void shouldDeleteExpiredConfigMaps() {
		repository.create("test-name", "test-holder", System.currentTimeMillis() - 1);
		repository.create("test-name-2", "test-holder-2", System.currentTimeMillis() + 10000);
		repository.deleteExpired();
		assertThat(repository.get("test-name")).isNull();
		assertThat(repository.get("test-name-2")).isNotNull();
	}

	private void deleteConfigMap(String name) {
		kubernetesClient.configMaps()
			.inNamespace(session.getNamespace())
			.withName(String.format("%s-%s", ConfigMapLockRepository.CONFIG_MAP_PREFIX, name))
			.delete();
	}

}
