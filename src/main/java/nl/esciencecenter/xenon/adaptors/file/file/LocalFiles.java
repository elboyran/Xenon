/**
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.xenon.adaptors.file.file;

import static nl.esciencecenter.xenon.adaptors.file.file.LocalProperties.ADAPTOR_DESCRIPTION;
import static nl.esciencecenter.xenon.adaptors.file.file.LocalProperties.ADAPTOR_LOCATIONS;
import static nl.esciencecenter.xenon.adaptors.file.file.LocalProperties.ADAPTOR_NAME;
import static nl.esciencecenter.xenon.adaptors.file.file.LocalProperties.ADAPTOR_SCHEME;
import static nl.esciencecenter.xenon.adaptors.file.file.LocalProperties.VALID_PROPERTIES;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.esciencecenter.xenon.InvalidLocationException;
import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.credentials.Credential;
import nl.esciencecenter.xenon.engine.XenonProperties;
import nl.esciencecenter.xenon.engine.files.FileAdaptor;
import nl.esciencecenter.xenon.engine.files.FileSystemImplementation;
import nl.esciencecenter.xenon.engine.files.FilesEngine;
import nl.esciencecenter.xenon.engine.files.PathImplementation;
import nl.esciencecenter.xenon.engine.util.OpenOptions;
import nl.esciencecenter.xenon.files.DirectoryStream;
import nl.esciencecenter.xenon.files.FileAttributes;
import nl.esciencecenter.xenon.files.FileSystem;
import nl.esciencecenter.xenon.files.Files;
import nl.esciencecenter.xenon.files.InvalidOptionsException;
import nl.esciencecenter.xenon.files.InvalidPathException;
import nl.esciencecenter.xenon.files.NoSuchPathException;
import nl.esciencecenter.xenon.files.OpenOption;
import nl.esciencecenter.xenon.files.Path;
import nl.esciencecenter.xenon.files.PathAlreadyExistsException;
import nl.esciencecenter.xenon.files.PathAttributesPair;
import nl.esciencecenter.xenon.files.PosixFilePermission;
import nl.esciencecenter.xenon.files.RelativePath;
import nl.esciencecenter.xenon.util.Utils;

/**
 * LocalFiles implements an Xenon <code>Files</code> adaptor for local file operations.
 * 
 * @see Files
 * 
 * @version 1.0
 * @since 1.0
 */
public class LocalFiles extends FileAdaptor {


    /** The next ID for a FileSystem */
    private static int fsID = 0;

    private static synchronized int getNextFsID() {
        return fsID++;
    }

    public LocalFiles(FilesEngine filesEngine, Map<String, String> properties) throws XenonException {
        super(filesEngine, ADAPTOR_NAME, ADAPTOR_DESCRIPTION, ADAPTOR_SCHEME, ADAPTOR_LOCATIONS, VALID_PROPERTIES,
            new XenonProperties(VALID_PROPERTIES, properties));
    }
    
    /**
     * Check if a parent directory exists and throw an exception if this is not the case.  
     *  
     * @param path the path of which the parent must be checked. 
     *
     * @throws XenonException
     *          If the parent does not exist. 
     *  
     */
    private void checkParent(Path path) throws XenonException {
        RelativePath parentName = path.getRelativePath().getParent();
        
        if (parentName == null) { 
            throw new InvalidPathException(ADAPTOR_NAME, "Parent directory does not exist!");
        }
        
        Path parent = newPath(path.getFileSystem(), parentName);
            
        if (!exists(parent)) {
            throw new XenonException(ADAPTOR_NAME, "Parent directory " + parent + " does not exist!");
        }
    }
    
    /** 
     * Check if a location string is valid for the local filesystem. 
     * 
     * The location should -only- contain a file system root, such as "/" or "C:". 
     * 
     * @param location
     *          the location to check.
     * @throws InvalidLocationException
     *          if the location is invalid.                   
     */
    public static void checkFileLocation(String location) throws InvalidLocationException {
        if (location == null || location.isEmpty() || Utils.isLocalRoot(location)) {
            return;
        }

        throw new InvalidLocationException(ADAPTOR_NAME, "Location must only contain a file system root! (not " + location + ")");
    }
       
    /**
     * Move or rename an existing source path to a non-existing target path.
     * 
     * The parent of the target path (e.g. <code>target.getParent</code>) must exist.
     * 
     * If the source is a link, the link itself will be moved, not the path to which it refers. If the source is a directory, it
     * will be renamed to the target. This implies that a moving a directory between physical locations may fail.
     * 
     * @param source
     *            the existing source path.
     * @param target
     *            the non existing target path.
     *
     * @throws NoSuchPathException
     *             If the source file does not exist or the target parent directory does not exist.
     * @throws PathAlreadyExistsException
     *             If the target file already exists.
     * @throws XenonException
     *             If the move failed.
     */
    @Override
    public void move(Path source, Path target) throws XenonException {
        if (!exists(source)) {
            throw new NoSuchPathException(ADAPTOR_NAME, "Source " + source + " does not exist!");
        }

        RelativePath sourceName = source.getRelativePath().normalize();
        RelativePath targetName = target.getRelativePath().normalize();
        
        if (sourceName.equals(targetName)) {
            return;
        }

        if (exists(target)) {
            throw new PathAlreadyExistsException(ADAPTOR_NAME, "Target " + target + " already exists!");
        }

        checkParent(target);

        LocalUtils.move(source, target);
    }

    @Override
    public Path readSymbolicLink(Path link) throws XenonException {
        try {
            java.nio.file.Path path = LocalUtils.javaPath(link);
            java.nio.file.Path target = java.nio.file.Files.readSymbolicLink(path);

            RelativePath parent = link.getRelativePath().getParent();

            if (parent == null || target.isAbsolute()) {
                return new PathImplementation(link.getFileSystem(), new RelativePath(target.toString()));
            }

            return newPath(link.getFileSystem(), parent.resolve(new RelativePath(target.toString())));
        } catch (IOException e) {
            throw new XenonException(ADAPTOR_NAME, "Failed to read symbolic link.", e);
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter filter) throws XenonException {
        FileAttributes att = getAttributes(dir);

        if (!att.isDirectory()) {
            throw new InvalidPathException(ADAPTOR_NAME, "File is not a directory.");
        }

        if (filter == null) {
            throw new XenonException(ADAPTOR_NAME, "Filter is null.");
        }

        return new LocalDirectoryStream(dir, filter);
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(Path dir, DirectoryStream.Filter filter)
            throws XenonException {

        FileAttributes att = getAttributes(dir);

        if (!att.isDirectory()) {
            throw new InvalidPathException(ADAPTOR_NAME, "File is not a directory.");
        }

        if (filter == null) {
            throw new XenonException(ADAPTOR_NAME, "Filter is null.");
        }

        return new LocalDirectoryAttributeStream(this, new LocalDirectoryStream(dir, filter));
    }

    @Override
    public InputStream newInputStream(Path path) throws XenonException {

        if (!exists(path)) {
            throw new NoSuchPathException(ADAPTOR_NAME, "File " + path + " does not exist!");
        }

        FileAttributes att = getAttributes(path);

        if (att.isDirectory()) {
            throw new InvalidPathException(ADAPTOR_NAME, "Path " + path + " is a directory!");
        }

        return LocalUtils.newInputStream(path);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws XenonException {

        OpenOptions tmp = OpenOptions.processOptions(ADAPTOR_NAME, options);

        if (tmp.getReadMode() != null) {
            throw new InvalidOptionsException(ADAPTOR_NAME, "Disallowed open option: READ");
        }

        if (tmp.getAppendMode() == null) {
            throw new InvalidOptionsException(ADAPTOR_NAME, "No append mode provided!");
        }

        if (tmp.getWriteMode() == null) {
            tmp.setWriteMode(OpenOption.WRITE);
        }

        if (tmp.getOpenMode() == OpenOption.CREATE && exists(path)) {
            throw new PathAlreadyExistsException(ADAPTOR_NAME, "File already exists: " + path);
        } else if (tmp.getOpenMode() == OpenOption.OPEN && !exists(path)) {
            throw new NoSuchPathException(ADAPTOR_NAME, "File does not exist: " + path);
        }

        try {
            return java.nio.file.Files.newOutputStream(LocalUtils.javaPath(path), LocalUtils.javaOpenOptions(options));
        } catch (IOException e) {
            throw new XenonException(ADAPTOR_NAME, "Failed to create OutputStream.", e);
        }
    }

    @Override
    public FileAttributes getAttributes(Path path) throws XenonException {
        return new LocalFileAttributes(path);
    }

    @Override
    public boolean exists(Path path) throws XenonException {
        return java.nio.file.Files.exists(LocalUtils.javaPath(path));
    }

    @Override
    public void setPosixFilePermissions(Path path, Set<PosixFilePermission> permissions) throws XenonException {

        if (!exists(path)) {
            throw new NoSuchPathException(ADAPTOR_NAME, "File " + path + " does not exist!");
        }

        if (permissions == null) {
            throw new XenonException(ADAPTOR_NAME, "Permissions is null!");
        }

        LocalUtils.setPosixFilePermissions(path, permissions);
    }

    @Override
    public FileSystem newFileSystem(String location, Credential credential, Map<String, String> properties) 
            throws XenonException {
        
        checkFileLocation(location);
        LocalUtils.checkCredential(ADAPTOR_NAME, credential);

        XenonProperties p = new XenonProperties(VALID_PROPERTIES, properties);

        String root = Utils.getLocalRoot(location);
        RelativePath relativePath = new RelativePath(root).relativize(new RelativePath(location));

        return new FileSystemImplementation(ADAPTOR_NAME, "localfs-" + getNextFsID(), "file", root,  
                relativePath, credential, p);
    }

    @Override
    public void close(FileSystem filesystem) throws XenonException {
        // ignored!
    }

    @Override
    public boolean isOpen(FileSystem filesystem) throws XenonException {
        return true;
    }

    @Override
    public void createDirectory(Path dir) throws XenonException {
        if (exists(dir)) {
            throw new PathAlreadyExistsException(ADAPTOR_NAME, "Directory " + dir + " already exists!");
        }

        checkParent(dir);

        try {
            java.nio.file.Files.createDirectory(LocalUtils.javaPath(dir));
        } catch (IOException e) {
            throw new XenonException(ADAPTOR_NAME, "Failed to create directory " + dir, e);
        }
    }

    @Override
    public void createFile(Path path) throws XenonException {

        if (exists(path)) {
            throw new PathAlreadyExistsException(ADAPTOR_NAME, "File " + path + " already exists!");
        }

        checkParent(path);

        LocalUtils.createFile(path);
    }

    @Override
    public void delete(Path path) throws XenonException {
        LocalUtils.delete(path);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir) throws XenonException {
        return newDirectoryStream(dir, FilesEngine.ACCEPT_ALL_FILTER);
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(Path dir) throws XenonException {
        return newAttributesDirectoryStream(dir, FilesEngine.ACCEPT_ALL_FILTER);
    }

    @Override
    public Map<String, String> getAdaptorSpecificInformation() {
        return new HashMap<>();
    }

    @Override
    public void end() {
        // Nothing to do here...
    }
}
