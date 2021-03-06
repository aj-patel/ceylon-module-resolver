package com.redhat.ceylon.cmr.api;

public class ModuleQuery {
    private String name;
    private Type type;
    private Long start;
    private Long count;
    private long[] pagingInfo;

    public enum Type {
        SRC(ArtifactContext.SRC), 
        JVM(ArtifactContext.CAR, ArtifactContext.JAR), 
        JS(ArtifactContext.JS);
        
        private String[] suffixes;

        Type(String... suffixes){
            this.suffixes = suffixes;
        }

        public String[] getSuffixes() {
            return suffixes;
        }
    }
    
    public ModuleQuery(String name, Type type){
        this.name = name.toLowerCase();
        this.type = type;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.toLowerCase();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Long getStart() {
        return start;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public boolean isPaging() {
        return count != null || start != null;
    }

    public void setPagingInfo(long[] pagingInfo) {
        this.pagingInfo = pagingInfo;
    }

    public long[] getPagingInfo() {
        return pagingInfo;
    }

}
