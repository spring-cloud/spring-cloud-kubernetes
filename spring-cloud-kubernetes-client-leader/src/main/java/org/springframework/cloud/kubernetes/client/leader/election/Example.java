package org.springframework.cloud.kubernetes.client.leader.election;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.util.Config;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_GROUP;
import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_VERSION;

public class Example {

	public static void main(String[] args) throws Exception {
//		ApiClient client = Config.defaultClient();
//		//Configuration.setDefaultApiClient(client);
//
//		// New
//		String appNamespace = "default";
//		String appName = "leader-election-foobar";
//		String lockHolderIdentityName = UUID.randomUUID().toString(); // Anything unique
//		LeaseLock lock = new LeaseLock(appNamespace, appName, lockHolderIdentityName, client);
//
//		LeaderElectionConfig leaderElectionConfig =
//			new LeaderElectionConfig(
//				lock, Duration.ofMillis(10000), Duration.ofMillis(8000), Duration.ofMillis(2000));
//		try (LeaderElector leaderElector = new LeaderElector(leaderElectionConfig)) {
//			leaderElector.run(
//				() -> {
//					System.out.println("Do something when getting leadership.");
//				},
//				() -> {
//					System.out.println("Do something when losing leadership.");
//				},
//				x -> {
//					System.out.println("current leader was elected : " + x);
//				});
//		}
//
//		System.out.println("here");

		ApiClient client = Config.defaultClient();
		CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);

		List<V1APIResource> resources = customObjectsApi.getAPIResources(COORDINATION_GROUP, COORDINATION_VERSION)
			.execute()
			.getResources();

		boolean found = resources.stream().map(V1APIResource::getKind).anyMatch("Lease"::equals);

		System.out.println("test");

	}

}
