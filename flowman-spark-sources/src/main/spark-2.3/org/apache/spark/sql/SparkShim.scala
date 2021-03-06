/*
 * Copyright 2018-2019 Kaya Kupferschmidt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql

import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.sql.execution.datasources.DataSource
import org.apache.spark.sql.execution.datasources.FileFormat
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.sources.RelationProvider
import org.apache.spark.sql.sources.SchemaRelationProvider
import org.apache.spark.unsafe.types.CalendarInterval


object SparkShim {
    def getHadoopConf(sparkConf:SparkConf) :org.apache.hadoop.conf.Configuration = SparkHadoopUtil.get.newConfiguration(sparkConf)

    def parseCalendarInterval(str:String) : CalendarInterval = CalendarInterval.fromString(str)

    def isStaticConf(key:String) : Boolean = {
        SQLConf.staticConfKeys.contains(key)
    }

    def relationSupportsMultiplePaths(spark:SparkSession, format:String) : Boolean = {
        val providingClass = DataSource.lookupDataSource(format, spark.sessionState.conf)
        relationSupportsMultiplePaths(providingClass)
    }

    def relationSupportsMultiplePaths(providingClass:Class[_]) : Boolean = {
        providingClass.newInstance() match {
            case _: RelationProvider => false
            case _: SchemaRelationProvider => false
            case _: FileFormat => true
            case _ => false
        }
    }
}
