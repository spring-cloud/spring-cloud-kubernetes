
# Setting up the Environment

To play with these examples, you can install locally Kubernetes & Docker using `[Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/)` within a Virtual Machine
managed by a hypervisor (Xhyve, Virtualbox or KVM) if your machine is not a native Unix operating system.

  
When the minikube  is installed on your machine, you can start kubernetes using this command:
```
minikube start
```

You also probably want to configure your docker client to point the minikube docker deamon with:
```
eval $(minikube docker-env)
```

This will make sure that the docker images that you build are available to the minikube environment.

## Kubernetes Reload Example

This example demonstrate how to use the reload feature to change the configuration of a spring-boot application at runtime.

The application consists of a timed bean that periodically prints a message to the console. 
The message can be changed using a config map.

### Running the example
Once you have your environment set up, you can deploy the application using the fabric8 maven plugin:

```
mvn clean install fabric8:build fabric8:deploy -Pintegration
```

**Note**: Unfortuntaly, when you deploy using the fabric8 plugin, the readyness and liveness probes fail to point to the right actuator URL due a lack of support for spring boot. 
This push you to edit the generated deployment inside kubernetes and change these probes which points to "path": "/health" to  "path": "/actuator/health". 
This will make your deployment go green. This issue is already reported into the fabric8 community: https://github.com/fabric8io/fabric8-maven-plugin/issues/1178

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
