Cassandra Lucene Index Spark Examples
=====================================

Here you have some Spark examples over Cassandra Lucene queries


- `Requirements <#requirements>`__
    - `Download and compile <#download-and-compile>`__
    - `Build docker container <#build-docker-container>`__
    - `Deploy the cluster <#deploy-the-cluster>`__
    - `Create example environment <#create-example-environment>`__
- `Examples <#examples>`__
    - `Usual cassandra query <#usual-cassandra-query>`__
    - `Match query <#match-query>`__
    - `Geo-spatial bounding box query <#geo-spatial-bounding-box-query>`__
    - `Geo-spatial distance query <#geo-spatial-distance-query>`__
    - `Range query <#range-query>`__


Requirements
------------

To be able to run these examples we have created a Debian-based Docker container with java 7.80.15 maven 3.3.3, Spark
1.4.1 with Apache Hadoop 2.6, Apache Cassandra 2.1.9 and Stratio’s Cassandra Lucene Index 2.1.9.0.
Once the docker container is built know every user can deploy a cluster with one machine acting as Spark Master and
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


Build docker container
++++++++++++++++++++++

If you don't have docker installed then run:

.. code-block:: bash

    sudo apt-get install docker 


Go to Docker containers directory from cassandra lucene index base directory:

.. code-block:: bash

    cd cassandra-lucene-index-examples-spark/resources/docker
    
Build the Docker container, this will take a while, please be patient

.. code-block:: bash
    
    docker build -t stratio/cassandra_spark .

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
    docker run -d -e SPARK_MASTER=$SPARK_MASTER_IP -e CASSANDRA_SEEDS=$CASSANDRA_SEEDS \
    --name worker2 stratio/cassandra_spark &&
    docker run -d -e SPARK_MASTER=$SPARK_MASTER_IP -e CASSANDRA_SEEDS=$CASSANDRA_SEEDS \
    --name worker3 stratio/cassandra_spark

Now you have a Cassandra/Spark running cluster. You can check the Spark cluster in spark master website
http://SPARK_MASTER_IP:8080


You will see the 3 spark workers attached to the Spark master

or the cassandra ring running in host terminal 

.. code-block:: bash

    docker exec -it worker1 nodetool status

Create example environment
++++++++++++++++++++++++++

When you have your cluster running you can execute the CreateTableAndPopulate.cql, this file with the jar containing
examples' code is in /home/example in docker containers, so you don't need to copy anything.
 
Open a terminal in any of the workers 

.. code-block:: bash

    docker exec -it worker1 /bin/bash 


Run CreateTableAndPopulate.cql script located in /home/example directory  by CQL shell
    
.. code-block:: bash

    cqlsh -f /home/example/CreateTableAndPopulate.cql $(hostname --ip-address)
    

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


Every example can be executed via spark-submit or in a spark-shell. To run in spark-shell run above line to start
spark-shell in any of the workers

.. code-block:: bash

     spark-shell --master spark://$SPARK_MASTER:7077 --jars /home/example/cassandra-lucene-index-plugin-examples-spark.jar



As you can see the spark-shell examples are just like the scala code just taking out the SparkContext contruction
line because spark-shell builds it while starting
 
Usual cassandra query
+++++++++++++++++++++

This example calculates the mean off all (1000 rows) temp values.

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcAllMean \
     --master spark://$SPARK_MASTER:7077 \
     --deploy-mode client /home/example/cassandra-lucene-index-plugin-examples-spark.jar
     

From spark-shell:

.. code-block:: bash 

    import com.datastax.spark.connector._

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"

    var totalMean = 0.0f

    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value")

    val temperatureRdd=tempRdd.map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =temperatureRdd.count()

    if (totalNumElems>0) {
        val pairTempRdd = temperatureRdd.map(s => (1, s))
        val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
        totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }
    println("Mean calculated on all data, mean: "+totalMean.toString
            +" numRows: "+ totalNumElems.toString)

     
     
Match query
+++++++++++

This example calculates the mean temp of sensors with sensor_type match "plane"

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByType \
     --master spark://$SPARK_MASTER:7077 \
     --deploy-mode client /home/example/cassandra-lucene-index-plugin-examples-spark.jar



From spark-shell:

.. code-block:: bash

    import com.datastax.spark.connector._
    import com.stratio.cassandra.lucene.search.SearchBuilders._

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"
    val INDEX_COLUMN_CONSTANT: String = "lucene"
    var totalMean = 0.0f

    val luceneQuery: String = search.refresh(true).filter(`match`("sensor_type", "plane")).toJson

    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value")
    val whereRdd=tempRdd.where(INDEX_COLUMN_CONSTANT+ "= ?",luceneQuery)

    val mapRdd=whereRdd.map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =mapRdd.count()

    if (totalNumElems>0) {
        val pairTempRdd = mapRdd.map(s => (1, s))
        val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
        totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }

    println("Mean calculated on type query data, mean: "+totalMean.toString
            +", numRows: "+ totalNumElems.toString)


Geo-spatial bounding box query
++++++++++++++++++++++++++++++

This example calculates the mean temp of sensors whose position in inside bounding box [(-10.0, 10.0), (-10.0, 10.0)]

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByBBOX \
     --master spark://$SPARK_MASTER:7077 \
     --deploy-mode client /home/example/cassandra-lucene-index-plugin-examples-spark.jar


From spark-shell:

.. code-block:: bash

    import com.datastax.spark.connector._
    import com.stratio.cassandra.lucene.search.SearchBuilders._

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"
    val INDEX_COLUMN_CONSTANT: String = "lucene"
    var totalMean = 0.0f

    val luceneQuery = search.refresh(true).filter(geoBBox("place", -10.0f, 10.0f, -10.0f, 10.0f)).toJson

    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value")
    val whereRdd=tempRdd.where(INDEX_COLUMN_CONSTANT+ "= ?", luceneQuery)
    val mapRdd=whereRdd.map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =mapRdd.count()

    if (totalNumElems>0) {
        val pairTempRdd = mapRdd.map(s => (1, s))
        val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
        totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }

    println("Mean calculated on BBOX query data, mean: "+totalMean.toString
            +" , numRows: "+ totalNumElems.toString)



Geo-spatial distance query
++++++++++++++++++++++++++

This example calculates the mean temp of sensors whose position distance from [0.0, 0.0] is less than 100000km

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByGeoDistance \
     --master spark://$SPARK_MASTER:7077 \
     --deploy-mode client /home/example/cassandra-lucene-index-plugin-examples-spark.jar

From spark-shell:

.. code-block:: bash

    import com.datastax.spark.connector._
    import com.stratio.cassandra.lucene.search.SearchBuilders._

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"
    val INDEX_COLUMN_CONSTANT: String = "lucene"
    var totalMean = 0.0f

    val luceneQuery = search.refresh(true).filter(geoDistance("place", 0.0f, 0.0f, "100000km")).toJson

    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value")
    val whereRdd=tempRdd.where(INDEX_COLUMN_CONSTANT+ "= ?",luceneQuery)
    val mapRdd=whereRdd.map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =mapRdd.count()

    if (totalNumElems>0) {
        val pairTempRdd = mapRdd.map(s => (1, s))
        val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
        totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }

    println("Mean calculated on GeoDistance data, mean: "+totalMean.toString
            +" , numRows: "+totalNumElems.toString)

Range query
+++++++++++

This example calculates the mean temp of sensors whose temp >= 30.0

From terminal:

.. code-block:: bash

     spark-submit --class com.stratio.cassandra.examples.spark.calcMeanByRange \
     --master spark://$SPARK_MASTER:7077 \
     --deploy-mode client /home/example/cassandra-lucene-index-plugin-examples-spark.jar

From spark-shell:

.. code-block:: bash

    import com.datastax.spark.connector._
    import com.stratio.cassandra.lucene.search.SearchBuilders._

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"
    val INDEX_COLUMN_CONSTANT: String = "lucene"
    var totalMean = 0.0f

    val luceneQueryAux = range("temp_value").includeLower(true).lower(30.0f)
    val luceneQuery: String=search.refresh(true).filter(luceneQueryAux).toJson


    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value")
    val whereRdd=tempRdd.where(INDEX_COLUMN_CONSTANT+ "= ?",luceneQuery)
    val mapRdd=whereRdd.map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =mapRdd.count()

    if (totalNumElems>0) {
        val pairTempRdd = mapRdd.map(s => (1, s))
        val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
        totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }

    println("Mean calculated on range type data, mean: "+totalMean.toString
        +" , numRows: "+ totalNumElems.toString)
