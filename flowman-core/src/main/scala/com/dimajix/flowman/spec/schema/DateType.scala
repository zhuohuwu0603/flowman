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

package com.dimajix.flowman.spec.schema

import java.sql.Date
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import org.apache.spark.sql.types.DataType


case object DateType extends FieldType {
    override def sparkType : DataType = org.apache.spark.sql.types.DateType

    override def parse(value:String) : Any = Date.valueOf(value)
    override def interpolate(value: FieldValue, granularity:String) : Iterable[Any] = {
        value match {
            case SingleValue(v) => Seq(Date.valueOf(v))
            case ArrayValue(values) => values.map(Date.valueOf)
            case RangeValue(start,end) => {
                val startDate = Date.valueOf(start).toLocalDate.toEpochDay
                val endDate = Date.valueOf(end).toLocalDate.toEpochDay
                val step = if (granularity != null && granularity.nonEmpty)
                    Duration.parse(granularity).get(ChronoUnit.SECONDS)/(24*60*60)
                else
                    1
                startDate until endDate by step map(x => Date.valueOf(LocalDate.ofEpochDay(x)))
            }
        }
    }
}