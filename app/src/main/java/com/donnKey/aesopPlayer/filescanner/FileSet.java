package com.donnKey.aesopPlayer.filescanner;

import com.google.common.base.Preconditions;

import java.io.File;

public class FileSet {

    public final String id;
    public final String directoryName;
    public final File path;
    public final File[] files;
    public final boolean isDemoSample;
    public final boolean isReference; // Never "Completed", thus never default delete in Provisioning

    public FileSet(String id, File absolutePath, File[] files, boolean isDemoSample, boolean isReference) {
        Preconditions.checkArgument(absolutePath.isDirectory());
        this.id = id;
        this.path = absolutePath;
        this.directoryName = absolutePath.getName();
        this.files = files;
        this.isDemoSample = isDemoSample;
        this.isReference = isReference;
    }

    /**
     * Compares only the id field.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileSet fileSet = (FileSet) o;

        return id.equals(fileSet.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
