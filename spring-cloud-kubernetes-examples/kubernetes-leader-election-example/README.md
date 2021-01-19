# Spring Cloud Kubernetes leader election example

## Setting up the Environment

This example uses a Fabric8 Maven Plugin to deploy an application to a Kubernetes cluster.
To try it locally, download and install [Minikube](https://minikube.sigs.k8s.io/docs/start/).

Once Minikube is downloaded, start it with the following command:
```
minikube start
```

And configure environment variables to point to the Minikube's Docker daemon:
```
eval $(minikube docker-env)
```

## Overview

Spring Cloud Kubernetes leader election mechanism implements leader election API of Spring Integration using Kubernetes ConfigMap.
Multiple instances of the same application compete for a leadership of a specified role.
But only one of them can become a leader and receive an `OnGrantedEvent` application event with a leadership `Context`.
All the instances periodically try to get a leadership and whichever comes first - becomes a leader.
The new leader will remain until either its instance disappears from the cluster or it yields its leadership.
Once the leader is gone, any of the existing instances can become a new leader (even the previous leader if it yielded leadership but stayed in the cluster). 
And finally, if the leadership is yielded or revoked for some reason, the old leader receives `OnRevokedEvent` application event.

## Example application usage

Leader election mechanism uses Kubernetes ConfigMap feature to coordinate leadership information.
To access ConfigMap user needs correct role and role binding.
Create them using the following commands:
```
kubectl apply -f leader-role.yml
kubectl apply -f leader-rolebinding.yml
```

Now build and deploy the application:
```
mvn clean fabric8:deploy -Pkubernetes
```

This will deploy a single application instance to the cluster and that instance will automatically become a leader.

Create an environment variable for an easier application access:
```
SERVICE_URL=$(minikube service kubernetes-leader-election-example --url)
```

Get leadership information:
```
curl $SERVICE_URL
```

You should receive a message like this:
```
I am 'kubernetes-leader-election-example-1234567890-abcde' and I am the leader of the 'world'
```

Yield the leadership:
```
curl -X PUT $SERVICE_URL
```

And check the leadership information again:
```
curl $SERVICE_URL
```

Now you should receive a message like this:
```
I am 'kubernetes-leader-election-example-1234567890-abcde' but I am not a leader of the 'world'
```

If you wouldn't do anything for a few seconds, the same instance will become a leader again because it only yielded its leadership but stayed in the cluster.

Now scale the application to two instances and try all the steps again:
```
kubectl scale --replicas=2 deployment.apps/kubernetes-leader-election-example
```

> Note: with multiple replicas in the cluster, `curl` command will access one of them depending on the Kubernetes load balancing configuration.
Thus, when trying to yield the leadership, request might go to a non-leader node first. Just execute command again until it reaches the correct node.

> Note: instances periodically try to acquire leadership and Spring Cloud Kubernetes doesn't decide which one of them is more worth to become one.
Thus, it is possible that the instance which just yielded the leadership, made another leadership take over request faster than another instances and became a leader again.
 
