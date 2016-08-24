/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.gradle.python.plugin


import com.linkedin.gradle.python.plugin.internal.AbstractBaseRuleSourcePluginTest
import com.linkedin.gradle.python.spec.binary.WheelBinarySpec
import com.linkedin.gradle.python.spec.component.internal.PythonComponentSpecInternal

class PythonWheelPluginTest extends AbstractBaseRuleSourcePluginTest {

  def 'apply multiple platforms'() {
    when:
    dsl {
      pluginManager.apply PythonWheelPlugin
      model {
        components {
          python(com.linkedin.gradle.python.spec.component.PythonComponentSpec) {
            targetPlatform 'python2.7'
            targetPlatform 'python2.6'
          }
        }
      }
    }

    then:
    def components = realizeComponents()
    def binaries = realizeBinaries()
    def python = components.python as PythonComponentSpecInternal

    and:
    python.getPythonEnvironments().size() == 2

    and:
    binaries.withType(WheelBinarySpec).size() == 2
    for(WheelBinarySpec spec : binaries.withType(WheelBinarySpec)) {
      assert spec != null
      assert (spec.tasks.toList().collect { it.name } as Set).containsAll("createPythonWheel${spec.getPythonEnvironment().getVersion().getVersionString()}" as String)
    }

    and:
    binaries.size() == 2
  }
}