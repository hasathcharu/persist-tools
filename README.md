Ballerina Persist Tools
===================

[![Build](https://github.com/ballerina-platform/persist-tools/actions/workflows/build-timestamped-master.yml/badge.svg)](https://github.com/ballerina-platform/persist-tools/actions/workflows/build-timestamped-master.yml)
[![codecov](https://codecov.io/gh/ballerina-platform/persist-tools/branch/main/graph/badge.svg)](https://codecov.io/gh/ballerina-platform/persist-tools)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/ballerina-platform/persist-tools.svg)](https://github.com/ballerina-platform/persist-tools/commits/main)

The Ballerina `persist` Tool which supports several operations on the Ballerina Persistence Layer. Ballerina Persistent Layer provides functionality to store and query data conveniently.

The `persist` commands will make it easy to enable Ballerina Persistence Layer in a bal project. With this support, users need not worry about the persistence layer in a project. Users can define an entity data model, validate the model and generate `persist` clients, which provide convenient APIs to store and query data in a data store.

For more information, see [`persist` API Documentation](https://lib.ballerina.io/ballerina/persist/latest).

## Issues and projects

Issues and Projects tabs are disabled for this repository as this is part of the Ballerina Standard Library. To report bugs, request new features, start new discussions, view project boards, etc. please visit Ballerina Standard Library [parent repository](https://github.com/ballerina-platform/ballerina-standard-library).

This repository only contains the source code for the package.

## Build from the source

### Set up the prerequisites

1. Download and install Java SE Development Kit (JDK) version 11 (from one of the following locations).
    * [Oracle](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)

    * [OpenJDK](https://adoptium.net/)

      > **Note:** Set the JAVA_HOME environment variable to the path name of the directory into which you installed JDK.
2. Export Github Personal access token with read package permissions as follows,

        export packageUser=<Username>
        export packagePAT=<Personal access token>

### Build the source

Execute the commands below to build from source.

1. To build the package:
    ```    
    ./gradlew clean build
    ```
2. To run the tests:
    ```
    ./gradlew clean test
    ```

3. To run a group of tests
    ```
    ./gradlew clean test -Pgroups=<test_group_names>
    ```

4. To build the without the tests:
    ```
    ./gradlew clean build -x test
    ```

5. To debug package implementation:
    ```
    ./gradlew clean build -Pdebug=<port>
    ```

6. To debug with Ballerina language:
    ```
    ./gradlew clean build -PbalJavaDebug=<port>
    ```

## Contributing to Ballerina

As an open source project, Ballerina welcomes contributions from the community. 

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All contributors are encouraged to read the [Ballerina code of conduct](https://ballerina.io/code-of-conduct).

## Useful links

* Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
* Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.
