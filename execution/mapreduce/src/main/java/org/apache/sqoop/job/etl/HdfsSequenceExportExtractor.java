/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.job.etl;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.sqoop.common.ImmutableContext;
import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.job.MapreduceExecutionError;
import org.apache.sqoop.job.PrefixContext;
import org.apache.sqoop.job.io.Data;
import org.apache.sqoop.job.io.DataWriter;

public class HdfsSequenceExportExtractor extends Extractor {

  public static final Log LOG =
    LogFactory.getLog(HdfsSequenceExportExtractor.class.getName());

  private Configuration conf;
  private DataWriter datawriter;

  private final char fieldDelimiter;

  public HdfsSequenceExportExtractor() {
    fieldDelimiter = Data.DEFAULT_FIELD_DELIMITER;
  }

  @Override
  public void run(ImmutableContext context, Object connectionConfiguration,
      Object jobConfiguration, Partition partition, DataWriter writer) {
    writer.setFieldDelimiter(fieldDelimiter);

    conf = ((PrefixContext)context).getConfiguration();
    datawriter = writer;

    try {
      HdfsExportPartition p = (HdfsExportPartition)partition;
      LOG.info("Working on partition: " + p);
      int numFiles = p.getNumberOfFiles();
      for (int i=0; i<numFiles; i++) {
        extractFile(p.getFile(i), p.getOffset(i), p.getLength(i));
      }
    } catch (IOException e) {
      throw new SqoopException(MapreduceExecutionError.MAPRED_EXEC_0017, e);
    }
  }

  private void extractFile(Path file, long start, long length)
      throws IOException {
    long end = start + length;
    LOG.info("Extracting file " + file);
    LOG.info("\t from offset " + start);
    LOG.info("\t to offset " + end);
    LOG.info("\t of length " + length);

    SequenceFile.Reader filereader = new SequenceFile.Reader(file.getFileSystem(conf), file, conf);

    if (start > filereader.getPosition()) {
      filereader.sync(start); // sync to start
    }

    Text line = new Text();
    boolean hasNext = filereader.next(line);
    while (hasNext) {
      datawriter.writeCsvRecord(line.toString());
      line = new Text();
      hasNext = filereader.next(line);
      if(filereader.getPosition() >= end && filereader.syncSeen()) {
        break;
      }
    }
  }

  @Override
  public long getRowsRead() {
    // TODO need to return the rows read
    return 0;
  }

}
