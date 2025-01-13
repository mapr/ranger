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

import org.apache.ranger.security.oidc.ClientRegistrationRepositoryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

/**
 * Instead of performing RP-initiated logout like
 * {@link org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler} does
 * by redirecting User Agent to OP's end_session_endpoint, this logout success handler sends
 * the logout request to OP itself without involving User Agent.
 *
 * The only reason for doing so is to avoid CORS policies check, with which there is a problem when using Keycloak.
 *
 * After performing an OIDC logout, this class delegates the work to a provided implementation.
 */
public class CustomOidcLogoutSuccessHandler implements LogoutSuccessHandler {

  private final String clientId;
  private final URI endSessionEndpoint;
  private final LogoutSuccessHandler delegateLogoutSuccessHandler;

  public CustomOidcLogoutSuccessHandler(@Autowired ClientRegistrationRepository clientRegistrationRepository,
                                        LogoutSuccessHandler delegateLogoutSuccessHandler) {
    ClientRegistration clientRegistration = clientRegistrationRepository
            .findByRegistrationId(ClientRegistrationRepositoryFactory.REGISTRATION_ID);
    this.clientId = clientRegistration.getClientId();
    this.endSessionEndpoint = URI.create((String) clientRegistration.getProviderDetails()
            .getConfigurationMetadata().get("end_session_endpoint"));
    this.delegateLogoutSuccessHandler = delegateLogoutSuccessHandler;
  }

  @Override
  public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
          throws IOException, ServletException {
    oidcLogout(authentication);
    delegateLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);
  }

  private void oidcLogout(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof OidcUser)) {
      return;
    }

    String idToken = ((OidcUser)authentication.getPrincipal()).getIdToken().getTokenValue();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("id_token_hint", idToken);
    formData.add("client_id", clientId);
    RequestEntity<MultiValueMap<String, String>> requestEntity =
            RequestEntity.post(endSessionEndpoint).headers(headers).body(formData);

    ResponseEntity<String> responseEntity = new RestTemplate().exchange(requestEntity, String.class);
    if (responseEntity.getStatusCode() != HttpStatus.OK) {
      throw new RuntimeException(String.format("Failed to log user out of OP: %s %s",
              responseEntity.getStatusCode(), responseEntity.getBody()));
    }
  }
}
