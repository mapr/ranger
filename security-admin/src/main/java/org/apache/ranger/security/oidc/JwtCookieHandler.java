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

import org.apache.hadoop.security.authentication.util.SsoConfigurationUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A single class implementing 2 interfaces to handle JWT in cookies:
 * 1. {@link BearerTokenResolver}: if given delegate implementation hasn't resolved a token,
 * tries to resolve it from cookie
 * 2. {@link AuthenticationSuccessHandler}: if authentication method was OIDC, sets the JWT cookie,
 * then proceeds to given delegate implementation
 * Cookie name, path and domain are configured by hadoop (see {@link SsoConfigurationUtil}.
 */
public class JwtCookieHandler implements BearerTokenResolver, AuthenticationSuccessHandler {

  private final BearerTokenResolver delegateBearerTokenResolver;
  private final AuthenticationSuccessHandler delegateAuthenticationSuccessHandler;

  public JwtCookieHandler(BearerTokenResolver delegateBearerTokenResolver,
                          AuthenticationSuccessHandler delegateAuthenticationSuccessHandler) {
    this.delegateBearerTokenResolver = delegateBearerTokenResolver;
    this.delegateAuthenticationSuccessHandler = delegateAuthenticationSuccessHandler;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    String tokenFromDelegate = delegateBearerTokenResolver.resolve(request);
    if (tokenFromDelegate != null) {
      return tokenFromDelegate;
    }
    Cookie cookie = WebUtils.getCookie(request, SsoConfigurationUtil.getInstance().getCookieName());
    if (cookie != null) {
      return cookie.getValue();
    }
    return null;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
    if (authentication.getPrincipal() instanceof  OidcUser) {
      OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
      String idToken = oidcUser.getIdToken().getTokenValue();
      SsoConfigurationUtil sso = SsoConfigurationUtil.getInstance();
      Cookie cookie = new Cookie(sso.getCookieName(), idToken);
      cookie.setPath(sso.getCookiePath());
      cookie.setDomain(sso.getCookieDomain());
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      response.addCookie(cookie);
    }
    delegateAuthenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
  }
}
