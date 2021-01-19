
# Setting up the Environment

To play with these examples, you can install locally Kubernetes & Docker using [Minikube](https://minikube.sigs.k8s.io/docs/start/) within a Virtual Machine
managed by a hypervisor (Xhyve, Virtualbox or KVM) if your machine is not a native Unix operating system.

  
When the Minikube  is installed on your machine, you can start kubernetes using this command:
```
minikube start
```

You also probably want to configure your docker client to point the Minikube docker daemon with:
```
eval $(minikube docker-env)
```

This will make sure that the docker images that you build are available to the Minikube environment.

## Kubernetes Reload Example

This example demonstrate how to use the reload feature to change the configuration of a spring-boot application at runtime.

The application consists of a timed bean that periodically prints a message to the console. 
The message can be changed using a config map.

### Running the example
Once you have your environment set up, you can deploy the application using the fabric8 maven plugin:

```
mvn clean install fabric8:build fabric8:deploy -Pintegration
```

### Changing the configuration

Create a yaml file with the following contents:

```yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reload-example
data:
  application.properties: |-
    bean.message=Hello World!
    another.property=value
```

A sample config map is provided with this example in the *config-map.yml* file.

To deploy the config map, just run the following command on Kubernetes:

```
kubectl create -f config-map.yml
```

As soon as the config map is deployed, the output of the application changes accordingly.
The config map can be now edited with the following command:

```
kubectl edit configmap reload-example
```

Changes are applied immediately when using the *event* reload mode.

The name of the config map (*"reload-example"*) matches the name of the application as declared in the *application.properties* file.

**Note**: If you are running in a Kubernetes environment where [RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) is enabled, you need to make sure that your pod has the right level of authorizations to access the K8s APIs or resources. 
To help you get started, a sample `ServiceAccount` and `RoleBinding` configuration is provided in `src/k8s` directory. These configuration needs to be applied to your K8s cluster and the newly created `ServiceAccount` needs to be attached to your pod spec like this:

```yml
      spec:
        containers:
          image: <image_loc>
          imagePullPolicy: IfNotPresent
          livenessProbe:
            failureThreshold: 3
            httpGet:
              path: /actuator/health
              port: 8080
              scheme: HTTP
            initialDelaySeconds: 180
            successThreshold: 1
          name: spring-boot
          ports:
          - containerPort: 8080
            name: http
            protocol: TCP
          - containerPort: 9779
            name: prometheus
            protocol: TCP
          readinessProbe:
            failureThreshold: 3
            httpGet:
              path: /actuator/health
              port: 8080
              scheme: HTTP
            initialDelaySeconds: 10
            successThreshold: 1
          securityContext:
            privileged: false
        serviceAccountName: <service_account_name>
```
