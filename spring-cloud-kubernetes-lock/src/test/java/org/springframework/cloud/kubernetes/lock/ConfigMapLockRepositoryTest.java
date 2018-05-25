package org.springframework.cloud.kubernetes.lock;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.CONFIG_MAP_PREFIX;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.CREATED_AT_KEY;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.HOLDER_KEY;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.KIND_LABEL;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.KIND_LABEL_VALUE;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.PROVIDER_LABEL;
import static org.springframework.cloud.kubernetes.lock.ConfigMapLockRepository.PROVIDER_LABEL_VALUE;

@RunWith(MockitoJUnitRunner.class)
public class ConfigMapLockRepositoryTest {

	private static final String NAMESPACE = "test-namespace";

	private static final String NAME = "test-name";

	private static final String CONFIGMAP_NAME = String.format("%s-%s", CONFIG_MAP_PREFIX, NAME);

	private static final String HOLDER = "test-holder";

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		mockConfigMapsOperation;

	@Mock
	private NonNamespaceOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		mockInNamespaceOperation;

	@Mock
	private Resource<ConfigMap, DoneableConfigMap> mockWithNameResource;

	@Mock
	private ConfigMap mockConfigMap;

	@Mock
	private Map<String, String> mockData;

	private ConfigMapLockRepository repository;

	@Before
	public void before() {
		given(mockKubernetesClient.configMaps()).willReturn(mockConfigMapsOperation);
		given(mockConfigMapsOperation.inNamespace(NAMESPACE)).willReturn(mockInNamespaceOperation);
		given(mockInNamespaceOperation.withName(CONFIGMAP_NAME)).willReturn(mockWithNameResource);
		given(mockWithNameResource.get()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(mockData);

		repository = new ConfigMapLockRepository(mockKubernetesClient, NAMESPACE);
	}

	@Test
	public void shouldGet() {
		Optional<ConfigMap> optionalConfigMap = repository.get(NAME);

		assertThat(optionalConfigMap.isPresent()).isTrue();
		assertThat(optionalConfigMap.get()).isEqualTo(mockConfigMap);
	}

	@Test
	public void shouldNotGetNonExistent() {
		given(mockWithNameResource.get()).willReturn(null);

		Optional<ConfigMap> optionalConfigMap = repository.get(NAME);

		assertThat(optionalConfigMap.isPresent()).isFalse();
	}

	@Test
	public void shouldCreate() {
		boolean result = repository.create(NAME, HOLDER, 1000);
		assertThat(result).isTrue();

		verify(mockKubernetesClient).configMaps();
		verify(mockConfigMapsOperation).inNamespace(NAMESPACE);

		ConfigMap expectedConfigMap = new ConfigMapBuilder().withNewMetadata()
			.withName(CONFIGMAP_NAME)
			.addToLabels(PROVIDER_LABEL, PROVIDER_LABEL_VALUE)
			.addToLabels(KIND_LABEL, KIND_LABEL_VALUE)
			.endMetadata()
			.addToData(HOLDER_KEY, HOLDER)
			.addToData(CREATED_AT_KEY, "1000")
			.build();
		verify(mockInNamespaceOperation).create(eq(expectedConfigMap));
	}

	@Test
	public void shouldHandleCreationFailure() {
		given(mockKubernetesClient.configMaps()).willThrow(new KubernetesClientException("test exception"));

		boolean result = repository.create(NAME, HOLDER, 1000);
		assertThat(result).isFalse();
	}

	@Test
	public void shouldDelete() {
		repository.delete(NAME);

		verify(mockWithNameResource).delete();
	}

	@Test
	public void shouldDeleteOld() {
		given(mockData.get(CREATED_AT_KEY)).willReturn(String.valueOf(System.currentTimeMillis() - 1000));

		repository.deleteIfOlderThan(NAME, 100);

		verify(mockWithNameResource).delete();
	}

	@Test
	public void shouldNotDeleteNonExpired() {
		given(mockData.get(CREATED_AT_KEY)).willReturn(String.valueOf(System.currentTimeMillis()) + 1000);

		repository.deleteIfOlderThan(NAME, 100);

		verify(mockWithNameResource, times(0)).delete();
	}

}
