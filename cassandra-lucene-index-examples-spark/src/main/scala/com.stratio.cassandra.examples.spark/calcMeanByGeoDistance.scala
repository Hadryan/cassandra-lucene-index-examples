/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.cassandra.examples.spark

import com.datastax.spark.connector._
import com.stratio.cassandra.lucene.builder.Builder._
import org.apache.spark.{SparkConf, SparkContext}


object calcMeanByGeoDistance {
  def main(args: Array[String]) {

    val KEYSPACE: String = "spark_example_keyspace"
    val TABLE: String = "sensors"
    val INDEX_COLUMN_CONSTANT: String = "lucene"
    var totalMean = 0.0f
    val t1 = System.currentTimeMillis
    val luceneQuery = search.refresh(true).filter(geoDistance("place", 0.0f, 0.0f, "100000km")).build()
    val sparkConf = new SparkConf(true).setMaster("local[*]").setAppName("app").set("spark.cassandra.connection.host", "127.0.0.1")
    val sc : SparkContext = new SparkContext(sparkConf)

    val tempRdd=sc.cassandraTable(KEYSPACE, TABLE).select("temp_value").where(INDEX_COLUMN_CONSTANT+ "= ?",luceneQuery).map[Float]((row)=>row.getFloat("temp_value"))

    val totalNumElems: Long =tempRdd.count()

    if (totalNumElems>0) {
      val pairTempRdd = tempRdd.map(s => (1, s))
      val totalTempPairRdd = pairTempRdd.reduceByKey((a, b) => a + b)
      totalMean = totalTempPairRdd.first()._2 / totalNumElems.toFloat
    }
    val t2 = System.currentTimeMillis
    println("Mean calculated on GeoDistance data, mean: "+totalMean.toString+" , numRows: "+totalNumElems.toString+" took "+(t2-t1)+" msecs")
  }
}