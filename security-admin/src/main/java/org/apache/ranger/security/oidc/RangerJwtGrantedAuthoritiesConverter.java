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

package org.apache.ranger.security.oidc;

import org.apache.ranger.common.RangerConstants;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RangerJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final Map<String, String> JWT_TO_RANGER_ROLE_MAPPING;

  static {
    Map<String, String> jwtToRangerRoleMapping = new HashMap<>();
    jwtToRangerRoleMapping.put("developer", RangerConstants.ROLE_USER);
    jwtToRangerRoleMapping.put("infrastructure-admin", RangerConstants.ROLE_USER);
    jwtToRangerRoleMapping.put("fabric-manager", RangerConstants.ROLE_SYS_ADMIN);
    JWT_TO_RANGER_ROLE_MAPPING = Collections.unmodifiableMap(jwtToRangerRoleMapping);
  }

  @Override
  public Collection<GrantedAuthority> convert(Jwt source) {
    Object authorities = source.getClaim("userRoles");
    if (!(authorities instanceof Collection)) {
      return Collections.emptySet();
    }
    return ((Collection<String>) authorities).stream()
            .filter(JWT_TO_RANGER_ROLE_MAPPING::containsKey)
            .map(jwtRole -> new SimpleGrantedAuthority(JWT_TO_RANGER_ROLE_MAPPING.get(jwtRole)))
            .collect(Collectors.toSet());
  }

}
