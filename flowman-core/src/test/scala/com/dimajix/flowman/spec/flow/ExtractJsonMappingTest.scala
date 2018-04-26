/*
 * Copyright 2018 Kaya Kupferschmidt
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

package com.dimajix.flowman.spec.flow

import org.apache.spark.sql.types.ArrayType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.dimajix.flowman.LocalSparkSession
import com.dimajix.flowman.execution.Session
import com.dimajix.flowman.spec.Module
import com.dimajix.flowman.spec.TableIdentifier


class ExtractJsonMappingTest extends FlatSpec with Matchers with LocalSparkSession {
    "The ExtractJsonMapping" should "be parseable" in {
        val spec =
            """
              |mappings:
              |  m0:
              |    type: json-extract
              |    input: p0
              |    column: _1
              |    schema:
              |      - name: s
              |        type: String
              |      - name: i
              |        type: Integer
              |      - name: st
              |        type:
              |          type: struct
              |          fields:
              |           - name: lolo
              |             type: string
              |           - name: i
              |             type: Integer
              |      - name: a
              |        type:
              |          type: array
              |          elementType: Double
            """.stripMargin

        val project = Module.read.string(spec).toProject("project")
        val session = Session.builder().withSparkSession(spark).build()
        val executor = session.createExecutor(project)
        implicit val context = executor.context

        project.mappings.size should be (1)
        project.mappings.contains("m0") should be (true)

        val input = executor.spark.createDataFrame(Seq(
            ("""{"i":12,"s":"lala"}""", 12),
            ("""{"st":{"lolo":"x"},"a":[0.1,0.7]}""", 23)
        ))

        val mapping = project.mappings("m0")
        val result = mapping.execute(executor, Map(TableIdentifier("p0") -> input))
        result.count() should be (2)
        result.schema should be (StructType(
            StructField("s", StringType, true) ::
            StructField("i", IntegerType, true) ::
            StructField("st", StructType(
                StructField("lolo", StringType, true) ::
                StructField("i", IntegerType, true) ::
                Nil
            )) ::
            StructField("a", ArrayType(DoubleType), true) ::
            Nil
        ))
    }
}