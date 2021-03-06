/*
 * Copyright 2011 Red Hat inc. and third party contributors as noted 
 * by the author tags.
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

package com.redhat.ceylon.cmr.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import com.redhat.ceylon.cmr.api.AbstractRepositoryManager;
import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.ModuleQuery;
import com.redhat.ceylon.cmr.api.ModuleSearchResult;
import com.redhat.ceylon.cmr.api.ModuleSearchResult.ModuleDetails;
import com.redhat.ceylon.cmr.api.ModuleVersionQuery;
import com.redhat.ceylon.cmr.api.ModuleVersionResult;
import com.redhat.ceylon.cmr.api.ModuleResult;
import com.redhat.ceylon.cmr.api.ArtifactResult;
import com.redhat.ceylon.cmr.api.Logger;
import com.redhat.ceylon.cmr.api.Repository;
import com.redhat.ceylon.cmr.api.RepositoryException;
import com.redhat.ceylon.cmr.spi.ContentOptions;
import com.redhat.ceylon.cmr.spi.ContentStore;
import com.redhat.ceylon.cmr.spi.Node;
import com.redhat.ceylon.cmr.spi.OpenNode;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class AbstractNodeRepositoryManager extends AbstractRepositoryManager {

    protected static final String SHA1 = ".sha1";
    protected static final String LOCAL = ".local";
    protected static final String CACHED = ".cached";

    protected Repository root; // main local root
    protected List<Repository> roots = new CopyOnWriteArrayList<Repository>(); // lookup roots - order matters!

    public AbstractNodeRepositoryManager(Logger log) {
        super(log);
    }

    protected OpenNode getRoot() {
        if (root == null)
            throw new IllegalArgumentException("Missing root!");

        return root.getRoot();
    }

    protected void setRoot(Repository root) {
        if (root == null)
            throw new IllegalArgumentException("Null root");
        if (this.root != null)
            throw new IllegalArgumentException("Root already set!");

        this.root = root;
        this.roots.add(root);
    }

    protected void prependRepository(Repository external) {
        roots.add(0, external);
    }

    protected void appendRepository(Repository external) {
        roots.add(external);
    }

    protected void removeRepository(Repository external) {
        roots.remove(external);
    }

    protected ArtifactResult toArtifactResult(Node node) {
        final Repository adapter = NodeUtils.getRepository(node);
        return adapter.getArtifactResult(this, node);
    }

    public List<String> getRepositoriesDisplayString() {
        final List<String> displayStrings = new ArrayList<String>();
        for (Repository root : roots) {
            displayStrings.add(root.getDisplayString());
        }
        return displayStrings;
    }

    public void putArtifact(ArtifactContext context, InputStream content) throws RepositoryException {
        final Node parent = getOrCreateParent(context);
        log.debug("Adding artifact " + context + " to repository " + root.getDisplayString());
        log.debug(" -> " + NodeUtils.getFullPath(parent));
        final String label = root.getArtifactName(context);
        try {
            if (parent instanceof OpenNode) {
                final OpenNode on = (OpenNode) parent;
                if (on.addContent(label, content, context) == null)
                    addContent(context, parent, label, content);
            } else {
                addContent(context, parent, label, content);
            }
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
        log.debug(" -> [done]");
    }

    @Override
    protected void putFolder(ArtifactContext context, File folder) throws RepositoryException {
        Node parent = getOrCreateParent(context);
        log.debug("Adding folder " + context + " to repository " + root.getDisplayString());
        log.debug(" -> " + NodeUtils.getFullPath(parent));

        // fast-path for Herd
        if(isHerd()){
            uploadToHerd(parent, context, folder);
            return;
        }
        
        final String label = root.getArtifactName(context);
        if (parent instanceof OpenNode) {
            final OpenNode on = (OpenNode) parent;
            final OpenNode curent = on.createNode(label);
            try {
                for (File f : folder.listFiles()) // ignore folder, it should match new root
                    putFiles(curent, f, context);
            } catch (Exception e) {
                removeArtifact(context);
                throw new RepositoryException(e);
            }
        } else {
            throw new RepositoryException("Cannot put folder [" + folder + "] to non-open node: " + context);
        }
        log.debug(" -> [done]");
    }

    private boolean isHerd() {
        ContentStore cs = root.getRoot().getService(ContentStore.class);
        return cs != null && cs.isHerd();
    }

    private void uploadToHerd(Node parent, ArtifactContext context, File folder) {
        log.debug("Uploading folder to Herd");
        try {
            File zip = IOUtils.zipFolder(folder);
            log.debug("Herd module-doc zip file is at "+zip.getAbsolutePath());
            try{
                context.setSuffix(ArtifactContext.DOCS_ZIPPED);
                final String label = root.getArtifactName(context);
                ((OpenNode)parent).addContent(label, new FileInputStream(zip), context);
            }finally{
                zip.delete();
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to upload module-doc zip to Herd repository", e);
        }
    }

    protected void putFiles(OpenNode current, File file, ContentOptions options) throws IOException {
        if (current == null)
            throw new IOException("Null current, could probably not create new node for file: " + file.getParent());

        if (file.isDirectory()) {
            current = current.createNode(file.getName());
            for (File f : file.listFiles())
                putFiles(current, f, options);
        } else {
            log.debug(" Adding file " + file.getPath() + " at " + NodeUtils.getFullPath(current));
            current.addContent(file.getName(), new FileInputStream(file), options);
            log.debug("  -> [done]");
        }
    }

    protected void addContent(ArtifactContext context, Node parent, String label, InputStream content) throws IOException {
        throw new IOException("Cannot add child [" + label + "] content [" + content + "] on parent node: " + parent);
    }

    public void removeArtifact(ArtifactContext context) throws RepositoryException {
        Node parent = getFromRootNode(context, false);
        log.debug("Remove artifact " + context + " to repository " + root.getDisplayString());
        if (parent != null) {
            final String label = root.getArtifactName(context);
            try {
                removeNode(parent, label);
            } catch (IOException e) {
                throw new RepositoryException(e);
            }
            log.debug(" -> [done]");
        } else {
            log.debug(" -> No such artifact: " + context);
        }
    }

    protected void removeNode(Node parent, String child) throws IOException {
        if (parent instanceof OpenNode) {
            final OpenNode on = (OpenNode) parent;
            on.removeNode(child);
        } else {
            throw new IOException("Parent node is not open: " + parent);
        }
    }

    protected Node getLeafNode(ArtifactContext context) {
        final Node node = getFromAllRoots(context, true);
        if (node == null) {
            if (context.isThrowErrorIfMissing())
                throw new IllegalArgumentException("No such artifact: " + context);
            return null;
        }

        // by default we don't check sha1 on remote nodes, it should be done after download
        if (context.isIgnoreSHA() == false && node.isRemote() == false && node.hasBinaries()) {
            Boolean result = null;
            Node parent = NodeUtils.firstParent(node);
            if (parent == null) {
                throw new IllegalArgumentException("Parent should not be null: " + node);
            }
            Node shaResult = (parent instanceof OpenNode) ? ((OpenNode) parent).peekChild(node.getLabel() + SHA1 + CACHED) : parent.getChild(node.getLabel() + SHA1 + CACHED);
            if (shaResult == null) {
                try {
                    result = checkSHA(node);
                    if (parent instanceof OpenNode) {
                        final OpenNode on = (OpenNode) parent;
                        on.addNode(node.getLabel() + SHA1 + CACHED, result);
                    }
                } catch (IOException e) {
                    log.warning("Error checking SHA1: " + e);
                }

            } else {
                result = shaResult.getValue(Boolean.class);
            }
            // check sha
            if (result != null && result == false)
                throw new InvalidArchiveException("Invalid SHA1 for artifact: " + context,
                        NodeUtils.getFullPath(node));
        }

        // save the context info
        context.toNode(node);

        return node;
    }

    protected Boolean checkSHA(Node artifact) throws IOException {
        final Node sha = artifact.getChild(SHA1);
        return (sha != null) ? checkSHA(artifact, sha.getInputStream()) : null;
    }

    protected boolean checkSHA(Node artifact, InputStream shaStream) throws IOException {
        final String shaFromSha = IOUtils.readSha1(shaStream);
        final String shaFromArtifact = IOUtils.sha1(artifact.getInputStream());
        return shaFromArtifact.equals(shaFromSha);
    }

    protected Node getOrCreateParent(ArtifactContext context) {
        Node parent = getFromRootNode(context, false);
        if (parent == null) {
            parent = root.createParent(context);
        }
        return parent;
    }

    protected Node getFromRootNode(ArtifactContext context, boolean addLeaf) {
        return fromAdapters(Collections.singleton(root), context, addLeaf);
    }

    protected Node getFromAllRoots(ArtifactContext context, boolean addLeaf) {
        LookupCaching.enable();
        try {
            return fromAdapters(roots, context, addLeaf);
        } finally {
            LookupCaching.disable();
        }
    }

    protected Node fromAdapters(Iterable<Repository> repositories, ArtifactContext context, boolean addLeaf) {
        log.debug("Looking for " + context);
        for (Repository repository : repositories) {
            log.debug(" Trying repository " + repository.getDisplayString());
            Node node = repository.findParent(context);
            if (node != null) {
                if (addLeaf) {
                    final Node parent = node;
                    context.toNode(parent);
                    try {
                        node = node.getChild(repository.getArtifactName(context));
                    } finally {
                        ArtifactContext.removeNode(parent);
                    }
                }

                if (node != null) {
                    NodeUtils.keepRepository(node, repository);
                    log.debug("  -> Found at " + NodeUtils.getFullPath(node));
                    return node;
                }
            }
            log.debug("  -> Not Found");
        }

        log.debug(" -> Artifact " + context + " not found in any repository");
        return null; // not found
    }
    
    @Override
    public ModuleResult completeModules(ModuleQuery lookup) {
        ModuleResult result = new ModuleResult();
        for(Repository root : roots){
            root.completeModules(lookup, result);
        }
        return result;
    }
    
    @Override
    public ModuleVersionResult completeVersions(ModuleVersionQuery lookup) {
        ModuleVersionResult result = new ModuleVersionResult(lookup.getName());
        for(Repository root : roots){
            root.completeVersions(lookup, result);
        }
        return result;
    }

    @Override
    public ModuleSearchResult searchModules(ModuleQuery query) {
        if(!query.isPaging()){
            // that's pretty simple
            ModuleSearchResult result = new ModuleSearchResult();
            for(Repository root : roots){
                root.searchModules(query, result);
            }
            return result;
        }else{
            // we need to merge manually
            ModuleSearchResult[] results = new ModuleSearchResult[roots.size()];
            // keep an overall module name ordering
            SortedSet<String> names = new TreeSet<String>();
            int i=0;
            long[] pagingInfo = query.getPagingInfo();
            if(pagingInfo != null){
                // check its length
                if(pagingInfo.length != roots.size())
                    throw new IllegalArgumentException("Paging info is not the same size as roots, it must have come from a different RepositoryManager");
            }
            for(Repository root : roots){
                ModuleSearchResult result = new ModuleSearchResult();
                // adapt the start index if required
                if(pagingInfo != null)
                    query.setStart(pagingInfo[i]);
                root.searchModules(query, result);
                results[i++] = result;
                names.addAll(result.getModuleNames());
            }
            // now merge results
            ModuleSearchResult result = new ModuleSearchResult();
            long[] resultPagingInfo = new long[roots.size()];
            // initialise it if we need to
            if(pagingInfo != null)
                System.arraycopy(pagingInfo, 0, resultPagingInfo, 0, resultPagingInfo.length);
            
            result.setNextPagingInfo(resultPagingInfo);
            i = 0;
            for(String module : names){
                // stop if we exceeded the count
                if(query.getCount() != null && i++ == query.getCount())
                    break;
                // collect every module result for that name from the results
                int repo = 0;
                for(ModuleSearchResult resultPart : results){
                    ModuleDetails details = resultPart.getResult(module);
                    // did we find anything in that repo?
                    if(details == null){
                        repo++;
                        continue;
                    }else{
                        // count one result for this repo
                        resultPagingInfo[repo++]++;
                    }
                    // merge it
                    result.addResult(module, details.getDoc(), details.getLicense(), details.getAuthors(), details.getVersions());
                }
            }
            // all done
            return result;
        }
    }    
}
