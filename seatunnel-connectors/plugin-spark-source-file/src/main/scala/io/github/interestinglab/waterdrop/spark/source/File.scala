/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.interestinglab.waterdrop.spark.source

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

import org.apache.spark.sql.{Dataset, Row, SparkSession}

import io.github.interestinglab.waterdrop.common.config.{CheckResult, TypesafeConfigUtils}
import io.github.interestinglab.waterdrop.spark.SparkEnvironment
import io.github.interestinglab.waterdrop.spark.batch.SparkBatchSource

class File extends SparkBatchSource {

  override def prepare(env: SparkEnvironment): Unit = {}

  override def getData(env: SparkEnvironment): Dataset[Row] = {
    val path = buildPathWithDefaultSchema(config.getString("path"), "file://")
    fileReader(env.getSparkSession, path)
  }

  override def checkConfig(): CheckResult = {
    config.hasPath("path") match {
      case true =>
        new CheckResult(true, "")
      case false =>
        new CheckResult(false, "please specify [path] as string")
    }
  }

  protected def fileReader(spark: SparkSession, path: String): Dataset[Row] = {
    val format = config.getString("format")
    var reader = spark.read.format(format)

    Try(TypesafeConfigUtils.extractSubConfigThrowable(config, "options.", false)) match {

      case Success(options) => {
        val optionMap = options
          .entrySet()
          .foldRight(Map[String, String]())((entry, m) => {
            m + (entry.getKey -> entry.getValue.unwrapped().toString)
          })

        reader = reader.options(optionMap)
      }
      case Failure(_) => // do nothing
    }

    format match {
      case "text" => reader.load(path).withColumnRenamed("value", "raw_message")
      case "parquet" => reader.parquet(path)
      case "json" => reader.json(path)
      case "orc" => reader.orc(path)
      case "csv" => reader.csv(path)
      case _ => reader.format(format).load(path)
    }
  }

  protected def buildPathWithDefaultSchema(uri: String, defaultUriSchema: String): String = {

    val path = uri.startsWith("/") match {
      case true => defaultUriSchema + uri
      case false => uri
    }

    path
  }
}
