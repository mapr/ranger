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

package org.apache.ranger.authorization.prestodb.authorizer;

import com.facebook.presto.common.CatalogSchemaName;
import com.facebook.presto.spi.CatalogSchemaTableName;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.security.AccessControlContext;
import com.facebook.presto.spi.security.AccessDeniedException;
import com.facebook.presto.spi.security.Identity;
import com.facebook.presto.spi.security.PrestoPrincipal;
import com.facebook.presto.spi.security.ViewExpression;
import com.google.common.collect.ImmutableSet;

import static com.facebook.presto.spi.security.PrincipalType.USER;
import static com.facebook.presto.spi.security.Privilege.SELECT;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RangerSystemAccessControlTest {
  static RangerSystemAccessControl accessControlManager = null;

  private static final Identity alice = new Identity("alice", Optional.empty());

  private static final Identity bob = new Identity("bob", Optional.empty());

  private static final Set<String> allCatalogs = ImmutableSet.of("open-to-all", "all-allowed", "alice-catalog");
  private static final String aliceCatalog = "alice-catalog";
  private static final CatalogSchemaName aliceSchema = new CatalogSchemaName("alice-catalog", "schema");
  private static final CatalogSchemaTableName aliceTable = new CatalogSchemaTableName("alice-catalog", "schema","table");
  private static final CatalogSchemaTableName aliceView = new CatalogSchemaTableName("alice-catalog", "schema","view");

  public static final AccessControlContext CONTEXT = new AccessControlContext(new QueryId("query_id"), Optional.empty(), Optional.empty());

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Map<String, String> config = new HashMap<>();
    accessControlManager = new RangerSystemAccessControl(config);
  }

  @Test
  public void testCatalogOperations()
  {
    assertEquals(accessControlManager.filterCatalogs(alice, CONTEXT, allCatalogs), allCatalogs);

    Set<String> bobCatalogs = ImmutableSet.of("open-to-all", "all-allowed");
    assertEquals(accessControlManager.filterCatalogs(bob, CONTEXT, allCatalogs), bobCatalogs);
    //Set<String> nonAsciiUserCatalogs = ImmutableSet.of("open-to-all", "all-allowed", "\u0200\u0200\u0200");
    //assertEquals(accessControlManager.filterCatalogs(context(nonAsciiUser), allCatalogs), nonAsciiUserCatalogs);
  }

  @Test
  @SuppressWarnings("PMD")
  public void testSchemaOperations()
  {

    Set<String> aliceSchemas = ImmutableSet.of("schema");
    assertEquals(accessControlManager.filterSchemas(alice, CONTEXT, aliceCatalog, aliceSchemas), aliceSchemas);
    assertEquals(accessControlManager.filterSchemas(bob, CONTEXT, "alice-catalog", aliceSchemas), ImmutableSet.of());

    accessControlManager.checkCanCreateSchema(alice, CONTEXT, aliceSchema);
    accessControlManager.checkCanDropSchema(alice, CONTEXT, aliceSchema);
    accessControlManager.checkCanRenameSchema(alice, CONTEXT, aliceSchema, "new-schema");
    accessControlManager.checkCanShowSchemas(alice, CONTEXT, aliceCatalog);

    try {
      accessControlManager.checkCanCreateSchema(bob, CONTEXT, aliceSchema);
    } catch (AccessDeniedException expected) {
    }
  }

  @Test
  @SuppressWarnings("PMD")
  public void testTableOperations()
  {
    Set<SchemaTableName> aliceTables = ImmutableSet.of(new SchemaTableName("schema", "table"));
    assertEquals(accessControlManager.filterTables((alice), CONTEXT, aliceCatalog, aliceTables), aliceTables);
    assertEquals(accessControlManager.filterTables((bob), CONTEXT,"alice-catalog", aliceTables), ImmutableSet.of());

    accessControlManager.checkCanCreateTable((alice), CONTEXT, aliceTable);
    accessControlManager.checkCanDropTable((alice), CONTEXT, aliceTable);
    accessControlManager.checkCanSelectFromColumns((alice), CONTEXT, aliceTable, ImmutableSet.of());
    accessControlManager.checkCanInsertIntoTable((alice), CONTEXT, aliceTable);
    accessControlManager.checkCanDeleteFromTable((alice), CONTEXT, aliceTable);
    accessControlManager.checkCanRenameColumn((alice), CONTEXT, aliceTable);


    try {
      accessControlManager.checkCanCreateTable((bob), CONTEXT, aliceTable);
    } catch (AccessDeniedException expected) {
    }
  }

  @Test
  @SuppressWarnings("PMD")
  public void testViewOperations()
  {
    accessControlManager.checkCanCreateView((alice), CONTEXT, aliceView);
    accessControlManager.checkCanDropView((alice), CONTEXT, aliceView);
    accessControlManager.checkCanSelectFromColumns((alice), CONTEXT, aliceView, ImmutableSet.of());
    accessControlManager.checkCanCreateViewWithSelectFromColumns((alice), CONTEXT, aliceTable, ImmutableSet.of());
    accessControlManager.checkCanCreateViewWithSelectFromColumns((alice), CONTEXT, aliceView, ImmutableSet.of());
    accessControlManager.checkCanSetCatalogSessionProperty((alice), CONTEXT, aliceCatalog, "property");
    accessControlManager.checkCanGrantTablePrivilege((alice), CONTEXT, SELECT, aliceTable, new PrestoPrincipal(USER, "grantee"), true);
    accessControlManager.checkCanRevokeTablePrivilege((alice), CONTEXT, SELECT, aliceTable, new PrestoPrincipal(USER, "revokee"), true);

    try {
      accessControlManager.checkCanCreateView((bob), CONTEXT, aliceView);
    } catch (AccessDeniedException expected) {
    }
  }

    @Test
    @SuppressWarnings("PMD")
    public void testMisc()
    {
      List<ViewExpression> ret = accessControlManager.getRowFilters(alice, CONTEXT, aliceTable);
      assertEquals(ret.size(), 0);
    }

}