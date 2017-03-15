# Hello World Example

This is a `Hello World` spring cloud application.

To run the application on Kubernetes:

     mvn clean package fabric8:build fabric8:deploy fabric8:start
     
To run the integration tests:     

	mvn clean install -Pintegration
