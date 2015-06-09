[![Stories in Ready](https://badge.waffle.io/NitorCreations/willow.png?label=ready&title=Ready)](https://waffle.io/NitorCreations/willow)
[![Stories in progress](https://badge.waffle.io/NitorCreations/willow.png?label=in%20progress&title=In%20progress)](https://waffle.io/NitorCreations/willow)
[![Stories ready to merge](https://badge.waffle.io/NitorCreations/willow.png?label=ready%20to%20merge&title=Ready%20to%20merge)](https://waffle.io/NitorCreations/willow)
[![Code Advisor On Demand Status](https://badges.ondemand.coverity.com/streams/jdq5h6193p18d9k86859ro7t0c)](https://ondemand.coverity.com/streams/jdq5h6193p18d9k86859ro7t0c/jobs)
[ ![Codeship Status for NitorCreations/willow](https://codeship.com/projects/eafd7080-e03e-0132-ef42-7a41f362b68c/status?branch=master)](https://codeship.com/projects/80769)
# willow #


## Prerequisites ##

Build requires at least Java 7 and JCE (comes in OpenJDK by default). Also phantomjs is required for javascript unit testing.

## Starting statistics ui in development mode ##
Development mode means static resources are read in from
```
willow-servers/src/main/resources
```
This enables the editing of markup, css and javascript so that changes are immediately available in the ui.

First you need to build the server.
```
$ git clone git@github.com:NitorCreations/willow.git
$ cd willow
$ mvn clean install
```
Please note that building requires unlimited JCE to be installed (openjdk has this out of the  box, oracle jdk needs you to download a set of jars and
extract them http://lmgtfy.com/?q=install+jce). NOTE: if JCE is not correctly installed, tests will fail with `java.lang.NoClassDefFoundError: Could not
initialize class javax.crypto.JceSecurity`.

Deployer agent authenticates with ssh agent signatures by default so you need to add a public key that matches a private key loaded into your ssh agent into
`willow-servers/src/main/resources/authorized_keys` in openssh authorized_keys format. You can check the private keys loaded into your ssh agent by running
`ssh-add -l`. To add a key, run `ssh-add ~/.ssh/id_rsa`.

```
$ willow-deployer/target/deployer.sh start test file:src/test/resources/develop-servers.properties
```
That starts the statistics server and starts feeding statistics from the local computer.
```
$ willow-deployer/target/deployer.sh stop test
```
That (in another terminal) stop everything. Alternatively
```
Ctrl-c Ctrl-z
$ kill -9 %1
```
In the same terminal kills the server

For windows there is willow-deployer/target/deployer.cmd that should work exactly the same way.
