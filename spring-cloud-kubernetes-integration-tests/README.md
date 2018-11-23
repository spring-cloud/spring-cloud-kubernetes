# Purpose

Explain how integration tests can be run against any k8s cluster. 
One way of running such a kubernetes cluster locally which is leveraged by our CI setup is [microk8s](https://microk8s.io/)
The Kubernetes resources necessary to get the test application
onto the cluster are created using the [Fabric8 Maven Plugin](https://maven.fabric8.io/) and the
lifecycle of the test applications is controlled by [Arquillian Cube](http://arquillian.org/arquillian-cube/)

# Basics

With FMP and Arquillian Cube setup for our project we need to configure the following things in order to properly get
our test applications onto our cluster of choice:

* The `KUBECONFIG` environment variable needs to be set to the location of the Kubernetes configuration file
we will use to access our cluster (this can be skipped if this file is already present in the standard locations that kubectl assumes)
* The `docker.host` system property needs to be set to the URL where the docker daemon we will use to build images is listening
This can be skipped when we use the default unix socket on a Linux machine
* The Docker image registry were out built images will be stored needs to be set using the `image.registry` environment variable   

# Instructions

Here we will describe the steps that are needed to setup microk8s and the maven command we will use to run the integration
tests against that microk8s cluster 

## Install microk8s

```bash
sudo snap install microk8s --classic --channel=1.11/stable
sleep 10
microk8s.enable dns registry
```

Ensure everything is running by inspecting the output of:

```bash
microk8s.kubectl get all --all-namespaces
```

It should look something like:

```
NAMESPACE            NAME                                       READY     STATUS    RESTARTS   AGE
container-registry   pod/registry-6bc95dfd76-274lc              1/1       Running   0          40s
kube-system          pod/hostpath-provisioner-9979c7f64-f96tw   1/1       Running   0          41s
kube-system          pod/kube-dns-864b8bdc77-68kmn              2/3       Running   0          41s

NAMESPACE            NAME                 TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
container-registry   service/registry     NodePort    10.152.183.175   <none>        5000:32000/TCP   50s
default              service/kubernetes   ClusterIP   10.152.183.1     <none>        443/TCP          1m
kube-system          service/kube-dns     ClusterIP   10.152.183.10    <none>        53/UDP,53/TCP    56s

NAMESPACE            NAME                                   DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
container-registry   deployment.apps/registry               1         1         1            1           50s
kube-system          deployment.apps/hostpath-provisioner   1         1         1            1           51s
kube-system          deployment.apps/kube-dns               1         1         1            0           56s

NAMESPACE            NAME                                             DESIRED   CURRENT   READY     AGE
container-registry   replicaset.apps/registry-6bc95dfd76              1         1         1         41s
kube-system          replicaset.apps/hostpath-provisioner-9979c7f64   1         1         1         41s
kube-system          replicaset.apps/kube-dns-864b8bdc77              1         1         0         41s
```

Export the kube config file that will be used to access this cluster

```bash
microk8s.kubectl config view --raw > /tmp/kubeconfig
```


## Launch tests

```bash
cd spring-cloud-kubernetes-integration-tests
KUBECONFIG=/tmp/kubeconfig mvn -Ddocker.host='unix:///var/snap/microk8s/current/docker.sock' -Dimage.registry='localhost:32000' clean package fabric8:build verify -Pfmp,it
```

The command above will for each test project:

* Build the ubjerjar
* Build a docker image based on that uberjar
* Launch the Arquillian Cube tests which will
    - Deploy the application to the cluster
    - Launch the test code
    - Undeploy the application
    
    
## Launching one of the applications manually

Each of the test modules can also be launched manually. This can be very useful for debugging purposes.
For example to launch the `simple-core` application
    
 ```bash
 cd spring-cloud-kubernetes-integration-tests/simple-core
 KUBECONFIG=/tmp/kubeconfig mvn -Ddocker.host='unix:///var/snap/microk8s/current/docker.sock' -Dimage.registry='localhost:32000' clean package fabric8:build fabric8:deploy -Pfmp
 ```
 
 When it's time to take down the application, simply execute: 
 

 ```bash
  KUBECONFIG=/tmp/kubeconfig mvn fabric8:undeploy -Pfmp
  ```
