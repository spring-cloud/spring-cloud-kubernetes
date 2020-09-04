package org.springframework.cloud.kubernetes.config.reload;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
/**
 * @author wind57
 */
public class ConfigurationChangeDetectorTest {

	private final ConfigurationChangeDetectorStub stub = new ConfigurationChangeDetectorStub(null, null, null, null);

	@Test
	public void testChangedTwoNulls() {
		boolean changed = stub.changed(null, (MapPropertySource) null);
		Assert.assertFalse(changed);
	}

	@Test
	public void testChangedLeftNullRightNonNull() {
		MapPropertySource right = new MapPropertySource("rightNonNull", Collections.emptyMap());
		boolean changed = stub.changed(null, right);
		Assert.assertTrue(changed);
	}

	@Test
	public void testChangedLeftNonNullRightNull() {
		MapPropertySource left = new MapPropertySource("leftNonNull", Collections.emptyMap());
		boolean changed = stub.changed(left, null);
		Assert.assertTrue(changed);
	}

	@Test
	public void testChangedEqualMaps() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		Map<String, Object> rightMap = new HashMap<>();
		rightMap.put("key", value);
		MapPropertySource left = new MapPropertySource("left", leftMap);
		MapPropertySource right = new MapPropertySource("right", rightMap);
		boolean changed = stub.changed(left, right);
		Assert.assertFalse(changed);
	}

	@Test
	public void testChangedNonEqualMaps() {
		Object value = new Object();
		Map<String, Object> leftMap = new HashMap<>();
		leftMap.put("key", value);
		leftMap.put("anotherKey", value);
		Map<String, Object> rightMap = new HashMap<>();
		rightMap.put("key", value);
		MapPropertySource left = new MapPropertySource("left", leftMap);
		MapPropertySource right = new MapPropertySource("right", rightMap);
		boolean changed = stub.changed(left, right);
		Assert.assertTrue(changed);
	}

	/**
	 * only needed to test some protected methods it defines
	 */
	private static class ConfigurationChangeDetectorStub extends ConfigurationChangeDetector {
		public ConfigurationChangeDetectorStub(ConfigurableEnvironment environment,
											   ConfigReloadProperties properties,
											   KubernetesClient kubernetesClient,
											   ConfigurationUpdateStrategy strategy) {
			super(environment, properties, kubernetesClient, strategy);
		}
	}
}

