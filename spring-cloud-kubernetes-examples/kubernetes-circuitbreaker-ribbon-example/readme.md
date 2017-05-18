## Kubernetes Circuit Breaker & Load Balancer Example

This example demonstrates how to use [Hystrix circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) and the [Ribbon Load Balancing](http://microservices.io/patterns/client-side-discovery.html). The circuit breaker which is backed with Ribbon will check regularly if the target service is still alive. If this is not loner the case, then a fall back process will be excuted. In our case, the REST `greeting service` which is calling the `name Service` responsible to generate the response message will reply a "fallback message" to the client if the `name service` is not longer replying.
As the Ribbon Kubernetes client is configured within this example, it will fetch from the Kubernetes API Server, the list of the endpoints available for the name service and loadbalance the request between the IP addresses available

### Running the example

This project example runs on ALL the Kubernetes or OpenShift environments, but for development purposes you can use [Minishift - OpenShift](https://github.com/minishift/minishift) or [Minikube - Kubernetes](https://kubernetes.io/docs/getting-started-guides/minikube/) tool
to install the platform locally within a virtual machine managed by VirtualBox, Xhyve or KVM, with no fuss.

### Build/Deploy using Minikube

First, create a new virtual machine provisioned with Kubernetes on your laptop using the command `minikube start`.

Next, you can compile your project and generate the Kubernetes resources (yaml files containing the definition of the pod, deployment, build, service and route to be created)
like also to deploy the application on Kubernetes in one maven line :

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
curl http://IP_OR_HOSTNAME/greeting
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

As Ribbon will question the Kubernetes API to get, base on the `name-service` name, the list of IP Addresses assigned to the service as endpoints,
you should see that you will get a response from one of the 2 pods running

```
kc get endpoints/name-service
NAME           ENDPOINTS                         AGE
name-service   172.17.0.5:8080,172.17.0.6:8080   40m
```

Here is an example about what you will get

```
curl http://IP_OR_HOSTNAME/greeting
Hello from name-service-1652024859-hf3xv!
curl http://IP_OR_HOSTNAME/greeting
Hello from name-service-1652024859-426kv!
...
```

### Test the fall back

In order to test the circuit breaker and the fallback option, you will scale the `name-service` to 0 pods as such

```
kc scale --replicas=0 deployment name-service
```

and next issue a new curl request to get the response from the greeting service

```
Hello from Fallback!
```
 
### Build/Deploy using Minishift

First, create a new virtual machine provisioned with OpenShift on your laptop using the command `minishift start`.

Next, log on to the OpenShift platform and next within your terminal use the `oc` client to create a project where
we will install the circuit breaker and load balancing application

```
oc new-project circuit-loadbalancing
```

When using OpenShift, you must assign the `view` role to the *default* service account in the current project in orde to allow our Java Kubernetes Api to access
the API Server :

```
oc policy add-role-to-user view --serviceaccount=default
```

You can now compile your project and generate the OpenShift resources (yaml files containing the definition of the pod, deployment, build, service and route to be created)
like also to deploy the application on the OpenShift platform in one maven line :

```
mvn clean install fabric8:deploy -Pkubernetes
```

### Call the Greeting service

When maven has finished to compile the code but also to call the platform in order to deploy the yaml files generated and tell to the platform to start the process
to build/deploy the docker image and create the containers where the Spring Boot application will run 'greeting-service" and "name-service", you will be able to 
check if the pods have been created using this command :

```
oc get pods --selector=project=greeting-service
```

If the status of the Spring Boot pod application is `running` and ready state `1`, then you can
get the external address IP/Hostname to be used to call the service from your laptop

```
oc get route/greeting-service 
```

and then call the service using the curl client

```
curl http://IP_OR_HOSTNAME/greeting
```

to get a response as such 

```
Hello from name-service-1-0dzb4!d
```

### Verify the load balancing

First, scale the number of pods of the `name service` to 2

```
oc scale --replicas=2 dc name-service
```

Wait a few minutes before to issue the curl request to call the Greeting Service to let the platform to create the new pod.

```
oc get pods --selector=project=name-service
NAME                   READY     STATUS    RESTARTS   AGE
name-service-1-0ss0r   1/1       Running   0          3m
name-service-1-fblp1   1/1       Running   0          36m
```

If you issue the curl request to access the greeting service, you should see that the message response
contains a different id end of the message which corresponds to the name of the pod.

```
Hello from name-service-1-0ss0r!
```

As Ribbon will question the Kubernetes API to get, base on the `name-service` name, the list of IP Addresses assigned to the service as endpoints,
you should see that you will get a different response from one of the 2 pods running

```
oc get endpoints/name-service
NAME           ENDPOINTS                         AGE
name-service   172.17.0.2:8080,172.17.0.3:8080   40m
```

Here is an example about what you will get

```
curl http://IP_OR_HOSTNAME/greeting
Hello from name-service-1-0ss0r!
curl http://IP_OR_HOSTNAME/greeting
Hello from name-service-1-fblp1!
...
```

### Test the fall back

In order to test the circuit breaker and the fallback option, you will scale the `name-service` to 0 pods as such

```
oc scale --replicas=0 dc name-service
```

and next issue a new curl request to get the response from the greeting service

```
Hello from Fallback!
```


