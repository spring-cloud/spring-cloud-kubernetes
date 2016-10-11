## Spring Cloud Kubernetes

[Spring Cloud](http://projects.spring.io/spring-cloud/) integration with [Kubernetes](http://kubernetes.io/)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes/) ![Apache 2](http://img.shields.io/badge/license-Apache%202-red.svg)

### Features

-   [DiscoveryClient for Kubernetes](#discoveryclient-for-kubernetes)
-   [KubernetesClient autoconfiguration](#kubernetesclient-autoconfiguration)
-   [PropertySource](#kubernetes-propertysource)
  -   [ConfigMap PropertySource](#configmap-propertysource)
  -   [Secrets PropertySource](#secrets-propertysource)
  -   [PropertySource Reload](#propertysource-reload)
-   [Pod Health Indicator](#pod-health-indicator)
-   [Transparency](#transparency) *(its transparent wether the code runs in or outside of Kubernetes)*
-   [Kubernetes Profile Autoconfiguration](#kubernetes-profile-autoconfiguration)
-   [Ribbon discovery in Kubernetes](#ribbon-discovery-in-kubernetes)
-   [Zipkin discovery in Kubernetes](#zipkin-discovery-in-kubernetes)
-   [ConfigMap Archaius Bridge](#configmap-archaius-bridge)

---
### DiscoveryClient for Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8/spring-cloud-starter-kubernetes.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8/spring-cloud-starter-kubernetes)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes/)


This project provides an implementation of [Discovery Client](https://github.com/spring-cloud/spring-cloud-commons/blob/master/spring-cloud-commons/src/main/java/org/springframework/cloud/client/discovery/DiscoveryClient.java) for [Kubernetes](http://kubernetes.io). This allows you to query Kubernetes endpoints *(see [services](http://kubernetes.io/docs/user-guide/services/))* by name.
This is something that you get for free just by adding the following dependency inside your project:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>spring-cloud-starter-kubernetes</artifactId>
    <version>${latest.version></version>
</dependency>
```

Then you can inject the client in your cloud simply by:

```java
@Autowire
private DiscoveryClient discoveryClient;
```

If for any reason you need to disable the `DiscoveryClient` you can simply set the following property:

```
spring.cloud.kubernetes.discovery.enabled=false
```

Some spring cloud components use the `DiscoveryClient` in order obtain info about the local service instance. For this to work you need to align the service name with `spring.application.name`.

### Kubernetes PropertySource

The most common approach to configure your spring boot application is to edit the `application.yaml` file. Often the user may override properties by specifying system properties or env variables.

#### ConfigMap PropertySource

Kubernetes has the notion of [ConfigMap](http://kubernetes.io/docs/user-guide/configmap/) for passing configuration to the application. This project provides integration with `ConfigMap` to make config maps accessible by spring boot.

The `ConfigMap` `PropertySource` when enabled will lookup Kubernetes for a `ConfigMap` named after the application (see `spring.application.name`). If the map is found it will read its data and do the following:

- apply individual configuration properties.
- apply as yaml the content of any property named `application.yaml`
- apply as properties file the content of any property named `application.properties`

Example:

Let's assume that we have a spring boot application named ``demo`` that uses properties to read its thread pool configuration.

- `pool.size.core`
- `pool.size.maximum`

This can be externalized to config map in yaml format:

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: demo
data:
  pool.size.core: 1
  pool.size.max: 16
```    

Individual properties work fine for most cases but sometimes we yaml is more convinient. In this case we will use a single property named `application.yaml` and embed our yaml inside it:

 ```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: demo
data:
  application.yaml: |-
    pool:
      size:
        core: 1
        max:16
```

**Notes:**
- To access ConfigMaps on OpenShift the service account needs at least view permissions i.e.:

    ```oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)```

#### Secrets PropertySource

Kubernetes has the notion of [Secrets](http://kubernetes.io/docs/user-guide/secrets/) for storing sensitive data such as password, OAuth tokens, etc. This project provides integration with `Secrets` to make secrets accessible by spring boot.

The `Secrets` `PropertySource` when enabled will lookup Kubernetes for `Secrets` from the following sources:
1. reading recursively from secrets mounts
2. named after the application (see `spring.application.name`)
3. matching some labels

Please note that by default, consuming Secrets via API (points 2 and 3 above) **is not enabled**.

If the secrets are found theirs data is made available to the application.

**Example:**

Let's assume that we have a spring boot application named ``demo`` that uses properties to read its ActiveMQ and PostreSQL configuration.

- `amq.username`
- `amq.password`
- `pg.username`
- `pg.password`

This can be externalized to Secrets in yaml format:

- **ActiveMQ**
    ```yaml
    apiVersion: v1
    kind: Secret
    metadata:
      name: activemq-secrets
      labels:
        broker: activemq
    type: Opaque
    data:
      amq.username: bXl1c2VyCg==
      amq.password: MWYyZDFlMmU2N2Rm
    ```    

- **PostreSQL**
    ```yaml
    apiVersion: v1
    kind: Secret
    metadata:
      name: postgres-secrets
      labels:
        db: postgres
    type: Opaque
    data:
      amq.username: dXNlcgo=
      amq.password: cGdhZG1pbgo=
    ```    

You can select the Secrets to consume in a number of ways:    

1. By listing the directories were secrets are mapped:
    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets/activemq,etc/secrets/postgres
    ```

    If you have all the secrets mapped to a common root, you can set them like:

    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets
    ```

2. By setting a named secret:
    ```
    -Dspring.cloud.kubernetes.secrets.name=postgres-secrets
    ```

3. By defining a list of labels:
    ```
    -Dspring.cloud.kubernetes.secrets.labels.broker=activemq
    -Dspring.cloud.kubernetes.secrets.labels.db=postgres
    ```

**Properties:**

| Name                                      | Type    | Default                    | Description
| ---                                       | ---     | ---                        | ---
| spring.cloud.kubernetes.secrets.enabled   | Boolean | true                       | Enable Secrets PropertySource
| spring.cloud.kubernetes.secrets.name      | String  | ${spring.application.name} | Sets the name of the secret to lookup
| spring.cloud.kubernetes.secrets.labels    | Map     | null                       | Sets the labels used to lookup secrets
| spring.cloud.kubernetes.secrets.paths     | List    | null                       | Sets the paths were secrets are mounted /example 1)
| spring.cloud.kubernetes.secrets.enableApi | Boolean | false                      | Enable/Disable consuming secrets via APIs (examples 2 and 3)

**Notes:**
- The property spring.cloud.kubernetes.secrets.labels behave as defined by [Map-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#map-based-binding).
- The property spring.cloud.kubernetes.secrets.paths behave as defined by [Collection-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#collection-based-binding).
- Access to secrets via API may be restricted for security reasons, the preferred way is to mount secret to the POD.

#### PropertySource Reload

Some applications may need to detect changes on external property sources and update their internal status to reflect the new configuration.
The reload feature of Spring Cloud Kubernetes is able to trigger an application reload when a related ConfigMap or Secret change.

This feature is disabled by default and can be enabled using the configuration property `spring.cloud.kubernetes.reload.enabled=true`
 (eg. in the *application.properties* file).

The following levels of reload are supported (property `spring.cloud.kubernetes.reload.strategy`):
- **refresh (default)**: only configuration beans annotated with `@ConfigurationProperties` or `@RefreshScope` are reloaded. 
This reload level leverages the refresh feature of Spring Cloud Context.
- **restart_context**: the whole Spring _ApplicationContext_ is gracefully restarted. Beans are recreated with the new configuration.
- **shutdown**: the Spring _ApplicationContext_ is shut down to activate a restart of the container.
 When using this level, make sure that the lifecycle of all non-daemon threads is bound to the ApplicationContext 
 and that a replication controller or replica set is configured to restart the pod.

Example:

Assuming that the reload feature is enabled with default settings (*refresh* mode), the following bean will be refreshed when the config map changes:
 
```java
@Configuration
@ConfigurationProperties(prefix = "bean")
public class MyConfig {

    private String message = "a message that can be changed live";

    // getter and setters

}
```

A way to see that changes effectively happen is creating another bean that prints the message periodically.

```java
@Component
public class MyBean {

    @Autowired
    private MyConfig config;

    @Scheduled(fixedDelay = 5000)
    public void hello() {
        System.out.println("The message is: " + config.getMessage());
    }
}
```

The message printed by the application can be changed using a config map like the following one:

```yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-example
data:
  application.properties: |-
    bean.message=Hello World!
```

Any change to the property named `bean.message` in the Config Map associated to the pod will be reflected in the output of the program 
(more details [here](#configmap-propertysource) about how to associate a Config Map to a pod).

The full example is available in [spring-cloud-kubernetes-reload-example](spring-cloud-kubernetes-examples/spring-cloud-kubernetes-reload-example). 

The reload feature supports two operating modes:
- **event (default)**: watches for changes in config maps or secrets using the Kubernetes API (web socket). 
Any event will produce a re-check on the configuration and a reload in case of changes. 
The `view` role on the service account is required in order to listen for config map changes. A higher level role (eg. `edit`) is required for secrets 
(secrets are not monitored by default).
- **polling**: re-creates the configuration periodically from config maps and secrets to see if it has changed.
The polling period can be configured using the property `spring.cloud.kubernetes.reload.period` and defaults to *15 seconds*.
It requires the same role as the monitored property source. 
This means, for example, that using polling on file mounted secret sources does not require particular privileges.

Properties:

| Name                                                   | Type    | Default                    | Description
| ---                                                    | ---     | ---                        | ---
| spring.cloud.kubernetes.reload.enabled                 | Boolean | false                      | Enables monitoring of property sources and configuration reload
| spring.cloud.kubernetes.reload.monitoring-config-maps  | Boolean | true                       | Allow monitoring changes in config maps
| spring.cloud.kubernetes.reload.monitoring-secrets      | Boolean | false                      | Allow monitoring changes in secrets
| spring.cloud.kubernetes.reload.strategy                | Enum    | refresh                    | The strategy to use when firing a reload (*refresh*, *restart_context*, *shutdown*)
| spring.cloud.kubernetes.reload.mode                    | Enum    | event                      | Specifies how to listen for changes in property sources (*event*, *polling*)
| spring.cloud.kubernetes.reload.period                  | Long    | 15000                      | The period in milliseconds for verifying changes when using the *polling* strategy

Notes:
- Properties under *spring.cloud.kubernetes.reload.** should not be used in config maps or secrets: changing such properties at runtime may lead to unexpected results;
- Deleting a property or the whole config map does not restore the original state of the beans when using the *refresh* level.


### Pod Health Indicator

Spring Boot uses [HealthIndicator](https://github.com/spring-projects/spring-boot/blob/master/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthIndicator.java) to expose info about the health of an application.
That makes it really useful for exposing health related information to the user and are also a good fit for use as [readiness probes](http://kubernetes.io/docs/user-guide/production-pods/#liveness-and-readiness-probes-aka-health-checks).

The Kubernetes health indicator which is part of the core modules exposes the following info:

- pod name
- visible services
- flag that indicates if app is internal or external to Kubernetes

### Transparency

All of the features described above will work equally fine regardless of wether our application is inside Kubernetes or not. This is really helpful for development and troubleshooting.

### Kubernetes Profile Autoconfiguration

When the application is run inside Kubernetes a profile named `kubernetes` will automatically get activated.
This allows the user to customize the configuration that will be applied in and out of kubernetes *(e.g. different dev and prod configuration)*.

### Ribbon discovery in Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes-netflix/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes-netflix/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8/spring-cloud-starter-kubernetes-netflix.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8/spring-cloud-starter-kubernetes-netflix)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes-netflix/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes-netflix/)

A Kubernetes based `ServerList` for Ribbon has been implemented. The implementation is part of the [spring-cloud-kubernetes-ribbon](spring-cloud-kubernetes-ribbon/pom.xml) module and you can use it by adding:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>spring-cloud-starter-kubernetes-netflix</artifactId>
    <version>${latest.version></version>
</dependency>
```

The ribbon discovery client can be disabled by setting `spring.cloud.kubernetes.ribbon.enabled=false`.

By default the client will detect all endpoints with the configured *client name* that lives in the current namespace.
If the endpoint contains multiple ports, the first port will be used. To fine tune the name of the desired port (if the service is a multiport service) or fine tune the namespace you can use one of the following properties.

- `KubernetesNamespace`
- `PortName`

Examples that are using this module for ribbon discovery are:

- [iPaas Quickstarts - Spring Boot - Ribbon](https://github.com/fabric8io/ipaas-quickstarts/tree/master/quickstart/spring-boot/ribbon)
- [Kubeflix - LoanBroker - Bank](https://github.com/fabric8io/kubeflix/tree/master/examples/loanbroker/bank)


### Zipkin discovery in Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes-zipkin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-starter-kubernetes-zipkin/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8/spring-cloud-starter-kubernetes-zipkin.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8/spring-cloud-starter-kubernetes-zipkin)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes-zipkin/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:spring-cloud-starter-kubernetes-zipkin/)

[Zipkin](https://github.com/openzipkin/zipkin) is a distributed tracing system and it is also supported by [Sleuth](https://github.com/spring-cloud/spring-cloud-sleuth).

Discovery of the services required by Zipkin (e.g. `zipkin-query`) is provided by [spring-cloud-kubernetes-zipkin](spring-cloud-kubernetes-zipkin/pom.xml) module and you can use it by adding:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>spring-cloud-starter-kubernetes-zipkin</artifactId>
    <version>${latest.version></version>
</dependency>
```

This works as an extension of [spring-cloud-sleuth-zipkin](https://github.com/spring-cloud/spring-cloud-sleuth/tree/master/spring-cloud-sleuth-zipkin).    

Examples of application that are using Zipkin discovery in Kubernetes:

- [iPaas Quickstarts - Spring Boot - Ribbon](https://github.com/fabric8io/ipaas-quickstarts/tree/master/quickstart/spring-boot/ribbon)
- [Kubeflix - LoanBroker - Bank](https://github.com/fabric8io/kubeflix/tree/master/examples/loanbroker/bank)

### ConfigMap Archaius Bridge

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-kubernetes-archaius/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/spring-cloud-kubernetes-archaius/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8/spring-cloud-kubernetes-archaius.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8/spring-cloud-kubernetes-archaius)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:spring-cloud-kubernetes-archaius/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:spring-cloud-kubernetes-archaius/)

Section [ConfigMap PropertySource](#configmap-propertysource) provides a brief explanation on how to configure spring boot application via ConfigMap.
This approach will aid in creating the configuration properties objects that will be passed in our application. If our application is using Archaius it will indirectly benefit by it.
An alternative approach that provides more direct Archaius support without getting in the way of spring configuration properties by using [spring-cloud-kubernetes-archaius](spring-cloud-kubernetes-archaius/pom.xml) that is part of the Netflix starter.

This module allows you to annotate your application with the `@ArchaiusConfigMapSource` and archaius will automatically use the configmap as a watched source *(get notification on changes)*.

---
### Troubleshooting

#### Namespace
Most of the components provided in this project need to know the namespace. For Kubernetes (1.3+) the namespace is made available to pod as part of the service account secret and automatically detected by the client.
For earlier version it needs to be specified as an env var to the pod. A quick way to do this is:

      env:
      - name: "KUBERNETES_NAMESPACE"
        valueFrom:
          fieldRef:
            fieldPath: "metadata.namespace"


#### Service Account
For distros of Kubernetes that support more fine-grained role-based access within the cluster, you need to make sure a pod that runs with spring-cloud-kubernetes has access to the Kubernetes API. For example, OpenShift has very comprehensive security measures that are on by default (typically) in a shared cluster. For any service accounts you assign to a deployment/pod, you need to make sure it has the correct roles. For example, you can add `cluster-view` permissions to your `default` service account depending on the project you're in:

```             
oc policy add-role-to-user cluster-view system:serviceaccount:<project/namespace>:default
```             

### Building

You can just use maven to build it from sources:

```
mvn clean install
```    

### Usage

The project provides a "starter" module, so you just need to add the following dependency in your project.

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>spring-cloud-starter-kubernetes</artifactId>
    <version>x.y.z</version>
</dependency>
```    
