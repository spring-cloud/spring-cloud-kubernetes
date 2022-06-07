
# Create a KinD cluster with an external load balancer

This bash script sets up a k8s cluster using kind and metallb.

The following dependencies must be installed before running the script:
  1. kubectl
  2. kind
  3. docker

Usage:

```bash
   ./setupkind.sh --cluster-name cluster1 --k8s-release 1.22.1 --ip-octet 255

Where:
   -n|--cluster-name - name of the k8s cluster to be created, cluster1 will be used if not given
   -r|--k8s-release  - the release of the k8s to setup, latest available if not given
   -s|--ip-octet     - the 3rd octet for public ip addresses, 255 if not given, valid range: 100-255
   -h|--help         - print the usage of this script
```

The `ip-octet` parameter controls the IP range that metallb will use for allocating
the public IP addresses for load balancer resources.

The public IPs are dictated by the docker network's subnet which `KinD` creates
when it creates a k8s cluster. This parameter is used as the 3rd octet for the
public IP v4 addresses when a load balancer is created. The default value is 255.
The first two octets are determined by the docker network created by `KinD`, the 4th octet
is hard coded as 200-240. As you might have guessed, for each k8s cluster one can
create at most 40 public IP v4 addresses.

The `ip-octet` parameter is not required when you create just one cluster, however, when running multiple k8s clusters it is important to proivde different values for each cluster
to avoid overlapping addresses.

For example, to create two clusters, run the script two times with the following
parameters:

```bash
  ./setupkind.sh --cluster-name cluster1 --ip-octet 255
  ./setupkind.sh --cluster-name cluster2 --ip-octet 245
```
