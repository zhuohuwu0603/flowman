package com.dimajix.dataflow.spi

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.matchprocessor.ClassAnnotationMatchProcessor

import com.dimajix.dataflow.spec.flow.Mapping
import com.dimajix.dataflow.spec.model.Relation


object Scanner {
    private val IGNORED_PACKAGES = Array(
        "java",
        "javax",
        "scala",
        "org.scala",
        "org.scalatest",
        "org.apache",
        "org.joda",
        "org.slf4j",
        "org.yaml",
        "org.xerial",
        "org.json4s",
        "org.mortbay",
        "org.codehaus",
        "org.glassfish",
        "org.jboss",
        "com.amazonaws",
        "com.codahale",
        "com.sun",
        "com.google",
        "com.twitter",
        "com.databricks",
        "com.fasterxml"
    )
    private var _mappings : Seq[(String,Class[_ <: Mapping])] = _
    private var _relations : Seq[(String,Class[_ <: Relation])] = _

    private def loadSubtypes: Unit = {
        synchronized {
            if (_relations == null) {
                val mappings = MappingProvider.providers.map(p => (p.getName, p.getImpl)).toBuffer
                val relations = RelationProvider.providers.map(p => (p.getName, p.getImpl)).toBuffer

                new FastClasspathScanner(IGNORED_PACKAGES.map("-" + _):_*)
                    .matchClassesWithAnnotation(classOf[com.dimajix.dataflow.annotation.Mapping],
                        new ClassAnnotationMatchProcessor {
                            override def processMatch(aClass: Class[_]): Unit = {
                                val annotation = aClass.getAnnotation(classOf[com.dimajix.dataflow.annotation.Mapping])
                                mappings.append((annotation.typeName(), aClass))
                            }
                        }
                    )
                    .matchClassesWithAnnotation(classOf[com.dimajix.dataflow.annotation.Relation],
                        new ClassAnnotationMatchProcessor {
                            override def processMatch(aClass: Class[_]): Unit = {
                                val annotation = aClass.getAnnotation(classOf[com.dimajix.dataflow.annotation.Relation])
                                relations.append((annotation.typeName(), aClass))
                            }
                        }
                    )
                    .scan()
                _mappings = mappings.map(kv => (kv._1, kv._2.asInstanceOf[Class[_ <: Mapping]]))
                _relations = relations.map(kv => (kv._1, kv._2.asInstanceOf[Class[_ <: Relation]]))
            }
        }
    }


    def mappings : Seq[(String,Class[_ <: Mapping])] = {
        loadSubtypes
        _mappings
    }
    def relations: Seq[(String,Class[_ <: Relation])] = {
        loadSubtypes
        _relations
    }

}
