/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.keycloakusersync;

import org.apache.hadoop.security.authentication.util.SsoConfigurationUtil;
import org.apache.ranger.ugsyncutil.model.KeycloakSyncSourceInfo;
import org.apache.ranger.ugsyncutil.model.UgsyncAuditInfo;
import org.apache.ranger.ugsyncutil.model.UsersGroupRoleAssignments;
import org.apache.ranger.ugsyncutil.util.UgsyncCommonConstants;
import org.apache.ranger.unixusersync.config.UserGroupSyncConfig;
import org.apache.ranger.usergroupsync.UserGroupSink;
import org.apache.ranger.usergroupsync.UserGroupSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AbstractUserRepresentation;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KeycloakUserGroupSource implements UserGroupSource {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserGroupSource.class);

  public static final String SYNC_SOURCE = "Keycloak";

  private boolean deletesEnabled;
  private long deleteFrequency;
  private long deleteCycles;

  private String clientId;

  private UsersResource usersResource;
  private GroupsResource groupsResource;

  private UgsyncAuditInfo ugsyncAuditInfo;

  @Override
  public void init() throws Throwable {
    UserGroupSyncConfig ugSyncConfig = UserGroupSyncConfig.getInstance();
    SsoConfigurationUtil ssoConfig = SsoConfigurationUtil.getInstance();

    deletesEnabled = ugSyncConfig.isUserSyncDeletesEnabled();
    deleteFrequency = ugSyncConfig.getUserSyncDeletesFrequency();
    clientId = ssoConfig.getClientId();
    String issuerUrl = ssoConfig.getClientIssuer();

    String[] issuerUrlParts = issuerUrl.split("/realms/");
    Keycloak keycloak = Keycloak.getInstance(
            issuerUrlParts[0], // keycloak server url
            issuerUrlParts[1], // realm name
            ugSyncConfig.getKeycloakUsername(),
            ugSyncConfig.getKeycloakPassword(),
            clientId,
            ssoConfig.getClientSecret(),
            SSLContext.getDefault()
    );

    RealmResource realmResource = keycloak.realm(issuerUrlParts[1]);
    usersResource = realmResource.users();
    groupsResource = realmResource.groups();

    KeycloakSyncSourceInfo keycloakSyncSourceInfo = new KeycloakSyncSourceInfo();
    keycloakSyncSourceInfo.setRealmUrl(issuerUrl);
    keycloakSyncSourceInfo.setClientId(ssoConfig.getClientId());
    ugsyncAuditInfo = new UgsyncAuditInfo();
    ugsyncAuditInfo.setSyncSource(SYNC_SOURCE);
    ugsyncAuditInfo.setKeycloakSyncSourceInfo(keycloakSyncSourceInfo);
  }

  @Override
  public boolean isChanged() {
    // No incremental syncing support
    return false;
  }

  @Override
  public void updateSink(UserGroupSink sink) throws Throwable {
    List<UserRepresentation> userRepresentations = usersResource.list();
    List<GroupRepresentation> groupRepresentations = groupsResource.groups();

    addOrUpdateGropus(sink, userRepresentations, groupRepresentations);
    updateRoles(sink, userRepresentations, groupRepresentations);
    sink.postUserGroupAuditInfo(ugsyncAuditInfo);
  }

  private void addOrUpdateGropus(UserGroupSink sink,
                                 List<UserRepresentation> userRepresentations,
                                 List<GroupRepresentation> groupRepresentations) throws Throwable {
    sink.addOrUpdateUsersGroups(
            toMap(groupRepresentations,
                    GroupRepresentation::getName,
                    groupRepresentation -> {
                      Map<String, String> attributes = new HashMap<>();
                      attributes.put(UgsyncCommonConstants.SYNC_SOURCE, SYNC_SOURCE);
                      attributes.put(UgsyncCommonConstants.ORIGINAL_NAME, groupRepresentation.getName());
                      attributes.put(UgsyncCommonConstants.FULL_NAME, groupRepresentation.getName());
                      return attributes;
                    }
            ),
            toMap(userRepresentations,
                    AbstractUserRepresentation::getUsername,
                    userRepresentation -> {
                      Map<String, String> attributes = new HashMap<>();
                      attributes.put(UgsyncCommonConstants.SYNC_SOURCE, SYNC_SOURCE);
                      attributes.put(UgsyncCommonConstants.ORIGINAL_NAME, userRepresentation.getUsername());
                      attributes.put(UgsyncCommonConstants.FULL_NAME, userRepresentation.getUsername());
                      return attributes;
                    }
            ),
            toMap(groupRepresentations,
                    GroupRepresentation::getName,
                    groupRepresentation -> toSet(getGroupResource(groupRepresentation).members(), AbstractUserRepresentation::getUsername)
            ),
            shouldComputeDeletes());
  }

  private void updateRoles(UserGroupSink sink,
                           List<UserRepresentation> userRepresentations,
                           List<GroupRepresentation> groupRepresentations) {
    UsersGroupRoleAssignments usersGroupRoleAssignments = new UsersGroupRoleAssignments();
    usersGroupRoleAssignments.setUsers(toList(userRepresentations, AbstractUserRepresentation::getUsername));
    usersGroupRoleAssignments.setUserRoleAssignments(toMap(userRepresentations,
            AbstractUserRepresentation::getUsername,
            this::getRangerRole
    ));
    usersGroupRoleAssignments.setGroupRoleAssignments(toMap(groupRepresentations,
            GroupRepresentation::getName,
            this::getRangerRole
    ));
    sink.updateRoles(usersGroupRoleAssignments);
  }

  private boolean shouldComputeDeletes() {
    if (!deletesEnabled) {
      return false;
    }
    if (deleteCycles < deleteFrequency) {
      deleteCycles++;
      return false;
    }
    deleteCycles = 1;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Compute deleted users/groups is enabled for this sync cycle");
    }
    return true;
  }

  private GroupResource getGroupResource(GroupRepresentation groupRepresentation) {
    return groupsResource.group(groupRepresentation.getId());
  }

  private String getRangerRole(UserRepresentation userRepresentation) {
    return getRangerRole(usersResource.get(userRepresentation.getId()).roles().getAll());
  }

  private String getRangerRole(GroupRepresentation groupRepresentation) {
    return getRangerRole(getGroupResource(groupRepresentation).roles().getAll());
  }

  private String getRangerRole(MappingsRepresentation mappingsRepresentation) {
    List<String> realmRoles = Optional.ofNullable(mappingsRepresentation)
            .map(MappingsRepresentation::getRealmMappings)
            .map(rm -> toList(rm, RoleRepresentation::getName))
            .orElseGet(Collections::emptyList);

    List<String> clientRoles = Optional.ofNullable(mappingsRepresentation)
            .map(MappingsRepresentation::getClientMappings)
            .map(cm -> cm.get(clientId))
            .map(ClientMappingsRepresentation::getMappings)
            .map(cm -> toList(cm, RoleRepresentation::getName))
            .orElseGet(Collections::emptyList);

    List<String> keycloakRoles = new ArrayList<>(realmRoles);
    keycloakRoles.addAll(clientRoles);
    if (keycloakRoles.contains("fabric-manager")) {
      return "ROLE_SYS_ADMIN";
    }
    return "ROLE_USER";
  }

  private <T, R> Set<R> toSet(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream().map(mapper).collect(Collectors.toSet());
  }

  private <T, R> List<R> toList(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream().map(mapper).collect(Collectors.toList());
  }

  private <T, KR, VR> Map<KR, VR> toMap(Collection<T> collection, Function<T, KR> keyMapper, Function<T, VR> valueMapper) {
    return collection.stream().collect(Collectors.toMap(keyMapper, valueMapper));
  }

}
