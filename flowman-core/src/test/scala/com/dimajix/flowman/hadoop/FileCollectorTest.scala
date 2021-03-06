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

package com.dimajix.flowman.hadoop

import java.time.Month

import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.{FileSystem => HadoopFileSystem}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.dimajix.flowman.catalog.PartitionSpec
import com.dimajix.flowman.types.RangeValue
import com.dimajix.flowman.types.TimestampType
import com.dimajix.flowman.util.UtcTimestamp


class FileCollectorTest extends FlatSpec with Matchers with BeforeAndAfterAll {
    var hadoopConf:org.apache.hadoop.conf.Configuration = _
    var fileSystem: HadoopFileSystem = _
    var workingDirectory:Path = _

    override def beforeAll: Unit = {
        hadoopConf = new org.apache.hadoop.conf.Configuration()

        val tmpDirectory = java.nio.file.Files.createTempDirectory("test-FileCollectorTest").toString
        workingDirectory = new Path("file:///", tmpDirectory)
        fileSystem = workingDirectory.getFileSystem(hadoopConf)
        fileSystem.mkdirs(new Path(workingDirectory, "data/2016/01/03"))
        fileSystem.create(new Path(workingDirectory, "data/2016/01/03/01.seq")).close()
        fileSystem.create(new Path(workingDirectory, "data/2016/01/03/02.seq")).close()
        fileSystem.mkdirs(new Path(workingDirectory, "data/2016/01/04"))
        fileSystem.mkdirs(new Path(workingDirectory, "data/2016/01/05"))
        fileSystem.create(new Path(workingDirectory, "data/2016/01/05/01.seq")).close()
        fileSystem.create(new Path(workingDirectory, "data/2016/01/05/02.seq")).close()
        fileSystem.mkdirs(new Path(workingDirectory, "data/2016/02/01"))
        fileSystem.create(new Path(workingDirectory, "data/2016/02/01/01.seq")).close()
        fileSystem.create(new Path(workingDirectory, "data/2016/02/01/02.seq")).close()
        fileSystem.create(new Path(workingDirectory, "data/2016/02/01/03.seq")).close()
        fileSystem.create(new Path(workingDirectory, "data/2016/02/01/04.seq")).close()
        fileSystem.mkdirs(new Path(workingDirectory, "data/2016/02/02"))

        fileSystem.mkdirs(new Path(workingDirectory, "data/2017/06/19/"))
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497830400.i-02255f88.rtb-imp.log")).close()
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497831300.i-02255f88.rtb-imp.log")).close()
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497832200.i-02255f88.rtb-imp.log")).close()
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497833100.i-02255f88.rtb-imp.log")).close()
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497834000.i-02255f88.rtb-imp.log")).close()
        fileSystem.create(new Path(workingDirectory, "data/2017/06/19/1497852000.i-02255f88.rtb-imp.log")).close()
    }
    override def afterAll = {
        fileSystem.delete(workingDirectory, true)
    }

    "The file collector" should "not enumerate all files" in {
        val collector = FileCollector.builder(hadoopConf)
            .path(new Path(workingDirectory, "data/2016/02/01"))
            .build()
        val files = collector.collect()
        files.size should be (1)
        files should be (Seq(new Path(workingDirectory, "data/2016/02/01")))
    }

    it should "glob intermediate directories" in {
        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .path(new Path(workingDirectory, "data/2016/0*/0*"))
            .build()
        val files = collector.collect()
        files.size should be (5)
        files should be (Seq(
            new Path(workingDirectory, "data/2016/01/03"),
            new Path(workingDirectory, "data/2016/01/04"),
            new Path(workingDirectory, "data/2016/01/05"),
            new Path(workingDirectory, "data/2016/02/01"),
            new Path(workingDirectory, "data/2016/02/02")
        ))
    }

    it should "support default values" in {
        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .path(new Path(workingDirectory, "data"))
            .pattern("$year/$month/$day")
            .defaults(Map("year" -> "*", "month" -> "*", "day" -> "*"))
            .build()

        val files1 = collector.collect(Seq(PartitionSpec(Map("year" -> "2016", "month" -> "01", "day" -> "03"))))
        files1 should be (Seq(
            new Path(workingDirectory, "data/2016/01/03")
        ))

        val files2 = collector.collect(Seq(PartitionSpec(Map("year" -> "2016", "month" -> "01"))))
        files2 should be (Seq(
            new Path(workingDirectory, "data/2016/01/03"),
            new Path(workingDirectory, "data/2016/01/04"),
            new Path(workingDirectory, "data/2016/01/05")
        ))

        val files3 = collector.collect(Seq(PartitionSpec(Map("year" -> "2016", "day" -> "01"))))
        files3 should be (Seq(
            new Path(workingDirectory, "data/2016/02/01")
        ))

        val files4 = collector.collect(Seq(PartitionSpec(Map("year" -> "2016"))))
        files4 should be (Seq(
            new Path(workingDirectory, "data/2016/01/03"),
            new Path(workingDirectory, "data/2016/01/04"),
            new Path(workingDirectory, "data/2016/01/05"),
            new Path(workingDirectory, "data/2016/02/01"),
            new Path(workingDirectory, "data/2016/02/02")
        ))
    }

    it should "collect all directories in given daily range (1)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 3, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.FEBRUARY, 2, 0, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("P1D"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("data/$ts.format('yyyy/MM/dd')")
            .build()
        val files = collector.collect(partitions)

        files.size should be (4)
        files should be (Seq(
            new Path(workingDirectory, "data/2016/01/03"),
            new Path(workingDirectory, "data/2016/01/04"),
            new Path(workingDirectory, "data/2016/01/05"),
            new Path(workingDirectory, "data/2016/02/01")
        ))
    }

    it should "collect all files in given daily range (2)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 4, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 0, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("P1D"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("data/$ts.format('yyyy/MM/dd')")
            .build()
        val files = collector.collect(partitions)

        files.size should be (1)
        files should be (Seq(
            new Path(workingDirectory, "data/2016/01/04")
        ))
    }

    it should "collect all files in given daily range (3)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 4, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 1, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("P1D"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("data/$ts.format('yyyy/MM/dd')")
            .build()
        val files = collector.collect(partitions)

        files.size should be (1)
        files should be (Seq(
            new Path(workingDirectory, "data/2016/01/04")
        ))
    }

    it should "collect all files in given daily range (4)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 4, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 6, 0, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("P1D"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("data/$ts.format('yyyy/MM/dd')")
            .build()
        val files = collector.collect(partitions)

        files.size should be (2)
        files(0).toString should be(workingDirectory.toString + "/data/2016/01/04")
        files(1).toString should be(workingDirectory.toString + "/data/2016/01/05")
    }

    it should "collect all files in given daily range (5)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 4, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 6, 0, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("P1D"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""$ts.format("'data/'yyyy/MM/dd")""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (2)
        files(0).toString should be(workingDirectory.toString + "/data/2016/01/04")
        files(1).toString should be(workingDirectory.toString + "/data/2016/01/05")
    }

    it should "collect all files in given hourly range (1)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 1, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 2, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT1H"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd/HH'.seq'")""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (1)
        files(0).toString should be(workingDirectory.toString + "/data/2016/01/05/01.seq")
    }

    it should "collect all files in given hourly range (2)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 3, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 2, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT1H"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd/HH'.seq'")""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (3)
        files(0).toString should be(workingDirectory.toString + "/data/2016/01/03/01.seq")
        files(1).toString should be(workingDirectory.toString + "/data/2016/01/03/02.seq")
        files(2).toString should be(workingDirectory.toString + "/data/2016/01/05/01.seq")
    }

    it should "collect all files in given hourly range (3)" in {
        val firstDate = UtcTimestamp.of(2016, Month.JANUARY, 3, 0, 0)
        val lastDate = UtcTimestamp.of(2016, Month.JANUARY, 5, 3, 0)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT1H"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd/HH'.seq'")""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (4)
        files(0).toString should be(workingDirectory.toString + "/data/2016/01/03/01.seq")
        files(1).toString should be(workingDirectory.toString + "/data/2016/01/03/02.seq")
        files(2).toString should be(workingDirectory.toString + "/data/2016/01/05/01.seq")
        files(3).toString should be(workingDirectory.toString + "/data/2016/01/05/02.seq")
    }

    it should "collect unixtimestamps as well (1)" in {
        val firstDate = UtcTimestamp.of(2017, Month.JUNE, 19, 0, 0)
        val lastDate = UtcTimestamp.of(2017, Month.JUNE, 19, 23, 59)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT15M"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd")/${ts.toEpochSeconds()}.i-*.log""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (6)
        files(0).toString should be(workingDirectory.toString + "/data/2017/06/19/1497830400.i-02255f88.rtb-imp.log")
        files(1).toString should be(workingDirectory.toString + "/data/2017/06/19/1497831300.i-02255f88.rtb-imp.log")
        files(2).toString should be(workingDirectory.toString + "/data/2017/06/19/1497832200.i-02255f88.rtb-imp.log")
        files(3).toString should be(workingDirectory.toString + "/data/2017/06/19/1497833100.i-02255f88.rtb-imp.log")
        files(4).toString should be(workingDirectory.toString + "/data/2017/06/19/1497834000.i-02255f88.rtb-imp.log")
        files(5).toString should be(workingDirectory.toString + "/data/2017/06/19/1497852000.i-02255f88.rtb-imp.log")
    }

    it should "collect unixtimestamps as well (2)" in {
        val firstDate = UtcTimestamp.of(2017, Month.JUNE, 19, 0, 15)
        val lastDate = UtcTimestamp.of(2017, Month.JUNE, 19, 0, 45)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT15M"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd")/${ts.toEpochSeconds()}.i-*.log""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (2)
        files(0).toString should be(workingDirectory.toString + "/data/2017/06/19/1497831300.i-02255f88.rtb-imp.log")
        files(1).toString should be(workingDirectory.toString + "/data/2017/06/19/1497832200.i-02255f88.rtb-imp.log")
    }

    it should "collect unixtimestamps as well (3)" in {
        val firstDate = UtcTimestamp.of(2017, Month.JUNE, 19, 0, 15)
        val lastDate = UtcTimestamp.of(2017, Month.JUNE, 19, 0, 44)
        val range = RangeValue(firstDate.toString, lastDate.toString)
        val days = TimestampType.interpolate(range, Some("PT15M"))
        val partitions = days.map(p => PartitionSpec(Map("ts" -> p)))

        val collector = FileCollector.builder(hadoopConf)
            .path(workingDirectory)
            .pattern("""data/$ts.format("yyyy/MM/dd")/${ts.toEpochSeconds()}.i-*.log""")
            .build()
        val files = collector.collect(partitions)

        files.size should be (1)
        files(0).toString should be(workingDirectory.toString + "/data/2017/06/19/1497831300.i-02255f88.rtb-imp.log")
    }
}
