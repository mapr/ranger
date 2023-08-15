/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.services.hdfs.client.minicluster;

import java.io.File;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class MapRMiniDFSCluster {
    private Configuration conf;
    private FileSystem fs;
    private Path workDir;

    public MapRMiniDFSCluster() throws IOException {
        this.workDir = new Path(System.getProperty("test.tmp.dir", "target" + File.separator + "test" + File.separator + "tmp"));
        this.conf = new Configuration();
        this.conf.set("fs.default.name", "file:///");
        this.fs = FileSystem.getLocal(this.conf);
        this.fs.setWorkingDirectory(this.workDir);
    }

    public MapRMiniDFSCluster(Configuration conf) throws IOException {
        this.workDir = new Path(System.getProperty("test.tmp.dir", "target" + File.separator + "test" + File.separator + "tmp"));
        this.conf = conf;
        this.fs = FileSystem.getLocal(conf);
        this.fs.setWorkingDirectory(this.workDir);
    }


    public FileSystem getFileSystem() throws IOException {
        return this.fs;
    }
}