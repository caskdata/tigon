/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.tigon.data.increment.hbase94;

import co.cask.tigon.conf.Constants;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for the HBase 0.94 version of the {@link IncrementHandler} coprocessor.
 */
public class IncrementHandlerTest {
  private static final byte[] EMPTY_BYTES = new byte[0];
  private static final byte[] FAMILY = Bytes.toBytes("i");

  private static HBaseTestingUtility testUtil;
  private static Configuration conf;

  private long ts = 1;

  @BeforeClass
  public static void setup() throws Exception {
    testUtil = new HBaseTestingUtility();
    testUtil.startMiniCluster();
    conf = testUtil.getConfiguration();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    testUtil.shutdownMiniCluster();
  }

  @Test
  public void testIncrements() throws Exception {
    byte[] tableName = Bytes.toBytes("incrementTest");
    HTableDescriptor tableDesc = new HTableDescriptor(tableName);
    HColumnDescriptor columnDesc = new HColumnDescriptor(FAMILY);
    columnDesc.setMaxVersions(Integer.MAX_VALUE);
    tableDesc.addFamily(columnDesc);
    tableDesc.addCoprocessor(IncrementHandler.class.getName());
    testUtil.getHBaseAdmin().createTable(tableDesc);
    testUtil.waitTableAvailable(tableName, 5000);

    HTable table = new HTable(conf, tableName);
    try {
      byte[] colA = Bytes.toBytes("a");
      byte[] row1 = Bytes.toBytes("row1");

      // test column containing only increments
      table.put(newIncrement(row1, colA, 1));
      table.put(newIncrement(row1, colA, 1));
      table.put(newIncrement(row1, colA, 1));

      assertColumn(table, row1, colA, 3);

      // test intermixed increments and puts
      Put putA = new Put(row1);
      putA.add(FAMILY, colA, ts++, Bytes.toBytes(5L));
      table.put(putA);

      assertColumn(table, row1, colA, 5);

      table.put(newIncrement(row1, colA, 1));
      table.put(newIncrement(row1, colA, 1));

      assertColumn(table, row1, colA, 7);

      // test multiple increment columns
      byte[] row2 = Bytes.toBytes("row2");
      byte[] colB = Bytes.toBytes("b");

      // increment A and B twice at the same timestamp
      table.put(newIncrement(row2, colA, 1, 1));
      table.put(newIncrement(row2, colB, 1, 1));
      table.put(newIncrement(row2, colA, 2, 1));
      table.put(newIncrement(row2, colB, 2, 1));
      // increment A once more
      table.put(newIncrement(row2, colA, 1));

      assertColumns(table, row2, new byte[][]{ colA, colB }, new long[]{ 3, 2 });

      // overwrite B with a new put
      Put p = new Put(row2);
      p.add(FAMILY, colB, ts++, Bytes.toBytes(10L));
      table.put(p);

      assertColumns(table, row2, new byte[][]{ colA, colB }, new long[]{ 3, 10 });

      // check a full scan
      Scan scan = new Scan();
      ResultScanner scanner = table.getScanner(scan);
      // row1
      Result scanRes = scanner.next();
      assertNotNull(scanRes);
      assertFalse(scanRes.isEmpty());
      KeyValue scanResCell = scanRes.getColumnLatest(FAMILY, colA);
      assertArrayEquals(row1, scanResCell.getRow());
      assertEquals(7L, Bytes.toLong(scanResCell.getValue()));

      // row2
      scanRes = scanner.next();
      assertNotNull(scanRes);
      assertFalse(scanRes.isEmpty());
      scanResCell = scanRes.getColumnLatest(FAMILY, colA);
      assertArrayEquals(row2, scanResCell.getRow());
      assertEquals(3L, Bytes.toLong(scanResCell.getValue()));
      scanResCell = scanRes.getColumnLatest(FAMILY, colB);
      assertArrayEquals(row2, scanResCell.getRow());
      assertEquals(10L, Bytes.toLong(scanResCell.getValue()));
    } finally {
      table.close();
    }
  }

  private void assertColumn(HTable table, byte[] row, byte[] col, long expected) throws Exception {
    Result res = table.get(new Get(row));
    KeyValue resA = res.getColumnLatest(FAMILY, col);
    assertFalse(res.isEmpty());
    assertNotNull(resA);
    assertEquals(expected, Bytes.toLong(resA.getValue()));

    Scan scan = new Scan(row);
    scan.addFamily(FAMILY);
    ResultScanner scanner = table.getScanner(scan);
    Result scanRes = scanner.next();
    assertNotNull(scanRes);
    assertFalse(scanRes.isEmpty());
    KeyValue scanResA = scanRes.getColumnLatest(FAMILY, col);
    assertArrayEquals(row, scanResA.getRow());
    assertEquals(expected, Bytes.toLong(scanResA.getValue()));
  }

  private void assertColumns(HTable table, byte[] row, byte[][] cols, long[] expected) throws Exception {
    assertEquals(cols.length, expected.length);

    Get get = new Get(row);
    Scan scan = new Scan(row);
    for (byte[] col : cols) {
      get.addColumn(FAMILY, col);
      scan.addColumn(FAMILY, col);
    }

    // check get
    Result res = table.get(get);
    assertFalse(res.isEmpty());
    for (int i = 0; i < cols.length; i++) {
      KeyValue resCell = res.getColumnLatest(FAMILY, cols[i]);
      assertNotNull(resCell);
      assertEquals(expected[i], Bytes.toLong(resCell.getValue()));
    }

    // check scan
    ResultScanner scanner = table.getScanner(scan);
    Result scanRes = scanner.next();
    assertNotNull(scanRes);
    assertFalse(scanRes.isEmpty());
    for (int i = 0; i < cols.length; i++) {
      KeyValue scanResCell = scanRes.getColumnLatest(FAMILY, cols[i]);
      assertArrayEquals(row, scanResCell.getRow());
      assertEquals(expected[i], Bytes.toLong(scanResCell.getValue()));
    }
  }

  public Put newIncrement(byte[] row, byte[] column, long value) {
      return newIncrement(row, column, ts++, value);
  }

  public Put newIncrement(byte[] row, byte[] column, long timestamp, long value) {
    Put p = new Put(row);
    p.add(FAMILY, column, timestamp, Bytes.toBytes(value));
    p.setAttribute(Constants.DELTA_WRITE, EMPTY_BYTES);
    return p;
  }
}
