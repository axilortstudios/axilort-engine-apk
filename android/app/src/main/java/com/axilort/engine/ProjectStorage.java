package com.axilort.engine;

import java.io.File;

/** Marker/helper for the native project storage location. */
public final class ProjectStorage {
    private ProjectStorage() {}
    public static File projectFolder(File dataRoot, String name) {
        File projects = new File(dataRoot, "projects");
        File folder = new File(projects, name);
        folder.mkdirs();
        return folder;
    }
}
