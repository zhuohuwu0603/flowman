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

package com.dimajix.flowman.spec.flow

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.dimajix.flowman.execution.Session
import com.dimajix.flowman.spec.MappingOutputIdentifier
import com.dimajix.flowman.spec.Module
import com.dimajix.flowman.testing.LocalSparkSession


class AliasMappingTest extends FlatSpec with Matchers with LocalSparkSession {
    "An AliasMapping" should "be parseable" in {
        val spec =
            """
              |mappings:
              |  my_alias:
              |    kind: alias
              |    input: some_mapping
            """.stripMargin

        val project = Module.read.string(spec).toProject("project")
        val mapping = project.mappings("my_alias")

        mapping shouldBe an[AliasMappingSpec]
    }

    it should "support different outputs" in {
        val session = Session.builder().withSparkSession(spark).build()
        val executor = session.executor

        val mapping = AliasMapping(
            Mapping.Properties(session.context),
            MappingOutputIdentifier("input_df:output_2")
        )

        val inputDf = spark.emptyDataFrame
        mapping.input should be (MappingOutputIdentifier("input_df:output_2"))
        mapping.outputs should be (Seq("default"))

        val result = mapping.execute(executor, Map(MappingOutputIdentifier("input_df:output_2") -> inputDf))("default")
        result.count() should be (0)
    }
}
