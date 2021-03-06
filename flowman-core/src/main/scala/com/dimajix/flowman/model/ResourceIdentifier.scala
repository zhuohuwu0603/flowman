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

package com.dimajix.flowman.model

import java.io.File
import java.net.URL
import java.util.regex.Pattern

import scala.annotation.tailrec

import org.apache.hadoop.fs.Path

import com.dimajix.flowman.hadoop.GlobPattern


object ResourceIdentifier {
    def ofFile(file:Path) = GlobbingResourceIdentifier("file", file.toString)
    def ofLocal(file:Path) = GlobbingResourceIdentifier("local", file.toString)
    def ofLocal(file:File) = GlobbingResourceIdentifier("local", file.toURI.toString)
    def ofHiveDatabase(database:String) = RegexResourceIdentifier("hiveDatabase", database)
    def ofHiveTable(table:String) = RegexResourceIdentifier("hiveTable", table)
    def ofHiveTable(table:String, database:Option[String]) = RegexResourceIdentifier("hiveTable", fqTable(table, database))
    def ofHivePartition(table:String, database:Option[String], partition:Map[String,Any]) = RegexResourceIdentifier("hiveTablePartition", fqTable(table, database), partition.map { case(k,v) => k -> v.toString })
    def ofJdbcDatabase(database:String) = RegexResourceIdentifier("jdbcDatabase", database)
    def ofJdbcTable(table:String, database:Option[String]) = RegexResourceIdentifier("jdbcTable", fqTable(table, database))
    def ofJdbcTablePartition(table:String, database:Option[String], partition:Map[String,Any]) = RegexResourceIdentifier("jdbcTable", fqTable(table, database), partition.map { case(k,v) => k -> v.toString })
    def ofURL(url:URL) = RegexResourceIdentifier("url", url.toString)

    private def fqTable(table:String, database:Option[String]) : String = database.map(_ + ".").getOrElse("") + table
}


/**
 * A ResourceIdentifier is used to identify a physical resource which is either produced or consumed by a
 * target during a lifecycle phase. ResourceIdentifiers therefore play a crucial role in determining the correct
 * execution order of all targets
 */
abstract class ResourceIdentifier {
    val category:String
    val name:String
    val partition:Map[String,String]

    def isEmpty : Boolean = name.isEmpty
    def nonEmpty : Boolean = name.nonEmpty

    /**
      * Create new ResourceIdentifiers by exploding the powerset of all partitions
      * @return
      */
    def explodePartitions() : Seq[ResourceIdentifier] = {
        @tailrec
        def pwr(t: Set[String], ps: Set[Set[String]]): Set[Set[String]] =
            if (t.isEmpty) ps
            else pwr(t.tail, ps ++ (ps map (_ + t.head)))

        val ps = pwr(partition.keySet, Set(Set.empty[String])) //Powerset of ∅ is {∅}
        ps.toSeq.map(keys => withPartition(partition.filterKeys(keys.contains)))
    }

    /**
     * Makes a copy of this resource with a different partition
     * @param partition
     * @return
     */
    def withPartition(partition:Map[String,String]) : ResourceIdentifier

    /**
      * Returns true if this ResourceIdentifier is either equal to the other one or if it describes a resource which
      * actually contains the other one.
      * @param other
      * @return
      */
    def contains(other:ResourceIdentifier) : Boolean = {
        category == other.category &&
            containsName(other) &&
            containsPartition(other)
    }

    protected def containsName(other:ResourceIdentifier) : Boolean = {
        name == other.name
    }

    /**
      * Check that the current partition also holds the partition of the other resource. This is the case if all
      * partition values are also set in the other resource
      * @param other
      * @return
      */
    protected def containsPartition(other:ResourceIdentifier) : Boolean = {
        partition.forall(p => other.partition.get(p._1).contains(p._2))
    }
}


/**
 * This is the simplest ResourceIdentifier, which simply performs exact matches of the resource name
 * @param category
 * @param name
 * @param partition
 */
final case class SimpleResourceIdentifier(override val category:String, override val name:String, override val partition:Map[String,String] = Map())
extends ResourceIdentifier
{
    override def withPartition(partition:Map[String,String]) : ResourceIdentifier = copy(partition=partition)
}


/**
 * This ResourceIdentifier performs matches using globbing logic in order to detect if another resource is contained
 * within this resource.  Globbing only makes sense for file based resources, for other types ypu should either use the
 * SimpleResourceIdentifier or the RegexResourceIdentifier
 * @param category
 * @param name
 * @param partition
 */
final case class GlobbingResourceIdentifier(override val category:String, override val name:String, override val partition:Map[String,String] = Map())
extends ResourceIdentifier
{
    private lazy val globPattern = GlobPattern(name)

    override def withPartition(partition:Map[String,String]) : ResourceIdentifier = copy(partition=partition)

    override protected def containsName(other:ResourceIdentifier) : Boolean = {
        // Test simple case: Perfect match
        if (name == other.name) {
            true
        }
        // Test if wildcards do match
        else if (globPattern.hasWildcard) {
            globPattern.matches(other.name)
        }
        else {
            false
        }
    }
}


/**
 * The RegexResourceIdentifier performs matches against other resources using a regular expression. This can be useful
 * for table names or similar resources.
 * @param category
 * @param name
 * @param partition
 */
final case class RegexResourceIdentifier(override val category:String, override val name:String, override val partition:Map[String,String] = Map())
extends ResourceIdentifier
{
    private lazy val regex = Pattern.compile(name, Pattern.DOTALL)

    override def withPartition(partition:Map[String,String]) : ResourceIdentifier = copy(partition=partition)

    override protected def containsName(other:ResourceIdentifier) : Boolean = {
        // Test simple case: Perfect match
        if (name == other.name) {
            true
        }
        // Test if wildcards do match
        else {
            regex.matcher(other.name).matches
        }
    }
}
