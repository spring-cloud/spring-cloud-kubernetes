package org.springframework.cloud.kubernetes.leader;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class LeadershipControllerTest {

	private final String NAMESPACE = "test-namespace";

	private final String CONFIG_MAP_NAME = "test-config-map-name";

	private final String ROLE = "test-role";

	private final String ID = "test-id";

	@Mock
	private LeaderProperties mockLeaderProperties;

	@Mock
	private KubernetesClient mockKubernetesClient;

	@Mock
	private MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> configMapsOperation;

	@Mock
	private NonNamespaceOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> inNamespaceOperation;

	@Mock
	private Resource<ConfigMap, DoneableConfigMap> configMapResource;

	@Mock
	private ConfigMap mockConfigMap;

	@Mock
	private Map<String, String> mockData;

	private LeadershipController leadershipController;

	@Before
	public void before() {
		given(mockLeaderProperties.getLeaderIdPrefix()).willReturn("");
		given(mockLeaderProperties.getNamespace(NAMESPACE)).willReturn(NAMESPACE);
		given(mockLeaderProperties.getConfigMapName()).willReturn(CONFIG_MAP_NAME);

		given(mockKubernetesClient.getNamespace()).willReturn(NAMESPACE);
		given(mockKubernetesClient.configMaps()).willReturn(configMapsOperation);
		given(configMapsOperation.inNamespace(NAMESPACE)).willReturn(inNamespaceOperation);
		given(inNamespaceOperation.withName(CONFIG_MAP_NAME)).willReturn(configMapResource);
		given(configMapResource.get()).willReturn(mockConfigMap);
		given(mockConfigMap.getData()).willReturn(mockData);
		given(mockData.get(ROLE)).willReturn(ID);

		leadershipController = new LeadershipController(mockLeaderProperties, mockKubernetesClient);
	}

	@Test
	public void shouldGetLeader() {
		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader.getRole()).isEqualTo(ROLE);
		assertThat(leader.getId()).isEqualTo(ID);
	}

	@Test
	public void shouldNotGetLeaderFromNonExistingConfigMap() {
		given(mockLeaderProperties.getConfigMapName()).willReturn("unknown");

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldNotGetLeaderFromEmptyConfigMap() {
		given(mockConfigMap.getData()).willReturn(null);

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldNotGetLeaderFromInvalidConfigMap() {
		given(mockData.get(ROLE)).willReturn(null);

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

	@Test
	public void shouldHandleFailureWhenGettingLeader() {
		given(mockKubernetesClient.configMaps()).willThrow(new RuntimeException("Test exception"));

		Leader leader = leadershipController.getLeader(ROLE);
		assertThat(leader).isNull();
	}

}
