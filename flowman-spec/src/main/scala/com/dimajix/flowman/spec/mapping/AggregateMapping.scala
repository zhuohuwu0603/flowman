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

package com.dimajix.flowman.spec.mapping

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.functions.expr

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.execution.Executor
import com.dimajix.flowman.model.BaseMapping
import com.dimajix.flowman.model.Mapping
import com.dimajix.flowman.model.MappingOutputIdentifier


case class AggregateMapping(
    instanceProperties : Mapping.Properties,
    input : MappingOutputIdentifier,
    dimensions : Seq[String],
    aggregations : Map[String,String],
    filter:Option[String] = None,
    partitions : Int = 0
) extends BaseMapping {
    /**
      * Creates an instance of the aggregated table.
      *
      * @param executor
      * @param tables
      * @return
      */
    override def execute(executor:Executor, tables:Map[MappingOutputIdentifier,DataFrame]): Map[String,DataFrame] = {
        val df = tables(input)
        val dims = dimensions.map(col)
        val expressions = aggregations.map(kv => expr(kv._2).as(kv._1))

        val parts = partitions
        val aggs = if (parts > 0)
            df.repartition(parts, dims:_*).groupBy(dims:_*).agg(expressions.head, expressions.tail.toSeq:_*)
        else
            df.groupBy(dims:_*).agg(expressions.head, expressions.tail.toSeq:_*)

        // Apply optional 'HAVING' filter
        val result = filter.map(f => aggs.where(f)).getOrElse(aggs)

        Map("main" -> result)
    }

    /**
      * Returns the dependencies of this mapping, which is exactly one input table
      *
      * @return
      */
    override def inputs : Seq[MappingOutputIdentifier] = {
        Seq(input)
    }
}


class AggregateMappingSpec extends MappingSpec {
    @JsonProperty(value = "input", required = true) private[spec] var input: String = _
    @JsonProperty(value = "dimensions", required = true) private[spec] var dimensions: Array[String] = _
    @JsonProperty(value = "aggregations", required = true) private[spec] var aggregations: Map[String, String] = _
    @JsonProperty(value = "filter", required = false) private var filter: Option[String] = None
    @JsonProperty(value = "partitions", required = false) private[spec] var partitions: String = _

    /**
      * Creates the instance of the specified Mapping with all variable interpolation being performed
      * @param context
      * @return
      */
    override def instantiate(context:Context) : AggregateMapping = {
        AggregateMapping(
            instanceProperties(context),
            MappingOutputIdentifier.parse(context.evaluate(input)),
            dimensions.map(context.evaluate),
            context.evaluate(aggregations),
            context.evaluate(filter),
            if (partitions == null || partitions.isEmpty) 0 else context.evaluate(partitions).toInt
        )
    }
}
