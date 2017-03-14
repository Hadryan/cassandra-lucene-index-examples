Cassandra Lucene Index Spark Examples
=====================================

Here you have some Spark examples over Cassandra Lucene queries


- `Requirements <#requirements>`__
    - `Download and compile <#download-and-compile>`__
    - `Deploy the cluster <#deploy-the-cluster>`__
- `Examples <#examples>`__
    - `Usual cassandra query <#usual-cassandra-query>`__
    - `Match query <#match-query>`__
    - `Geo-spatial bounding box query <#geo-spatial-bounding-box-query>`__
    - `Geo-spatial distance query <#geo-spatial-distance-query>`__
    - `Range query <#range-query>`__


Requirements
------------

To be able to run these examples we have created a docker container based on stratio/cassandra-lucene-index with spark
Once the docker container is built now every user can deploy a cluster with one machine acting as Spark Master and
others as Spark Workers and Cassandra nodes. Here we show you all the steps you have to follow before getting the entire
cluster working.

Download Lucene index and compile
+++++++++++++++++++++++++++++++++

Download a fresh version of this project with version:

.. code-block:: bash

    git clone https://github.com/Stratio/cassandra-lucene-index-examples.git

    cd cassandra-lucene-index-examples/

Compile and package it

.. code-block:: bash

    mvn clean package

Docker container is also built in mvn package step.

Deploy the cluster
++++++++++++++++++

As mentioned before there are two types of machines in our cluster, one is Spark Master, you can run it like this

.. code-block:: bash

    docker run -i -t --rm --name spark_master stratio/cassandra_spark

The other type of machine contains a Spark worker and Cassandra node, this machine needs to know which one is the
Spark master so we proportionate the Spark master ip (you can get the ip from log output in terminal running spark
master machine )

Run the first so:

.. code-block:: bash

    docker run -i -t --rm -e SPARK_MASTER=[SPARK_MASTER_IP] --name worker1 stratio/cassandra_spark


The rest of worker machines need almost one cassandra_seeds ip in order to form the ring so we proportionate the 
CASSANDRA_SEEDS_IP with the worker1 ip

.. code-block:: bash

    docker run -i -t --rm -e SPARK_MASTER=[SPARK_MASTER_IP] -e CASSANDRA_SEEDS=[WORKER1_IP] \
    --name worker2 stratio/cassandra_spark


You can execute the entire cluster deploy of a spark master and 3 spark workers by using docker inspect,
simply execute this script

.. code-block:: bash

    docker run -d --name spark_master stratio/cassandra_spark &&
    export SPARK_MASTER_IP=$(docker inspect -f  '{{ .NetworkSettings.IPAddress }}' spark_master) &&
    docker run -d -e SPARK_MASTER=$SPARK_MASTER_IP --name worker1 stratio/cassandra_spark &&
    export CASSANDRA_SEEDS=$(docker inspect -f  '{{ .NetworkSettings.IPAddress }}' worker1) &&
    export WORKER1_IP=$(docker inspect -f  '{{ .NetworkSettings.IPAddress }}' worker1) &&
    docker run -d -e SPARK_MASTER=$SPARK_MASTER_IP -e CASSANDRA_SEEDS=$CASSANDRA_SEEDS \
    --name worker2 stratio/cassandra_spark &&
    export WORKER2_IP=$(docker inspect -f  '{{ .NetworkSettings.IPAddress }}' worker2) &&
    docker run -d -e SPARK_MASTER=$SPARK_MASTER_IP -e CASSANDRA_SEEDS=$CASSANDRA_SEEDS -e POPULATE_TABLE=true \
    --name worker3 stratio/cassandra_spark &&
    export WORKER3_IP=$(docker inspect -f  '{{ .NetworkSettings.IPAddress }}' worker3) &&

Now you have a Cassandra/Spark running cluster. You can check the Spark cluster in spark master website
http://SPARK_MASTER_IP:8080


You will see the 3 spark workers attached to the Spark master

or the cassandra ring running in host terminal 

.. code-block:: bash

    docker exec -it worker1 nodetool status

Examples 
--------

Now having the cluster deployed and data populated, you can run the examples.

The examples are based in a table called sensors, his table with its keyspace and custom index is created with file
CreateTableAndPopulate.cql

.. code-block:: sql

    --create keyspace
    CREATE KEYSPACE spark_example_keyspace 
    WITH replication = {'class':'SimpleStrategy', 'replication_factor': 1};
    
    USE spark_example_keyspace;
    
    
    --create sensor table 
    CREATE TABLE sensors (
        id int PRIMARY KEY,
        latitude float,
        longitude float,
        lucene text,
        sensor_name text,
        sensor_type text,
        temp_value float
    );

    
    --create index 
    CREATE CUSTOM INDEX sensors_index ON spark_example_keyspace.sensors (lucene)
        USING 'com.stratio.cassandra.lucene.Index' 
        WITH OPTIONS = {
            'refresh_seconds' : '0.1',
            'schema' : '{
                fields : {
                    sensor_name : {type:"string"},
                    sensor_type : {type:"string"},
                    temp_value  : {type:"float"},
                    place : {type      :"geo_point",
                             latitude  :"latitude",
                             longitude :"longitude"}
                }
            }'
        };

The examples calculates the mean of temp_value based in several CQL lucene queries.

This project source code is prepared to be executed only via spark-submit because it is free of compulsory spark configuration
parameters that ae provided through spark-submit command line parameters.

If you edit source code to add spark basic configuration properties(app name, master url and cassandra connection) this
could be executed in spark-shell

 
Usual cassandra query
+++++++++++++++++++++

This example calculates the mean off all (1000 rows) temp values.

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcAllMean \
     --master spark://$SPARK_MASTER:7077 \
     --name calcAllMean \
     --deploy-mode client \
     --conf spark.cassandra.connection.host=172.17.0.3 \
     /home/example/cassandra-lucene-index-plugin-examples-spark.jar

     
Match query
+++++++++++

This example calculates the mean temp of sensors with sensor_type match "plane"

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByType \
     --master spark://$SPARK_MASTER:7077 \
     --name calcMeanByType \
     --deploy-mode client \
     --conf spark.cassandra.connection.host=172.17.0.3 \
     /home/example/cassandra-lucene-index-plugin-examples-spark.jar

Geo-spatial bounding box query
++++++++++++++++++++++++++++++

This example calculates the mean temp of sensors whose position in inside bounding box [(-10.0, 10.0), (-10.0, 10.0)]

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByBBOX \
     --master spark://$SPARK_MASTER:7077 \
     --name calcMeanByBBOX \
     --deploy-mode client \
     --conf spark.cassandra.connection.host=172.17.0.3 \
     /home/example/cassandra-lucene-index-plugin-examples-spark.jar

Geo-spatial distance query
++++++++++++++++++++++++++

This example calculates the mean temp of sensors whose position distance from [0.0, 0.0] is less than 100000km

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByGeoDistance \
     --master spark://$SPARK_MASTER:7077 \
     --name calcMeanByGeoDistance \
     --deploy-mode client \
     --conf spark.cassandra.connection.host=172.17.0.3 \
     /home/example/cassandra-lucene-index-plugin-examples-spark.jar

Range query
+++++++++++

This example calculates the mean temp of sensors whose temp >= 30.0

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByRange \
     --master spark://$SPARK_MASTER:7077 \
     --name calcMeanByRange \
     --deploy-mode client \
     --conf spark.cassandra.connection.host=172.17.0.3 \
     /home/example/cassandra-lucene-index-plugin-examples-spark.jar
