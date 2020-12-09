## Kubernetes Spring Cloud LoadBalancer Example

This example demonstrates how to use  [Spring Cloud LoadBalancer](https://github.com/spring-cloud-incubator/spring-cloud-loadbalancer). In our example the REST `greeting service` calls the `name service`.
As the Spring Cloud LoadBalancer for Kubernetes is configured within this example, it will fetch from the Kubernetes API Server the list of the endpoints available for the name service and load-balance the request between the IP addresses available.

### Running the example

This project example runs on ALL the Kubernetes or OpenShift environments, but for development purposes you can use [Minikube - Kubernetes](https://kubernetes.io/docs/getting-started-guides/minikube/) tool
to install the platform locally within a virtual machine managed by VirtualBox, Xhyve or KVM, with no fuss.

IMPORTANT: In order for this setup to work, you need to grant permissions to retrieve "pods", "services" and "enpoints" to the serviceaccont that will be used with the greeting-service.

### Build/Deploy using Minikube

First, create a new virtual machine provisioned with Kubernetes on your laptop using the command `minikube start`.

Next, you can compile your project and generate the Kubernetes resources (yaml files containing the definition of the pod, deployment, build, service and route to be created)
like also to deploy the application on Kubernetes in one maven line by running:

```
mvn clean install fabric8:deploy -Dfabric8.generator.from=fabric8/java-jboss-openjdk8-jdk -Pkubernetes
```

### Call the Greeting service

When maven has finished to compile the code but also to call the platform in order to deploy the yaml files generated and tell to the platform to start the process
to build/deploy the docker image and create the containers where the Spring Boot application will run 'greeting-service" and "name-service", you will be able to 
check if the pods have been created using this command :

```
kc get pods
```

If the status of the Spring Boot pod application is `running` and ready state `1`, then you can
get the external address IP/Hostname to be used to call the service from your laptop

```
minikube service --url greeting-service 
```

and then call the service using the curl client

```
curl https://IP_OR_HOSTNAME/greeting
```

to get a response as such 

```
Hello from name-service-1-0dzb4!d
```

### Verify the load balancing

First, scale the number of pods of the `name service` to 2

```
kc scale --replicas=2 deployment name-service
```

Wait a few minutes before to issue the curl request to call the Greeting Service to let the platform to create the new pod.

```
kc get pods --selector=project=name-service
NAME                            READY     STATUS    RESTARTS   AGE
name-service-1652024859-fsnfw   1/1       Running   0          33s
name-service-1652024859-wrzjs   1/1       Running   0          6m
```

If you issue the curl request to access the greeting service, you should see that the message response
contains a different id end of the message which corresponds to the name of the pod.

```
Hello from name-service-1-0ss0r!
```

As Spring Cloud LoadBalancer will question the Kubernetes API to get, base on the `name-service` name, the list of IP Addresses assigned to the service as endpoints,
you should see that you will get a response from one of the 2 pods running

```
kc get endpoints/name-service
NAME           ENDPOINTS                         AGE
name-service   172.17.0.5:8080,172.17.0.6:8080   40m
```

Here is an example about what you will get

```
curl https://IP_OR_HOSTNAME/greeting
Hello from name-service-1652024859-hf3xv!
curl https://IP_OR_HOSTNAME/greeting
Hello from name-service-1652024859-426kv!
...
```
