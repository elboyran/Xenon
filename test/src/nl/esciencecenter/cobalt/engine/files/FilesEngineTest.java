/*
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
package nl.esciencecenter.cobalt.engine.files;

import static org.junit.Assert.assertNotNull;

import java.util.HashMap;

import nl.esciencecenter.cobalt.CobaltException;
import nl.esciencecenter.cobalt.CobaltRuntimeException;
import nl.esciencecenter.cobalt.Util;
import nl.esciencecenter.cobalt.engine.CobaltEngine;
import nl.esciencecenter.cobalt.engine.files.FileSystemImplementation;
import nl.esciencecenter.cobalt.engine.files.FilesEngine;
import nl.esciencecenter.cobalt.engine.files.PathImplementation;
import nl.esciencecenter.cobalt.files.CopyOption;
import nl.esciencecenter.cobalt.files.RelativePath;

import org.junit.Test;

public class FilesEngineTest {

    @Test
    public void testFilesEngine() throws Exception {

        CobaltEngine oe = Util.createOctopusEngine(new HashMap<String, String>());
        FilesEngine engine = new FilesEngine(oe);
        String tmp = engine.toString();
        assertNotNull(tmp);
    }

    @Test(expected = CobaltRuntimeException.class)
    public void testUnknownFileSystem() throws Exception {

        CobaltEngine oe = Util.createOctopusEngine(new HashMap<String, String>());
        FilesEngine engine = new FilesEngine(oe);

        FileSystemImplementation fsi = new FileSystemImplementation("test", "test1", "test", "/", new RelativePath(),
                null, null);

        // Should throw exception
        engine.newPath(fsi, new RelativePath("tmp/bla.txt"));
    }

    @Test(expected = CobaltException.class)
    public void testInterSchemeCopy() throws Exception {

        CobaltEngine oe = Util.createOctopusEngine(new HashMap<String, String>());
        FilesEngine engine = new FilesEngine(oe);

        FileSystemImplementation fs1 = new FileSystemImplementation("aap", "test1", "test", "/", new RelativePath(),
                null, null);

        PathImplementation p1 = new PathImplementation(fs1, new RelativePath("test"));

        FileSystemImplementation fs2 = new FileSystemImplementation("noot", "test1", "test", "/", new RelativePath(),
                null, null);

        PathImplementation p2 = new PathImplementation(fs2, new RelativePath("test"));

        // Should throw exception
        engine.copy(p1, p2, CopyOption.CREATE);
    }
}