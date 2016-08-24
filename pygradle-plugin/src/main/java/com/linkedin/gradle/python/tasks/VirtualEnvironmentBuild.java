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

package com.linkedin.gradle.python.tasks;

import com.linkedin.gradle.python.internal.toolchain.PythonExecutable;
import com.linkedin.gradle.python.tasks.utilities.DefaultOutputStreamProcessor;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.util.GFileUtils;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;


public class VirtualEnvironmentBuild extends BasePythonTask {

    private Configuration virtualEnvFiles;
    private String activateScriptName;
    private String virtualEnvName;

    @Inject
    protected FileOperations getFileOperations() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @OutputFile
    @SuppressWarnings("unused")
    public File getLocalPythonExecutable() {
        return new File(getVenvDir(), "bin/python");
    }

    @OutputFile
    public File getActivateScript() {
        return new File(getProject().getProjectDir(), activateScriptName);
    }

    @TaskAction
    public void doWork() {
        if (null == virtualEnvFiles) {
            throw new GradleException("Virtual Env must be defined");
        }

        final File vendorDir = getPythonEnvironment().getVendorDir();
        final String virtualEnvDependencyVersion = findVirtualEnvDependencyVersion();

        for (final File file : getVirtualEnvFiles()) {
            getFileOperations().copy(new Action<CopySpec>() {
                @Override
                public void execute(CopySpec copySpec) {
                    if (file.getName().endsWith(".whl")) {
                        copySpec.from(getFileOperations().zipTree(file));
                        copySpec.into(new File(vendorDir, "virtualenv-" + virtualEnvDependencyVersion));
                    } else {
                        copySpec.from(getFileOperations().tarTree(file));
                        copySpec.into(vendorDir);
                    }
                }
            });
        }

        final String path = String.format("%s/virtualenv-%s/virtualenv.py", vendorDir.getAbsolutePath(), virtualEnvDependencyVersion);
        final PythonExecutable pythonExecutable = getPythonEnvironment().getSystemPythonExecutable();
        final DefaultOutputStreamProcessor streamProcessor = new DefaultOutputStreamProcessor();

        ExecResult result = pythonExecutable.execute(new Action<ExecAction>() {
            @Override
            public void execute(ExecAction execAction) {
                execAction.args(path,
                        "--python", pythonExecutable.getPythonPath().getAbsolutePath(),
                        "--prompt", virtualEnvName,
                        getVenvDir().getAbsolutePath());
                execAction.setErrorOutput(streamProcessor);
                execAction.setStandardOutput(streamProcessor);
            }
        });

        if (result.getExitValue() != 0) {
            getLogger().lifecycle(streamProcessor.getWholeText());
        }
        result.assertNormalExitValue();

        File source = new File(getVenvDir(), "bin/activate");
        GFileUtils.copyFile(source, getActivateScript());

        getActivateScript().setExecutable(true);
    }

    private String findVirtualEnvDependencyVersion() {
        ResolvedConfiguration resolvedConfiguration = getVirtualEnvFiles().getResolvedConfiguration();
        Set<ResolvedDependency> virtualEnvDependencies = resolvedConfiguration.getFirstLevelModuleDependencies(new VirtualEvnSpec());
        if (virtualEnvDependencies.isEmpty()) {
            throw new GradleException("Unable to find virtualenv dependency");
        }

        VersionNumber highest = new VersionNumber(0, 0, 0, null);
        for (ResolvedDependency resolvedDependency : virtualEnvDependencies) {
            VersionNumber test = VersionNumber.parse(resolvedDependency.getModuleVersion());
            if (test.compareTo(highest) > 0) {
                highest = test;
            }
        }

        return highest.toString();
    }

    @InputFiles
    Configuration getVirtualEnvFiles() {
        return virtualEnvFiles;
    }

    public void setVirtualEnvFiles(Configuration configuration) {
        this.virtualEnvFiles = configuration;
    }

    public void setActivateScriptName(String activateScriptName) {
        this.activateScriptName = activateScriptName;
    }

    @Input
    @SuppressWarnings("unused")
    public String getVirtualEnvName() {
        return virtualEnvName;
    }

    public void setVirtualEnvName(String virtualEnvName) {
        this.virtualEnvName = virtualEnvName;
    }

    private class VirtualEvnSpec implements Spec<Dependency> {

        @Override
        public boolean isSatisfiedBy(Dependency element) {
            return "virtualenv".equals(element.getName());
        }
    }
}