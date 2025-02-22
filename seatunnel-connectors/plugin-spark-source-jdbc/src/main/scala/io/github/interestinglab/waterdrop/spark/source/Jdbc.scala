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

import org.apache.spark.sql.{DataFrameReader, Dataset, Row, SparkSession}

import io.github.interestinglab.waterdrop.common.config.{CheckResult, TypesafeConfigUtils}
import io.github.interestinglab.waterdrop.spark.SparkEnvironment
import io.github.interestinglab.waterdrop.spark.batch.SparkBatchSource

class Jdbc extends SparkBatchSource {

  override def prepare(env: SparkEnvironment): Unit = {}

  override def getData(env: SparkEnvironment): Dataset[Row] = {
    jdbcReader(env.getSparkSession, config.getString("driver")).load()
  }

  override def checkConfig(): CheckResult = {
    val requiredOptions = List("url", "table", "user", "password");

    val nonExistsOptions = requiredOptions
      .map(optionName => (optionName, config.hasPath(optionName)))
      .filter { p =>
        val (optionName, exists) = p
        !exists
      }
    if (nonExistsOptions.isEmpty) {
      new CheckResult(true, "")
    } else {
      new CheckResult(
        false,
        "please specify " + nonExistsOptions
          .map { case (field, _) => "[" + field + "]" }
          .mkString(", ") + " as non-empty string")
    }
  }

  def jdbcReader(sparkSession: SparkSession, driver: String): DataFrameReader = {

    val reader = sparkSession.read
      .format("jdbc")
      .option("url", config.getString("url"))
      .option("dbtable", config.getString("table"))
      .option("user", config.getString("user"))
      .option("password", config.getString("password"))
      .option("driver", driver)

    Try(TypesafeConfigUtils.extractSubConfigThrowable(config, "jdbc.", false)) match {

      case Success(options) => {
        val optionMap = options
          .entrySet()
          .foldRight(Map[String, String]())((entry, m) => {
            m + (entry.getKey -> entry.getValue.unwrapped().toString)
          })

        reader.options(optionMap)
      }
      case Failure(exception) => // do nothing
    }

    reader
  }
}
