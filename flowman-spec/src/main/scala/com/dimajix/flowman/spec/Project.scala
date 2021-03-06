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

package com.dimajix.flowman.spec

import com.fasterxml.jackson.annotation.JsonProperty

import com.dimajix.flowman.model.Project



class ProjectSpec {
    @JsonProperty(value="name", required = true) private var name: String = ""
    @JsonProperty(value="description", required = false) private var description: Option[String] = None
    @JsonProperty(value="version", required = false) private var version: Option[String] = None
    @JsonProperty(value="modules", required = true) private[spec] var modules: Seq[String] = Seq()

    def instantiate(): Project = {
        Project(
            name=name,
            description=description,
            version=version,
            modules=modules
        )
    }
}

