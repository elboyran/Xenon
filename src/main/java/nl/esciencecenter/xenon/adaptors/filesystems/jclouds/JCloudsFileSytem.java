package nl.esciencecenter.xenon.adaptors.filesystems.jclouds;

import nl.esciencecenter.xenon.UnsupportedOperationException;
import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.adaptors.NotConnectedException;
import nl.esciencecenter.xenon.adaptors.XenonProperties;
import nl.esciencecenter.xenon.adaptors.filesystems.PathAttributesImplementation;
import nl.esciencecenter.xenon.filesystems.*;
import org.apache.sshd.common.util.io.EmptyInputStream;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.*;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.http.functions.ParseURIFromListOrLocationHeaderIf20x;

import java.io.*;
import java.util.*;

// We emulate the presence of (empty) directories by a file ending in a /
// this is the same behaviour as the official S3 console

public class JCloudsFileSytem extends FileSystem {

    private static final String NOT_EMPTY = "___not__empty___";
    final String bucket;
    final BlobStoreContext context;
    final String adaptorName;
    final String endPoint;
    boolean open ;

    public JCloudsFileSytem(String uniqueID,String adaptorName, String endPoint, BlobStoreContext context, String bucket,  XenonProperties properties) {
        super(uniqueID,adaptorName,endPoint,new Path(""),properties);
        this.context = context;
        this.bucket = bucket;
        this.adaptorName = adaptorName;
        this.open = true;
        this.endPoint = endPoint;
    }

    @Override
    public void close() throws XenonException {
        checkClosed();
        context.close();
        open = false;
    }

    @Override
    public boolean isOpen() throws XenonException {
        return open;
    }

    @Override
    public void rename(Path source, Path target) throws XenonException {
        throw new UnsupportedOperationException(adaptorName,"This adaptor does not support renaming.");
    }

    void checkClosed() throws XenonException{
        if(!isOpen()){
            throw new NotConnectedException(getAdaptorName(), "Already closed file system!");
        }
    }

/*
    @Override
    protected void copyFile(Path source, FileSystem destinationFS, Path destination, CopyMode mode) throws XenonException{

        if(destinationFS instanceof  JCloudsFileSytem){
            JCloudsFileSytem dest = (JCloudsFileSytem)destinationFS;
            if( dest.endPoint.equals(endPoint) && dest.adaptorName.equals(adaptorName)){
                if (exists(destination)) {
                    switch (mode) {
                        case CREATE:
                            throw new PathAlreadyExistsException(getAdaptorName(), "Destination path already exists: " + destination);
                        case IGNORE:
                            return;
                        case REPLACE:
                            // continue
                            break;
                    }
                }
                context.getBlobStore().copyBlob(bucket,source.getRelativePath(),
                        dest.bucket,destination.getRelativePath(),CopyOptions.NONE);
                return;
            }
        }
        // revert to default copy
        super.copyFile(source,destinationFS,destination,mode);
    }
*/

    @Override
    public void createDirectory(Path dir) throws XenonException {
        checkClosed();
        if(exists(dir)){
            throw new PathAlreadyExistsException(adaptorName, "Cannot create directory, path already exists : " + dir.getRelativePath());
        }
        if(!exists(dir.getParent())) {
            throw new NoSuchPathException(adaptorName, "Cannot create file, " + dir.getRelativePath() + ", parent directory " + dir.getParent().getRelativePath() + "does not exists.");
        }
        makeNonEmptyFile(dir);
    }

    private void makeNonEmptyFile(Path dir) {
        String existsFile = dir.getRelativePath() +  "/" + NOT_EMPTY;
        final Blob b = context.getBlobStore().blobBuilder(bucket).name(existsFile).payload(new EmptyInputStream()).contentLength(0).build();
        context.getBlobStore().putBlob(bucket,b);
    }

    private void removeNonEmptyFile(Path dir){
        String existsFile = dir.getRelativePath() +  "/" + NOT_EMPTY;
        if(context.getBlobStore().blobExists(bucket,existsFile )){
            context.getBlobStore().removeBlob(bucket, existsFile);
        }
    }

    @Override
    public void createFile(Path file) throws XenonException {
        checkClosed();
        if(exists(file)){
            throw new PathAlreadyExistsException(adaptorName, "Cannot create file, path already exists : " + file.getRelativePath());
        }
        if(!exists(file.getParent())){
            throw new NoSuchPathException(adaptorName, "Cannot create file, " + file.getRelativePath() + ", parent directory " + file.getParent().getRelativePath() + "does not exists.");
        }
        removeNonEmptyFile(file.getParent());
        makeEmptyFile(file);
    }

    private void makeEmptyFile(Path file) {
        String path = file.getRelativePath();
        InputStream emtpy = new org.apache.sshd.common.util.io.NullInputStream();
        final Blob b = context.getBlobStore().blobBuilder(bucket).name(path).payload(emtpy).contentLength(0).build();
        context.getBlobStore().putBlob(bucket,b);
    }

    @Override
    public void createSymbolicLink(Path link, Path target) throws XenonException {
        throw new AttributeNotSupportedException(adaptorName, "Symbolic link  not supported by " + adaptorName);
    }


    @Override
    public void deleteFile(Path file) throws XenonException{
        checkClosed();
        context.getBlobStore().removeBlob(bucket,file.getRelativePath());
        ensureDirectoryExists(file.getParent());

    }

    private void ensureDirectoryExists(Path dir) throws XenonException {
        if(dir.equals(new Path(""))){
            return;             // do not put a not empty file at the root
        }
        if(!list(dir,false).iterator().hasNext()){
            makeNonEmptyFile(dir);
        }
    }

    @Override
    protected void deleteDirectory(Path dir) throws XenonException {
        checkClosed();
        // there are two options: the directory is empty or not.
        // in the former case the __not_empty__ file is deleted and no exception is thrown (correct behaviour)
        // in the latter case the __not_empty__ file is deleted and an exception is thrown (correct behaviour)
        removeNonEmptyFile(dir);
        if(list(dir, false).iterator().hasNext()){
            throw new XenonException(adaptorName, "Cannot delete directory: " + dir.getRelativePath() + " not empty!");
        }

    }

    @Override
    protected Iterable<PathAttributes> listDirectory(Path dir) throws XenonException {
        return list(dir, false);
    }



    @Override
    public boolean exists(Path path) throws XenonException {
        assertNotNull(path);
        checkClosed();
        return fileExists(path) || dirExists(path);
    }

    private boolean dirExists(Path path) {
        String name = path.getRelativePath();
        return listPrefix(name + "/", false).iterator().hasNext();
    }

    private boolean fileExists(Path path) {
        return context.getBlobStore().blobExists(bucket,path.getRelativePath());
    }


    PathAttributes toPathAttributes(final StorageMetadata m, final BlobAccess access){
        Path p = new Path(m.getName());
        System.out.println(m);
        PathAttributesImplementation pa = new PathAttributesImplementation();
        pa.setPath(p);
        if(m.getSize() != null) {
            pa.setSize(m.getSize());
        }
        Date d = m.getCreationDate();
        if(d != null){
            pa.setCreationTime(d.getTime());
        }
        d = m.getLastModified();
        if(d != null){
            pa.setLastModifiedTime(d.getTime());
        }
        pa.setReadable(true);
        pa.setWritable(true);
        switch(m.getType()){
            case RELATIVE_PATH:
                pa.setDirectory(true); break;
            default: pa.setDirectory(false); break;
        }
        /*
        Set<PosixFilePermission> permissions = new HashSet<>();
        permissions.add(PosixFilePermission.OWNER_READ);
        permissions.add(PosixFilePermission.OWNER_WRITE);
        if(access == BlobAccess.PUBLIC_READ){
            permissions.add(PosixFilePermission.OTHERS_READ);
           // if(isDir) { permissions.add(PosixFilePermission.OTHERS_EXECUTE); }
        }
        */
        pa.setPermissions(null);
        return pa;
    }

    class ListingIterator implements Iterator<PathAttributes> {

        private final ListContainerOptions options;
        Iterator<? extends StorageMetadata> curIterator;
        PageSet<? extends StorageMetadata> curPageSet;
        StorageMetadata nxt;

        ListingIterator(ListContainerOptions options,  PageSet<? extends StorageMetadata> pageSet){
            this.options = options;
            this.curPageSet = pageSet;
            this.curIterator = curPageSet.iterator();
            getNext();
        }

        ListingIterator(ListContainerOptions options, Iterator<? extends StorageMetadata> curIterator, PageSet<? extends StorageMetadata> pageSet){
            this.options = options;
            this.curIterator = curIterator;
            this.nxt = nxt;
            this.curPageSet = pageSet;
        }

        void getNext(){
            if (!curIterator.hasNext() && curPageSet.getNextMarker() != null){
                curPageSet = context.getBlobStore().list(bucket,options.afterMarker(curPageSet.getNextMarker()));
                curIterator = curPageSet.iterator();
            }
            if(curIterator.hasNext()){
                nxt = curIterator.next();
            } else {
                nxt = null;
            }
            if(nxt != null && nxt.getName().endsWith(NOT_EMPTY)){

                getNext();
            }

        }

        @Override
        public boolean hasNext() { return  nxt != null; }

        @Override
        public PathAttributes next() {

            BlobAccess access = BlobAccess.PUBLIC_READ; // context.getBlobStore().getBlobAccess(bucket,nxt.getName());
            PathAttributes res = toPathAttributes(nxt,access);

            getNext();



            return res;
        }
    }


    public Iterable<PathAttributes> list(Path dir, boolean recursive) throws XenonException{
        checkClosed();
        if(context.getBlobStore().blobExists(bucket,dir.getRelativePath())){
            throw new InvalidPathException(getAdaptorName(), "Not a directory: " + dir.getRelativePath());
        }
        ListContainerOptions options = new ListContainerOptions().prefix(dir.getRelativePath() +"/");
        if(recursive) { options = options.recursive(); }
        final ListContainerOptions optionsFinal = options;
        final PageSet<? extends StorageMetadata> ps = context.getBlobStore().list(bucket,optionsFinal);
        final Iterator<? extends StorageMetadata> curIt = ps.iterator();
        if(!curIt.hasNext()){
            throw new NoSuchPathException(adaptorName, "No such directory: "  + dir.getRelativePath());
        }
        return new Iterable<PathAttributes>() {
            @Override
            public Iterator<PathAttributes> iterator() {
                return new ListingIterator(optionsFinal, curIt,   context.getBlobStore().list(bucket,optionsFinal));
            }
        };

    }

    private Iterable<PathAttributes> listPrefix(String prefix, boolean recursive){
        ListContainerOptions options = new ListContainerOptions().prefix(prefix);

        if(recursive) { options = options.recursive(); }
        final ListContainerOptions optionsFinal = options;


        return new Iterable<PathAttributes>() {
            @Override
            public Iterator<PathAttributes> iterator() {
                return new ListingIterator(optionsFinal, context.getBlobStore().list(bucket,optionsFinal));
            }
        };
    }

    @Override
    public InputStream readFromFile(Path path) throws XenonException {
        String name = path.getRelativePath();
        boolean exists = context.getBlobStore().blobExists(bucket,name);
        if(exists) {
            Blob b = context.getBlobStore().getBlob(bucket, path.getRelativePath());
            try {
                return b.getPayload().openStream();
            } catch (IOException e) {
                throw new XenonException(adaptorName, e.getMessage());
            }
        } else {
            throw new NoSuchPathException(adaptorName,"No such file: " + name);
        }
    }

    @Override
    public OutputStream writeToFile(Path path, long size) throws XenonException {
        final PipedInputStream read = new PipedInputStream();
        final Blob b = context.getBlobStore().blobBuilder(bucket).name(path.getRelativePath()).payload(read).contentLength(size).build();
        try {
            final OutputStream out = new PipedOutputStream(read);
            new Thread(new Runnable() {

                @Override
                public void run() {
                    context.getBlobStore().putBlob(bucket,b);
                }
            }).start();
            return out;
        } catch(IOException e){
            throw new XenonException(adaptorName,"IO error when trying to write: " + e.getMessage());
        }
    }

    @Override
    public OutputStream writeToFile(Path file) throws XenonException {
        throw new XenonException(adaptorName, "Sorry, this adaptor needs to know the size of a file before we start writing.");
    }

    @Override
    public OutputStream appendToFile(Path file) throws XenonException {
        throw new XenonException(adaptorName, "Sorry, this adaptor does not support appending.");
    }




    @Override
    public PathAttributes getAttributes(Path path) throws XenonException {

        String name = path.getRelativePath();
        for (PathAttributes p : listPrefix(name, false)) {
            if ( p.getPath().equals(path)) {
                return p;
            }
        }

        throw new NoSuchPathException(adaptorName, "File does not exist: " + path.getRelativePath());

    }

    @Override
    public Path readSymbolicLink(Path link) throws XenonException {
        throw new AttributeNotSupportedException(adaptorName, "Symbolic link  not supported by " + adaptorName);
    }

    @Override
    public void setPosixFilePermissions(Path path, Set<PosixFilePermission> permissions) throws XenonException {
        throw new UnsupportedOperationException(getAdaptorName(), "This adaptor does not support POSIX permissions");
        /*
        checkClosed();
        if(!context.getBlobStore().blobExists(bucket,path.getRelativePath())){
            throw new NoSuchPathException(adaptorName,"Cannot set permissions, no such blob: " + path.getRelativePath());
        }
        String s = path.getRelativePath();

        boolean publicAccess = false;
        for(PosixFilePermission p : permissions){
            switch(p){
                case OTHERS_READ: publicAccess = true; break;
                default : break;
            }
        }
        BlobAccess ba = publicAccess ? BlobAccess.PUBLIC_READ : BlobAccess.PRIVATE;
        context.getBlobStore().setBlobAccess(bucket,s,ba);
        */
    }





}