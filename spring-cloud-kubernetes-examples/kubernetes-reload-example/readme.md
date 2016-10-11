## Kubernetes Reload Example

This example demonstrate how to use the reload feature to change the configuration of a spring-boot application at runtime.

The application consists of a timed bean that periodically prints a message to the console. 
The message can be changed using a config map.

### Running the example

When using Openshift, you must assign the `view` role to the *default* service account in the current project:

```
oc policy add-role-to-user view --serviceaccount=default
```

You can deploy the application using the fabric8 maven plugin:

```
mvn clean install fabric8:build fabric8:deploy
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

To deploy the config map, just run the following command:

```
oc create -f config-map.yml
```

As soon as the config map is deployed, the output of the application changes accordingly.
The config map can be now edited with the following command:

```
oc edit configmap reload-example
```

Changes are applied immediately when using the *event* reload mode.

The name of the config map (*"reload-example"*) matches the name of the application as declared in the *application.properties* file.
