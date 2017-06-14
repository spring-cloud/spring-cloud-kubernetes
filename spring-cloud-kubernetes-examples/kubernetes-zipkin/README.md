## Kubernetes Zipkin Example

This project demonstrates how a Spring Boot application generating statistics as Spring Cloud Sleuth Spans/Traces can send them to a ZipKin server deployed in Kubernetes without the need to
configure the baseUrl of the ZipKin server deployed as the server will be discovered. The spans/traces generated can be viewed within the Zipkin dashboard under the `serviceName=sleuth-zipkin`

The Zipkin server is deployed according to the steps described within the `Minishift` or `Minikube` section.

The project exposes under the `TraceController` 2 endpoints `/` and `/hi` that you can play with in order to generate traces. When you call the root endpoint `/`, then
it will issue a call against the second endpoint `/hi` and you will receive `/hi/hello` as response. If you look to the Zipkin dashboard, you will be able to get 2 traces recorded.


```
	@RequestMapping("/")
	public String say() throws InterruptedException {
		Thread.sleep(this.random.nextInt(1000));
		log.info("Home");
		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/hi", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/hi")
	public String hi() throws InterruptedException {
		log.info("hi");
		int millis = this.random.nextInt(1000);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));
		return "hello";
	}
```

### Running the example

This project example runs on ALL the Kubernetes or OpenShift environments, but for development purposes you can use [Minishift - OpenShift](https://github.com/minishift/minishift) or [Minikube - Kubernetes](https://kubernetes.io/docs/getting-started-guides/minikube/) tool
to install the platform locally within a virtual machine managed by VirtualBox, Xhyve or KVM, with no fuss.

### Build/Deploy using Minikube 

First, create a new virtual machine provisioned with Kubernetes on your laptop using the command `minikube start`.

To deploy the Zipkin server and store the traces under a MySQL server, execute the following commands to create a persistent volume for the database
and next to deploy the Zipkin application

```
kubectl create -f http://repo1.maven.org/maven2/io/fabric8/zipkin/zipkin-starter-minimal/0.1.9/zipkin-starter-minimal-0.1.9-kubernetes.yml

cat << EOF | kubectl create -f -
kind: PersistentVolume
apiVersion: v1
metadata:
  name: pv0001
  labels:
    type: local
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/tmp/data01"
EOF    

cat << EOF | kubectl create -f - 
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-data
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
EOF
```

Next, you can compile your project and generate the Kubernetes resources (yaml files containing the definition of the pod, deployment, build, service and route to be created)
like also to deploy the application on Kubernetes in one maven line :

```
mvn clean install fabric8:deploy -Dservice.type=NodePort -Dfabric8.generator.from=fabric8/java-jboss-openjdk8-jdk -Pkubernetes
```

You can find the address of the zipkin server to be opened within your browser using this command

```
minikube service zipkin --url
```

like also the endpoint to call to generate traces

```
export ENDPOINT=$(minikube service kubernetes-zipkin --url)
curl $ENDPOINT
curl $ENDPOINT/hi
```

### Build/Deploy using Minishift

First, create a new virtual machine provisioned with OpenShift on your laptop using the command `minishift start`.

Next, log on to the OpenShift platform and next within your terminal use the `oc` client to create a project where
we will install the circuit breaker and load balancing application

```
oc new-project zipkin
```

When using OpenShift, you must assign the `view` role to the *default* service account in the current project in order to allow our Java Kubernetes Api to access
the API Server :

```
oc policy add-role-to-user view --serviceaccount=default
```

To deploy the Zipkin server and store the traces under a MySQL server, execute the following commands to deploy the Zipkin application

```
oc create -f http://repo1.maven.org/maven2/io/fabric8/zipkin/zipkin-starter-minimal/0.1.9/zipkin-starter-minimal-0.1.9-openshift.yml
oc delete pvc/mysql-data

cat << EOF | oc create -f - 
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-data
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
EOF
```

You can now compile your project and generate the OpenShift resources (yaml files containing the definition of the pod, deployment, build, service and route to be created)
like also to deploy the application on the OpenShift platform in one maven line :

```
mvn clean install fabric8:deploy -Pkubernetes
```

You can find the address of the Zipkin server to be opened within your browser using this command

```
oc get route/zipkin --template='{{.spec.host}}'
```

like also the endpoint to call to generate traces

```
export ENDPOINT=$(oc get route/kubernetes-zipkin --template='{{.spec.host}}')
curl $ENDPOINT
curl $ENDPOINT/hi
```
