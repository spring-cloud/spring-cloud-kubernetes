package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeaderKubernetesHelperTest {

	private static final String NAMESPACE = "test-namespace";

	private static final String NAME = "test-name";

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> podMixedOperation;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		configMapMixedOperation;

	@Mock
	private NonNamespaceOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> podNonNamespaceOperation;

	@Mock
	private NonNamespaceOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>>
		configMapNonNamespaceOperation;

	@Mock
	private FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podWatchList;

	@Mock
	private PodList mockPodList;

	@Mock
	private Pod mockPod;

	@Mock
	private ObjectMeta mockObjectMeta;

	@Mock
	private Resource<ConfigMap, DoneableConfigMap> configMapResource;

	@Mock
	private Replaceable<ConfigMap, ConfigMap> configMapReplaceable;

	@Mock
	private ConfigMap mockConfigMap;

	private LeaderKubernetesHelper kubernetesHelper;

	@Before
	public void before() {
		given(mockLeaderProperties.getConfigMapName()).willReturn(NAME);
		given(mockLeaderProperties.getNamespace(NAMESPACE)).willReturn(NAMESPACE);

		given(mockKubernetesClient.getNamespace()).willReturn(NAMESPACE);

		kubernetesHelper = new LeaderKubernetesHelper(mockLeaderProperties, mockKubernetesClient);
	}

	@Test
	public void shouldCheckIfPodExists() {
		given(mockKubernetesClient.getNamespace()).willReturn(NAMESPACE);
		given(mockKubernetesClient.pods()).willReturn(podMixedOperation);
		given(podMixedOperation.inNamespace(NAMESPACE)).willReturn(podNonNamespaceOperation);
		given(podNonNamespaceOperation.withLabels(anyMap())).willReturn(podWatchList);
		given(podWatchList.list()).willReturn(mockPodList);
		given(mockPodList.getItems()).willReturn(Collections.singletonList(mockPod));
		given(mockPod.getMetadata()).willReturn(mockObjectMeta);
		given(mockObjectMeta.getName()).willReturn("test-id");

		boolean result = kubernetesHelper.podExists("test-id");

		assertThat(result).isTrue();
		verify(mockObjectMeta).getName();
	}

	@Test
	public void shouldGetConfigMap() {
		given(mockKubernetesClient.configMaps()).willReturn(configMapMixedOperation);
		given(configMapMixedOperation.inNamespace(NAMESPACE)).willReturn(configMapNonNamespaceOperation);
		given(configMapNonNamespaceOperation.withName(NAME)).willReturn(configMapResource);
		given(configMapResource.get()).willReturn(mockConfigMap);

		ConfigMap result = kubernetesHelper.getConfigMap();

		assertThat(result).isEqualTo(mockConfigMap);
	}

	@Test
	public void shouldCreateConfigMap() {
		given(mockKubernetesClient.configMaps()).willReturn(configMapMixedOperation);
		given(configMapMixedOperation.inNamespace(NAMESPACE)).willReturn(configMapNonNamespaceOperation);

		Map<String, String> data = Collections.singletonMap("test-key", "test-value");
		kubernetesHelper.createConfigMap(data);

		ArgumentCaptor<ConfigMap> configMapCaptor = ArgumentCaptor.forClass(ConfigMap.class);
		verify(configMapNonNamespaceOperation).create(configMapCaptor.capture());

		ConfigMap configMap = configMapCaptor.getValue();
		ObjectMeta metaData = configMap.getMetadata();
		assertThat(metaData.getName()).isEqualTo(NAME);
		assertThat(metaData.getLabels()).containsEntry("provider", "spring-cloud-kubernetes");
		assertThat(metaData.getLabels()).containsEntry("kind", "leaders");
		assertThat(configMap.getData()).containsEntry("test-key", "test-value");
	}

	@Test
	public void shouldUpdateConfigMapEntry() {
		given(mockConfigMap.getMetadata()).willReturn(mockObjectMeta);
		given(mockObjectMeta.getResourceVersion()).willReturn("test-version");

		given(mockKubernetesClient.configMaps()).willReturn(configMapMixedOperation);
		given(configMapMixedOperation.inNamespace(NAMESPACE)).willReturn(configMapNonNamespaceOperation);
		given(configMapNonNamespaceOperation.withName(NAME)).willReturn(configMapResource);
		given(configMapResource.lockResourceVersion("test-version")).willReturn(configMapReplaceable);

		Map<String, String> data = Collections.singletonMap("test-key", "test-value");
		kubernetesHelper.updateConfigMapEntry(mockConfigMap, data);

		ArgumentCaptor<ConfigMap> configMapCaptor = ArgumentCaptor.forClass(ConfigMap.class);
		verify(configMapReplaceable).replace(configMapCaptor.capture());

		ConfigMap configMap = configMapCaptor.getValue();
		assertThat(configMap.getData()).containsEntry("test-key", "test-value");
	}

	@Test
	public void shouldRemoveConfigMapEntry() {
		Map<String, String> data = Collections.singletonMap("test-key", "test-value");
		given(mockConfigMap.getData()).willReturn(data);
		given(mockConfigMap.getMetadata()).willReturn(mockObjectMeta);
		given(mockObjectMeta.getResourceVersion()).willReturn("test-version");

		given(mockKubernetesClient.configMaps()).willReturn(configMapMixedOperation);
		given(configMapMixedOperation.inNamespace(NAMESPACE)).willReturn(configMapNonNamespaceOperation);
		given(configMapNonNamespaceOperation.withName(NAME)).willReturn(configMapResource);
		given(configMapResource.lockResourceVersion("test-version")).willReturn(configMapReplaceable);

		kubernetesHelper.removeConfigMapEntry(mockConfigMap, "test-key");

		ArgumentCaptor<ConfigMap> configMapCaptor = ArgumentCaptor.forClass(ConfigMap.class);
		verify(configMapReplaceable).replace(configMapCaptor.capture());

		ConfigMap configMap = configMapCaptor.getValue();
		assertThat(configMap.getData()).doesNotContainKeys("test-key");
	}

}
