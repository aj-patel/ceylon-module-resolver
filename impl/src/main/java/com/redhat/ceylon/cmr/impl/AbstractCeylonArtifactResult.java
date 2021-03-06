/*
 * Copyright 2011 Red Hat inc. and third party contributors as noted 
 * by the author tags.
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

package com.redhat.ceylon.cmr.impl;

import com.redhat.ceylon.cmr.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract, use Jandex to read off Module info.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractCeylonArtifactResult extends AbstractArtifactResult {

    private RepositoryManager manager;

    protected AbstractCeylonArtifactResult(RepositoryManager manager, String name, String version) {
        super(name, version);
        this.manager = manager;
    }

    public ArtifactResultType type() {
        return ArtifactResultType.CEYLON;
    }

    public List<ArtifactResult> dependencies() throws RepositoryException {
        List<ModuleInfo> infos = BytecodeUtils.readModuleInformation(name(), artifact());
        if (infos.isEmpty())
            return Collections.emptyList();

        final List<ArtifactResult> results = new ArrayList<ArtifactResult>();
        for (ModuleInfo mi : infos) {
            results.add(new LazyArtifactResult(
                    mi.getName(),
                    mi.getVersion(),
                    mi.isOptional() ? ImportType.OPTIONAL : (mi.isExport() ? ImportType.EXPORT : ImportType.UNDEFINED)));
        }
        return results;
    }

    private class LazyArtifactResult extends AbstractArtifactResult {
        private ArtifactResult delegate;
        private final ImportType importType;

        private LazyArtifactResult(String name, String version, ImportType importType) {
            super(name, version);
            this.importType = importType;
        }

        private synchronized ArtifactResult getDelegate() {
            if (delegate == null) {
                final ArtifactContext context = new ArtifactContext(name(), version());
                context.setThrowErrorIfMissing(importType() != ImportType.OPTIONAL);
                delegate = manager.getArtifactResult(context);
            }
            return delegate;
        }

        @Override
        public ImportType importType() {
            return importType;
        }

        public ArtifactResultType type() {
            return getDelegate().type();
        }

        public File artifact() throws RepositoryException {
            return getDelegate().artifact();
        }

        public List<ArtifactResult> dependencies() throws RepositoryException {
            return getDelegate().dependencies();
        }
    }
}

