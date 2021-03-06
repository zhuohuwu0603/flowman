# Installation Guide

## Requirements

Flowman brings many dependencies with the installation archive, but everything related to Hadoop or Spark needs to 
be provided by your platform. This approach ensures that the existing Spark and Hadoop installation is used together
with all patches and extensions available on your platform. Specifically this means that Flowman requires the following
components present on your system:

* Java 1.8
* Apache Spark with a matching minor version 
* Apache Hadoop with a matching minor version

Note that Flowman can be built for different Hadoop and Spark versions, and the major and minor version of the build
needs to match the ones of your platform
 

## Local Installation

Flowman is distributed as a `tar.gz` file, which simply needs to be extracted at some location on your computer or 
server. This can be done via
```shell script
tar xvzf flowman-dist-X.Y.Z-bin.tar.gz
```

### Directory Layout

```bash
├── bin
├── conf
├── examples
│   ├── plugin-example
│   │   └── job
│   ├── sftp-upload
│   │   ├── config
│   │   ├── data
│   │   └── job
│   └── weather
│       ├── config
│       ├── job
│       ├── mapping
│       ├── model
│       └── target
├── lib
├── libexec
└── plugins
    ├── flowman-aws
    ├── flowman-azure
    ├── flowman-example
    ├── flowman-impala
    ├── flowman-kafka
    └── flowman-mariadb
```

* The `bin` directory contains the Flowman executables
* The `conf` directory contains global configuration files
* The `lib` directory contains all Java jars
* The `libexec` directory contains some internal helper scripts
* The `plugins` directory contains more subdirectories, each containing a single plugin
* The `examples` directory contains some example projects 


## Configuration

You probably need to perform some basic global configuration of Flowman. The relevant files are stored in the `conf`
directory.

### `flowman-env.sh`

The `flowman-env.sh` script sets up an execution environment on a system level. Here some very fundamental Spark
and Hadoop properties can be configured, like for example
* `SPARK_HOME`, `HADOOP_HOME` and related environment variables
* `KRB_PRINCIPAL` and `KRB_KEYTAB` for using Kerberos
* Generic Java options like http proxy and more

#### Example
```shell script
#!/usr/bin/env bash

# Specify Java home (just in case)
#
#export JAVA_HOME

# Explicitly override Flowmans home. These settings are detected automatically,
# but can be overridden
#
#export FLOWMAN_HOME
#export FLOWMAN_CONF_DIR

# Specify any environment settings and paths
#
#export SPARK_HOME
#export HADOOP_HOME
#export HADOOP_CONF_DIR=${HADOOP_CONF_DIR=$HADOOP_HOME/conf}
#export YARN_HOME
#export HDFS_HOME
#export MAPRED_HOME
#export HIVE_HOME
#export HIVE_CONF_DIR=${HIVE_CONF_DIR=$HIVE_HOME/conf}

# Set the Kerberos principal in YARN cluster
#
#KRB_PRINCIPAL=
#KRB_KEYTAB=

# Specify the YARN queue to use
#
#YARN_QUEUE=

# Use a different spark-submit (for example spark2-submit in Cloudera)
#
#SPARK_SUBMIT=


# Apply any proxy settings from the system environment
#
if [[ "$PROXY_HOST" != "" ]]; then
    SPARK_DRIVER_JAVA_OPTS="
        -Dhttp.proxyHost=${PROXY_HOST}
        -Dhttp.proxyPort=${PROXY_PORT}
        -Dhttps.proxyHost=${PROXY_HOST}
        -Dhttps.proxyPort=${PROXY_PORT}
        $SPARK_DRIVER_JAVA_OPTS"

    SPARK_EXECUTOR_JAVA_OPTS="
        -Dhttp.proxyHost=${PROXY_HOST}
        -Dhttp.proxyPort=${PROXY_PORT}
        -Dhttps.proxyHost=${PROXY_HOST}
        -Dhttps.proxyPort=${PROXY_PORT}
        $SPARK_EXECUTOR_JAVA_OPTS"

    SPARK_OPTS="
        --conf spark.hadoop.fs.s3a.proxy.host=${PROXY_HOST}
        --conf spark.hadoop.fs.s3a.proxy.port=${PROXY_PORT}
        $SPARK_OPTS"
fi

# Set AWS credentials if required. You can also specify these in project config
#
if [[ "$AWS_ACCESS_KEY_ID" != "" ]]; then
    SPARK_OPTS="
        --conf spark.hadoop.fs.s3a.access.key=${AWS_ACCESS_KEY_ID}
        --conf spark.hadoop.fs.s3a.secret.key=${AWS_SECRET_ACCESS_KEY}
        $SPARK_OPTS"
fi
```

### `system.yml`
After the execution environment has been setup, the `system.yml` is the first configuration file processed by the Java
application. Its main purpose is to load some fundamental plugins, which are already required by the next level of 
configuration 

#### Example
```yaml
plugins:
  - flowman-impala
```

### `default-namespace.yml`
On top of the very global settings, Flowman also supports so called *namespaces*. Each project is executed within the
context of one namespace, if nothing else is specified the *defautlt namespace*. Each namespace contains some 
configuration, such that different namespaces might represent different tenants or different staging environments.

#### Example
```yaml
name: "default"

history:
  kind: jdbc
  connection: flowman_state
  retries: 3
  timeout: 1000

connections:
  flowman_state:
    driver: $System.getenv('FLOWMAN_HISTORY_DRIVER', 'org.apache.derby.jdbc.EmbeddedDriver')
    url: $System.getenv('FLOWMAN_HISTORY_URL', $String.concat('jdbc:derby:', $System.getenv('FLOWMAN_HOME'), '/logdb;create=true'))
    username: $System.getenv('FLOWMAN_HISTORY_USER', '')
    password: $System.getenv('FLOWMAN_HISTORY_PASSWORD', '')

config:
  - spark.sql.warehouse.dir=$System.getenv('FLOWMAN_HOME')/hive/warehouse
  - hive.metastore.uris=
  - javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=$System.getenv('FLOWMAN_HOME')/hive/db;create=true
  - datanucleus.rdbms.datastoreAdapterClassName=org.datanucleus.store.rdbms.adapter.DerbyAdapter

plugins:
  - flowman-example
  - flowman-hbase
  - flowman-aws
  - flowman-azure
  - flowman-kafka
  - flowman-mariadb

store:
  kind: file
  location: $System.getenv('FLOWMAN_HOME')/examples
```


## Running in a Kerberized Environment


## Deploying with Docker
