## Spring Cloud Kubernetes

[Spring Cloud](http://projects.spring.io/spring-cloud/) integration with [Kubernetes](http://kubernetes.io/)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes/) ![Apache 2](http://img.shields.io/badge/license-Apache%202-red.svg)

### Features

-   [DiscoveryClient for Kubernetes](#discoveryclient-for-kubernetes)
-   [KubernetesClient autoconfiguration](#kubernetesclient-autoconfiguration)
-   [PropertySource](#kubernetes-propertysource)
  -   [ConfigMap PropertySource](#configmap-propertysource)
  -   [Secrets PropertySource](#secrets-propertysource)
  -   [PropertySource Reload](#propertysource-reload)
-   [Pod Health Indicator](#pod-health-indicator)
-   [Transparency](#transparency) *(it is transparent whether the code runs in or outside of Kubernetes)*
-   [Kubernetes Profile Autoconfiguration](#kubernetes-profile-autoconfiguration)
-   [Ribbon discovery in Kubernetes](#ribbon-discovery-in-kubernetes)
-   [Zipkin discovery in Kubernetes](#zipkin-discovery-in-kubernetes)
-   [ConfigMap Archaius Bridge](#configmap-archaius-bridge)

---
### DiscoveryClient for Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes/)
[![Javadocs](http://www.javadoc.io/badge/org.springframework.cloud/spring-cloud-starter-kubernetes.svg?color=blue)](http://www.javadoc.io/doc/org.springframework.cloud/spring-cloud-starter-kubernetes)
[![Dependency Status](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes/badge?style=flat)](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes/)


This project provides an implementation of [Discovery Client](https://github.com/spring-cloud/spring-cloud-commons/blob/master/spring-cloud-commons/src/main/java/org/springframework/cloud/client/discovery/DiscoveryClient.java) for [Kubernetes](http://kubernetes.io). This allows you to query Kubernetes endpoints *(see [services](http://kubernetes.io/docs/user-guide/services/))* by name.
A service is typically exposed by the Kubernetes API server as a collection of endpoints which represent `http`, `https` addresses that a client can
access from a Spring Boot application running as a pod. This discovery feature is also used by the Spring Cloud Kubernetes Ribbon or Zipkin projects
to fetch respectively the list of the endpoints defined for an application to be load balanced or the Zipkin servers available to send the traces or spans.

This is something that you get for free just by adding the following dependency inside your project:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes</artifactId>
    <version>${latest.version}</version>
</dependency>
```

To enable loading of the `DiscoveryClient`, add `@EnableDiscoveryClient` to the according configuration or application class like this:

```java
@SpringBootApplication
@EnableDiscoveryClient
public class Application {  
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
```

Then you can inject the client in your code simply by:

```java
@Autowired
private DiscoveryClient discoveryClient;
```

If for any reason you need to disable the `DiscoveryClient` you can simply set the following property in `application
.properties`:

```
spring.cloud.kubernetes.discovery.enabled=false
```

[//]: # "TODO: make clearer with an example and details on how to align service and application name"

Some Spring Cloud components use the `DiscoveryClient` in order to obtain info about the local service instance. For 
this to work you need to align the service name with the `spring.application.name` property.

### Kubernetes PropertySource

The most common approach to configure your Spring Boot application is to create an `application.properties|yaml` or 
an `application-profile.properties|yaml` file containing key-value pairs providing customization values to your 
application or Spring Boot starters. Users may override these properties by specifying system properties or environment 
variables.

#### ConfigMap PropertySource

Kubernetes provides a resource named [ConfigMap](http://kubernetes.io/docs/user-guide/configmap/) to externalize the 
parameters to pass to your application in the form of key-value pairs or embedded `application.properties|yaml` files.
The [Spring Cloud Kubernetes Config](./spring-cloud-kubernetes-config) project makes Kubernetes `ConfigMap`s available 
during application bootstrapping and triggers hot reloading of beans or Spring context when changes are detected on 
observed `ConfigMap`s.

The default behavior is to create a `ConfigMapPropertySource` based on a Kubernetes `ConfigMap` which has `metadata.name` of either the name of 
your Spring application (as defined by its `spring.application.name` property) or a custom name defined within the
`bootstrap.properties` file under the following key `spring.cloud.kubernetes.config.name`.

However, more advanced configuration are possible where multiple ConfigMaps can be used
This is made possible by the `spring.cloud.kubernetes.config.sources` list.
For example one could define the following ConfigMaps

```yaml
spring:
  application:
    name: cloud-k8s-app	
  cloud:
    kubernetes:
      config:
        name: default-name
        namespace: default-namespace
        sources:
         # Spring Cloud Kubernetes will lookup a ConfigMap named c1 in namespace default-namespace 
         - name: c1
         # Spring Cloud Kubernetes will lookup a ConfigMap named default-name in whatever namespace n2
         - namespace: n2
         # Spring Cloud Kubernetes will lookup a ConfigMap named c3 in namespace n3
         - namespace: n3
           name: c3
```

In the example above, it `spring.cloud.kubernetes.config.namespace` had not been set,
then the ConfigMap named `c1` would be looked up in the namespace that the application runs  

Any matching `ConfigMap` that is found, will be processed as follows:

- apply individual configuration properties.
- apply as `yaml` the content of any property named `application.yaml`
- apply as properties file the content of any property named `application.properties`

The single exception to the aforementioned flow is when the `ConfigMap` contains a **single** key that indicates
the file is a YAML or Properties file. In that case the name of the key does NOT have to be `application.yaml` or
`application.properties` (it can be anything) and the value of the property will be treated correctly.
This features facilitates the use case where the `ConfigMap` was created using something like:

`kubectl create configmap game-config --from-file=/path/to/app-config.yaml`

Example:

Let's assume that we have a Spring Boot application named ``demo`` that uses properties to read its thread pool 
configuration.

- `pool.size.core`
- `pool.size.maximum`

This can be externalized to config map in `yaml` format:

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: demo
data:
  pool.size.core: 1
  pool.size.max: 16
```    

Individual properties work fine for most cases but sometimes embedded `yaml` is more convenient. In this case we will 
use a single property named `application.yaml` to embed our `yaml`:

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

The following also works:

 ```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: demo
data:
  custom-name.yaml: |-
    pool:
      size:
        core: 1
        max:16
```

Spring Boot applications can also be configured differently depending on active profiles which will be merged together
when the ConfigMap is read. It is possible to provide different property values for different profiles using an
`application.properties|yaml` property, specifying profile-specific values each in their own document
(indicated by the `---` sequence) as follows:
 
```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: demo
data:
  application.yml: |-
    greeting:
      message: Say Hello to the World
    farewell:
      message: Say Goodbye
    ---
    spring:
      profiles: development
    greeting:
      message: Say Hello to the Developers
    farewell:
      message: Say Goodbye to the Developers
    ---
    spring:
      profiles: production
    greeting:
      message: Say Hello to the Ops
```

In the above case, the configuration loaded into your Spring Application with the `development` profile will be:
```yaml
  greeting:
    message: Say Hello to the Developers
  farewell:
    message: Say Goodbye to the Developers
```
whereas if the `production` profile is active, the configuration will be:
```yaml
  greeting:
    message: Say Hello to the Ops
  farewell:
    message: Say Goodbye
```

If both profiles are active, the property which appears last within the configmap will overwrite preceding values.


To tell to Spring Boot which `profile` should be enabled at bootstrap, a system property can be passed to the Java 
command launching your Spring Boot application using an env variable that you will define with the OpenShift 
`DeploymentConfig` or Kubernetes `ReplicationConfig` resource file as follows:

```yaml
apiVersion: v1
kind: DeploymentConfig
spec:
  replicas: 1
  ...
    spec:
      containers:
      - env:
        - name: JAVA_APP_DIR
          value: /deployments
        - name: JAVA_OPTIONS
          value: -Dspring.profiles.active=developer
```

**Notes:**
- To access `ConfigMap`s on OpenShift the service account needs at least view permissions i.e.:

    ```oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)```
    
**Properties:**

| Name                                     | Type    | Default                    | Description
| ---                                      | ---     | ---                        | ---
| spring.cloud.kubernetes.config.enabled   | Boolean | true                       | Enable Secrets PropertySource
| spring.cloud.kubernetes.config.name      | String  | ${spring.application.name} | Sets the name of ConfigMap to lookup
| spring.cloud.kubernetes.config.namespace | String  | Client namespace           | Sets the Kubernetes namespace where to lookup
| spring.cloud.kubernetes.config.paths     | List    | null                       | Sets the paths where ConfigMaps are mounted
| spring.cloud.kubernetes.config.enableApi | Boolean | true                       | Enable/Disable consuming ConfigMaps via APIs


#### Secrets PropertySource

Kubernetes has the notion of [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) for storing 
sensitive data such as password, OAuth tokens, etc. This project provides integration with `Secrets` to make secrets 
accessible by Spring Boot applications. This feature can be explicitly enabled/disabled using the `spring.cloud.kubernetes.secrets.enabled` property.

The `SecretsPropertySource` when enabled will lookup Kubernetes for `Secrets` from the following sources:
1. reading recursively from secrets mounts
2. named after the application (as defined by `spring.application.name`)
3. matching some labels

Please note that by default, consuming Secrets via API (points 2 and 3 above) **is not enabled** for security reasons
 and it is recommend that containers share secrets via mounted volumes.

If the secrets are found their data is made available to the application.

**Example:**

Let's assume that we have a spring boot application named ``demo`` that uses properties to read its database 
configuration. We can create a Kubernetes secret using the following command:

```
oc create secret generic db-secret --from-literal=username=user --from-literal=password=p455w0rd
```

This would create the following secret (shown using `oc get secrets db-secret -o yaml`):

```yaml
apiVersion: v1
data:
  password: cDQ1NXcwcmQ=
  username: dXNlcg==
kind: Secret
metadata:
  creationTimestamp: 2017-07-04T09:15:57Z
  name: db-secret
  namespace: default
  resourceVersion: "357496"
  selfLink: /api/v1/namespaces/default/secrets/db-secret
  uid: 63c89263-6099-11e7-b3da-76d6186905a8
type: Opaque
``` 


Note that the data contains Base64-encoded versions of the literal provided by the create command.

This secret can then be used by your application for example by exporting the secret's value as environment variables:

```yaml
apiVersion: v1
kind: Deployment
metadata:
  name: ${project.artifactId}
spec:
   template:
     spec:
       containers:
         - env:
            - name: DB_USERNAME
              valueFrom:
                 secretKeyRef:
                   name: db-secret
                   key: username
            - name: DB_PASSWORD
              valueFrom:
                 secretKeyRef:
                   name: db-secret
                   key: password
```

You can select the Secrets to consume in a number of ways:    

1. By listing the directories where secrets are mapped:
    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets/db-secret,etc/secrets/postgresql
    ```

    If you have all the secrets mapped to a common root, you can set them like:

    ```
    -Dspring.cloud.kubernetes.secrets.paths=/etc/secrets
    ```

2. By setting a named secret:
    ```
    -Dspring.cloud.kubernetes.secrets.name=db-secret
    ```

3. By defining a list of labels:
    ```
    -Dspring.cloud.kubernetes.secrets.labels.broker=activemq
    -Dspring.cloud.kubernetes.secrets.labels.db=postgresql
    ```

**Properties:**

| Name                                      | Type    | Default                    | Description
| ---                                       | ---     | ---                        | ---
| spring.cloud.kubernetes.secrets.enabled   | Boolean | true                       | Enable Secrets PropertySource
| spring.cloud.kubernetes.secrets.name      | String  | ${spring.application.name} | Sets the name of the secret to lookup
| spring.cloud.kubernetes.secrets.namespace | String  | Client namespace           | Sets the Kubernetes namespace where to lookup
| spring.cloud.kubernetes.secrets.labels    | Map     | null                       | Sets the labels used to lookup secrets
| spring.cloud.kubernetes.secrets.paths     | List    | null                       | Sets the paths where secrets are mounted (example 1)
| spring.cloud.kubernetes.secrets.enableApi | Boolean | false                      | Enable/Disable consuming secrets via APIs (examples 2 and 3)

**Notes:**
- The property `spring.cloud.kubernetes.secrets.labels` behaves as defined by 
[Map-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#map-based-binding).
- The property `spring.cloud.kubernetes.secrets.paths` behaves as defined by 
[Collection-based binding](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-Configuration-Binding#collection-based-binding).
- Access to secrets via API may be restricted for security reasons, the preferred way is to mount secret to the POD.

Example of application using secrets (though it hasn't been updated to use the new `spring-cloud-kubernetes` project):
[spring-boot-camel-config](https://github.com/fabric8-quickstarts/spring-boot-camel-config)

#### PropertySource Reload

Some applications may need to detect changes on external property sources and update their internal status to reflect the new configuration.
The reload feature of Spring Cloud Kubernetes is able to trigger an application reload when a related `ConfigMap` or 
`Secret` changes.

This feature is disabled by default and can be enabled using the configuration property `spring.cloud.kubernetes.reload.enabled=true`
 (eg. in the *application.properties* file).

The following levels of reload are supported (property `spring.cloud.kubernetes.reload.strategy`):
- **`refresh` (default)**: only configuration beans annotated with `@ConfigurationProperties` or `@RefreshScope` are reloaded. 
This reload level leverages the refresh feature of Spring Cloud Context.
- **`restart_context`**: the whole Spring _ApplicationContext_ is gracefully restarted. Beans are recreated with the new configuration.
- **`shutdown`**: the Spring _ApplicationContext_ is shut down to activate a restart of the container.
 When using this level, make sure that the lifecycle of all non-daemon threads is bound to the ApplicationContext 
 and that a replication controller or replica set is configured to restart the pod.

Example:

Assuming that the reload feature is enabled with default settings (*`refresh`* mode), the following bean will be refreshed when the config map changes:
 
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

The message printed by the application can be changed using a `ConfigMap` as follows:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-example
data:
  application.properties: |-
    bean.message=Hello World!
```

Any change to the property named `bean.message` in the `ConfigMap` associated to the pod will be reflected in the 
output. More generally speaking, changes associated to properties prefixed with the value defined by the `prefix` 
field of the `@ConfigurationProperties` annotation will be detected and reflected in the application.
[Associating a `ConfigMap` to a pod](#configmap-propertysource) is explained above.

The full example is available in [spring-cloud-kubernetes-reload-example](spring-cloud-kubernetes-examples/kubernetes-reload-example). 

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
- Properties under *spring.cloud.kubernetes.reload.* should not be used in config maps or secrets: changing such properties at runtime may lead to unexpected results;
- Deleting a property or the whole config map does not restore the original state of the beans when using the *refresh* level.


### Pod Health Indicator

Spring Boot uses [HealthIndicator](https://github.com/spring-projects/spring-boot/blob/master/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/health/HealthIndicator.java) to expose info about the health of an application.
That makes it really useful for exposing health related information to the user and are also a good fit for use as [readiness probes](http://kubernetes.io/docs/user-guide/production-pods/#liveness-and-readiness-probes-aka-health-checks).

The Kubernetes health indicator which is part of the core module exposes the following info:

- pod name, ip address, namespace, service account, node name and its ip address
- flag that indicates if the Spring Boot application is internal or external to Kubernetes

### Transparency

All of the features described above will work equally well regardless of whether your application is running inside 
Kubernetes or not. This is really helpful for development and troubleshooting.
From a development point of view, this is really helpful as you can start your Spring Boot application and debug one 
of the modules part of this project. It is not required to deploy it in Kubernetes
as the code of the project relies on the 
[Fabric8 Kubernetes Java client](https://github.com/fabric8io/kubernetes-client) which is a fluent DSL able to 
communicate using `http` protocol to the REST API of Kubernetes Server.  

### Kubernetes Profile Autoconfiguration

When the application runs as a pod inside Kubernetes a Spring profile named `kubernetes` will automatically get activated.
This allows the developer to customize the configuration, to define beans that will be applied when the Spring Boot application is deployed
within the Kubernetes platform *(e.g. different dev and prod configuration)*.

### Ribbon discovery in Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes-netflix/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes-netflix/)
[![Javadocs](http://www.javadoc.io/badge/org.springframework.cloud/spring-cloud-starter-kubernetes-netflix.svg?color=blue)](http://www.javadoc.io/doc/org.springframework.cloud/spring-cloud-starter-kubernetes-netflix)
[![Dependency Status](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes-netflix/badge?style=flat)](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes-netflix/)

Spring Cloud client applications calling a microservice should be interested on relying on a client load-balancing 
feature in order to automatically discover at which endpoint(s) it can reach a given service. This mechanism has been
implemented within the [spring-cloud-kubernetes-ribbon](spring-cloud-kubernetes-ribbon/pom.xml) project where a 
Kubernetes client will populate a [Ribbon](https://github.com/Netflix/ribbon) `ServerList` containing information 
about such endpoints.

The implementation is part of the following starter that you can use by adding its dependency to your pom file:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-netflix</artifactId>
    <version>${latest.version}</version>
</dependency>
```

When the list of the endpoints is populated, the Kubernetes client will search the registered endpoints living in 
the current namespace/project matching the service name defined using the Ribbon Client annotation:

```java
@RibbonClient(name = "name-service")
```

You can configure Ribbon's behavior by providing properties in your `application.properties` (via your application's 
dedicated `ConfigMap`) using the following format: `<name of your service>.ribbon.<Ribbon configuration key>` where:

- `<name of your service>` corresponds to the service name you're accessing over Ribbon, as configured using the 
`@RibbonClient` annotation (e.g. `name-service` in the example above)
- `<Ribbon configuration key>` is one of the Ribbon configuration key defined by
[Ribbon's CommonClientConfigKey class](https://github.com/Netflix/ribbon/blob/master/ribbon-core/src/main/java/com/netflix/client/config/CommonClientConfigKey.java)

Additionally, the `spring-cloud-kubernetes-ribbon` project defines two additional configuration keys to further 
control how Ribbon interacts with Kubernetes. In particular, if an endpoint defines multiple ports, the default 
behavior is to use the first one found. To select more specifically which port to use, in a multi-port service, use 
the `PortName` key. If you want to specify in which Kubernetes' namespace the target service should be looked up, use
the `KubernetesNamespace` key, remembering in both instances to prefix these keys with your service name and 
`ribbon` prefix as specified above.

Examples that are using this module for ribbon discovery are:

- [Spring Cloud Circuitbreaker and Ribbon](spring-cloud-kubernetes-examples/kubernetes-circuitbreaker-ribbon-example)
- [fabric8-quickstarts - Spring Boot - Ribbon](https://github.com/fabric8-quickstarts/spring-boot-ribbon)
- [Kubeflix - LoanBroker - Bank](https://github.com/fabric8io/kubeflix/tree/master/examples/loanbroker/bank)

Remark : The Ribbon discovery client can be disabled by setting this key within the application properties file 
`spring.cloud.kubernetes.ribbon.enabled=false`.


### Zipkin discovery in Kubernetes

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes-zipkin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-starter-kubernetes-zipkin/)
[![Javadocs](http://www.javadoc.io/badge/org.springframework.cloud/spring-cloud-starter-kubernetes-zipkin.svg?color=blue)](http://www.javadoc.io/doc/org.springframework.cloud/spring-cloud-starter-kubernetes-zipkin)
[![Dependency Status](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes-zipkin/badge?style=flat)](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-starter-kubernetes-zipkin/)

[Zipkin](https://github.com/openzipkin/zipkin) is a distributed tracing system which is supported by the project 
[Spring Cloud Sleuth](https://github.com/spring-cloud/spring-cloud-sleuth) which allows
to collect traces or spans from microservice applications. 

A Discovery client has been implemented top of Kubernetes in order to fetch the Zipkin service (e.g. `zipkin`). This 
client is provided by the [spring-cloud-kubernetes-zipkin](spring-cloud-kubernetes-zipkin/pom.xml) project that you 
can use by adding this starter to your maven pom file:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-zipkin</artifactId>
    <version>${latest.version}</version>
</dependency>
```

This works as an extension of the [spring-cloud-sleuth-zipkin](https://github.com/spring-cloud/spring-cloud-sleuth/tree/master/spring-cloud-sleuth-zipkin) project. 
The name of the Zipkin service and the target Kubernetes namespace/project where the service runs can be specified 
using the following `application.properties` properties:

```bash
spring.cloud.kubernetes.zipkin.discovery.serviceName=my-zipkin
spring.cloud.kubernetes.zipkin.discovery.serviceNamespace=tracing
```

By default, the discovery client will look for a Zipkin service named `zipkin` within the current namespace.

Examples of application that are using Zipkin discovery in Kubernetes:

- [Spring Cloud Kubernetes and Zipkin](kubernetes-zipkin)
- [fabric8-quickstarts - Spring Boot - Ribbon](https://github.com/fabric8-quickstarts/spring-boot-ribbon)
- [Kubeflix - LoanBroker - Bank](https://github.com/fabric8io/kubeflix/tree/master/examples/loanbroker/bank)

### ConfigMap Archaius Bridge

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-kubernetes-archaius/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.springframework.cloud/spring-cloud-kubernetes-archaius/)
[![Javadocs](http://www.javadoc.io/badge/org.springframework.cloud/spring-cloud-kubernetes-archaius.svg?color=blue)](http://www.javadoc.io/doc/org.springframework.cloud/spring-cloud-kubernetes-archaius)
[![Dependency Status](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-kubernetes-archaius/badge?style=flat)](https://www.versioneye.com/java/org.springframework.cloud:spring-cloud-kubernetes-archaius/)

The section [ConfigMap PropertySource](#configmap-propertysource) introduced how to configure a spring boot application via `Kubernetes ConfigMap` containing your configuration properties file.

If you prefer to use the configuration management library [Netflix Archaius](https://github.com/Netflix/archaius/wiki) instead of using the Spring application.properties|"yaml file,
then you can also leverage the `ConfigMap feature` by using the [spring-cloud-kubernetes-archaius](spring-cloud-kubernetes-archaius/pom.xml) project.

To use it, add the following starter `spring-cloud-starter-kubernetes-all` to your pom file definition.

This module allows you to annotate your application with the `@ArchaiusConfigMapSource` and archaius will automatically use the `Kubernetes configmap` as a watched source *(get notification on changes)*.

---
### Troubleshooting

#### Version and compatibility

The current version of Spring Cloud Kubernetes is using version 2.2.x of the [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client) and is expected to work with version 1.x of [Kubernetes](https://github.com/kubernetes/kubernetes) and 1.x of [Openshift](https://github.com/openshift/origin).
Note, that [Kubernetes](https://github.com/kubernetes/kubernetes) and [Openshift](https://github.com/openshift/origin) are for the most part backwards compatible so, its expected that this framework is compatible with both the latest and earlier versions.


#### Namespace
Most of the components provided in this project need to know the namespace. For Kubernetes (1.3+) the namespace is made available to pod as part of the service account secret and automatically detected by the client.
For earlier version it needs to be specified as an env var to the pod. A quick way to do this is:

      env:
      - name: "KUBERNETES_NAMESPACE"
        valueFrom:
          fieldRef:
            fieldPath: "metadata.namespace"


#### Service Account
For distros of Kubernetes that support more fine-grained role-based access within the cluster, you need to make sure a pod that runs with spring-cloud-kubernetes has access to the Kubernetes API.
For example, OpenShift has very comprehensive security measures that are on by default (typically) in a shared cluster.
For any service accounts you assign to a deployment/pod, you need to make sure it has the correct roles. For example, you can add `cluster-reader` permissions to your `default` service account depending on the project you're in:

```             
oc policy add-role-to-user cluster-reader system:serviceaccount:<project/namespace>:default
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
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes</artifactId>
    <version>x.y.z</version>
</dependency>
```    
