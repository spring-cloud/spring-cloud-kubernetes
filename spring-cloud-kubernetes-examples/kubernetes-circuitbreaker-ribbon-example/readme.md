## Kubernetes Circuit Breaker & Load Balancer Example

This example demonstrates how to use [Hystrix circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) and the [Ribbon Load Balancing](http://microservices.io/patterns/client-side-discovery.html). The circuit breaker which is backed with Ribbon will check regularly if the target service is still alive. If this is not loner the case, then a fall back process will be excuted. In our case, the REST `greeting service` which is calling the `name Service` responsible to generate the response message will reply a "fallback message" to the client if the `name service` is not longer replying.
As the Ribbon Kubernetes client is configured within this example, it will fetch from the Kubernetes API Server, the list of the endpoints available for the name service and loadbalance the request between the IP addresses available

### Running the example

First, log on to the OpenShift platform and next within your terminal use the `oc` client to create a project where
we will install the circuit breaker and load balancing application

```
oc create project circuit-loadbalancing
```

When using Openshift, you must assign the `view` role to the *default* service account in the current project:

```
oc policy add-role-to-user view --serviceaccount=default
```

You can compile your project and generate the OpenShift/Kubernetes resources (yaml files contaning the defintion of the pod, deployment, build, service and route)
like also to deploy the application on Openshift in one maven line :

```
mvn clean install fabric8:deploy -Pintegration
```

### Call the Greeting service

```
curl http://greeting-service-circuit-lb.192.168.64.25.nip.io/greeting
```

### Verify the load balancing

First, scale the number of pods of the name service to 2

```
oc scale --replicas=2 dc name-service
```

Wait a few minutes before to issue the curl request to call the Greeting Service. You should see that the message response
contains a different id end of the message which corresponds to the name of the pod.

```
Hello from name-service-1-0ss0r!
```

and the Name Service pods running are 

```
oc get pods --selector=project=name-service
NAME                   READY     STATUS    RESTARTS   AGE
name-service-1-0ss0r   1/1       Running   0          3m
name-service-1-fblp1   1/1       Running   0          36m
```

As Ribbon will question the Kubernetes API to get, base on the `name-service` name, the list of IP Addresses assigned to the service as endpoints,
you should see that you will get a reponse from one of the 2 pods running

```
oc get endpoints/name-service
NAME           ENDPOINTS                         AGE
name-service   172.17.0.2:8080,172.17.0.3:8080   40m
```

Response received

```
histor
Hello from name-service-1-0ss0r!
curl http://greeting-service-circuit-lb.192.168.64.25.nip.io/greeting
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


