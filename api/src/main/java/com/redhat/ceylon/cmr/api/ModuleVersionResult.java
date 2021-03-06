package com.redhat.ceylon.cmr.api;

import java.util.SortedMap;
import java.util.TreeMap;

public class ModuleVersionResult {
    private String name;
    private SortedMap<String, ModuleVersionDetails> versions = new TreeMap<String, ModuleVersionDetails>();

    public ModuleVersionResult(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ModuleVersionDetails addVersion(String version) {
        if(versions.containsKey(version))
            return null;
        ModuleVersionDetails newVersion = new ModuleVersionDetails(version);
        versions.put(version, newVersion);
        return newVersion;
    }

    public SortedMap<String, ModuleVersionDetails> getVersions() {
        return versions;
    }

    public boolean hasVersion(String version) {
        return versions.containsKey(version);
    }
}
