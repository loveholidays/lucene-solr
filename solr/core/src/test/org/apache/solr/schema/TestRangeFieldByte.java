/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.schema;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.RangeFieldInt.ParsedRange;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestRangeFieldByte extends SolrTestCaseJ4 {

  @BeforeClass
  public static void setupCore() throws Exception {
    initCore("solrconfig-minimal.xml", "rangefield-schema-byte.xml");
    assertU(adoc("id", "0", "range", "[0 TO 10, 1 TO 10, 0, 0]"));
    assertU(adoc("id", "1", "range", "[1 TO 10, 1 TO 10, 0, 69]"));
    assertU(adoc("id", "2", "range", "[1 TO 10, 1 TO 5, 0, 59]"));
    assertU(adoc("id", "3", "range", "[0,0,0,49]"));
    assertU(commit());
  }

  @Test
  public void testRangeQuery() throws Exception {

    SolrQueryRequest req = req("q", "{!pr field=range method=intersects}(4, 9, 0, 0)", "fl", "range");
    assertQ(req, "//result[@numFound='1']");

    String out = h.query(req);
    assertTrue(out.contains("[0 TO 10"));

    assertQ(req("q", "{!pr field=range method=i}[0, 0, 0, 40 TO 80]"), "//result[@numFound='1']");
    assertQ(req("q", "{!pr field=range method=i}[5, 5, 0, 40 TO 80]"), "//result[@numFound='2']");
    assertQ(req("q", "{!pr field=range method=i}[5, 5, 0, 40 TO 60]"), "//result[@numFound='1']");
    assertQ(req("q", "{!pr field=range method=i}[5, 10, 0, 40 TO 80]"), "//result[@numFound='1']");
    assertQ(req("q", "{!pr field=range method=i}[5, 10, 12, 40 TO 80]"), "//result[@numFound='0']");

  }

  @Test
  public void testRangeQueryInvalidRange() throws Exception {

    SolrQueryRequest req = req("q", "{!pr field=range method=intersects}(4, 9, 0, 0)", "fl", "range");
    assertQ(req, "//result[@numFound='1']");

    String out = h.query(req);
    assertTrue(out.contains("[0 TO 10"));

    assertQ(req("q", "{!pr field=range method=i}[0, 0, 0, -128 TO 127]"), "//result[@numFound='1']");
    try {
      assertQ(req("q", "{!pr field=range method=i}[0, 0, 0, -128 TO 128]"), "//result[@numFound='1']");
    } catch (Exception e) {
      assertEquals(RuntimeException.class, e.getClass());
      assertEquals(NumberFormatException.class, e.getCause().getClass());
    }
  }

  @Test(expected = SolrException.class)
  public void testRangeQueryWithInvalidRangeAtIndexingTime() throws Exception {

    assertU(adoc("id", "1", "range", "[1 TO 10, 1 TO 10, 0, 69389]"));

  }

  @Test
  public void testSimpleRange() {
    ParsedRange range = new ParsedRange("[4 TO 6]");
    assertEquals(6, range.max[0], 0);
    assertEquals(4, range.min[0], 0);
    assertEquals(1, range.max.length);
    assertEquals(1, range.min.length);
  }

  @Test
  public void testMultipleDimensions() {
    ParsedRange range = new ParsedRange("[5 TO 11, 3 TO 10]");
    assertEquals(2, range.max.length);
    assertEquals(2, range.min.length);
    assertEquals(5, range.min[0], 0);
    assertEquals(3, range.min[1], 0);
    assertEquals(11, range.max[0], 0);
    assertEquals(10, range.max[1], 0);
  }

  @Test
  public void testMinAndMax() {
    ParsedRange range = new ParsedRange("[* TO 4, 5 TO *]");
    assertEquals(Integer.MIN_VALUE, range.min[0], 0);
    assertEquals(5, range.min[1], 0);
    assertEquals(4, range.max[0], 0);
    assertEquals(Integer.MAX_VALUE, range.max[1], 0);
  }

  @Test
  public void testNegatives() {
    ParsedRange range = new ParsedRange("[-1 TO 1]");
    assertEquals(-1, range.min[0], 0);
    assertEquals(1, range.max[0], 0);
  }

  @Test
  public void testWhitespace() {
    ParsedRange range = new ParsedRange("(4, 6)");
    assertEquals(4, range.min[0], 0);
    assertEquals(4, range.max[0], 0);
    assertEquals(6, range.min[1], 0);
    assertEquals(6, range.max[1], 0);
  }

}
