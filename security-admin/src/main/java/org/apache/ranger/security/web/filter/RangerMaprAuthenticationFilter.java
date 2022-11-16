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

package org.apache.ranger.security.web.filter;

import com.mapr.security.maprauth.MaprAuthenticationHandler;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.util.MaprSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Provides an authentication of MapR SASL type (via mapr ticket), if it is enabled and corresponding header is set
 * (MAPR-Negotiate). The authentication itself is perform via
 * com.mapr.security.maprauth.MaprAuthenticationHandler#maprAuthenticate(
 * javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse).
 * This filter takes token from mapr authentication and if it is valid (not null) sets the result to spring
 * security context (spring security filter chain doesn't pass the request if authentication#isAuthenticated() == false,
 * so if MapR SASL auth passed successfully we need to let spring know about it.
 * <p>
 * Mostly this class was written with RangerKRBAuthenticationFilter as an example/template because MapR SASL auth
 * should be an alternative of Kerberos auth. So please refer to kerberos filter to get more insights how it works or
 * should work.
 */
public class RangerMaprAuthenticationFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(RangerMaprAuthenticationFilter.class);
    private final MaprAuthenticationHandler maprAuthHandler = new MaprAuthenticationHandler();

    private String securityTYpe = PropertiesUtil.getProperty(MaprSecurity.SECURITY_TYPE_PROPERTY);

    public RangerMaprAuthenticationFilter() {
        try {
            // for some reason spring does not call Filter#init() on startup, so call it in constructor
            // see RangerKRBAuthenticationFilter's constructor
            init(null);
        } catch (ServletException e) {
            LOG.error("Error while initializing Filter : " + e.getMessage());
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.maprAuthHandler.init(null);
    }

    /**
     * if MapR SASL auth is disabled, do nothing and go to the next filter in the chain
     * <p>
     * authentication is considered succeeded only if MAPR-Negotiate header is provided and MaprAuthenticationHandler
     * returned valid (not null) token
     *
     * @param request
     * @param response
     * @param chain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (MaprSecurity.MAPR_SASL.equals(securityTYpe)) {
            try {
                AuthenticationToken token = maprAuthHandler.postauthenticate(
                        (HttpServletRequest) request, (HttpServletResponse) response);
                if (token != null) {
                    List<GrantedAuthority> grantedAuths = Collections.emptyList();
                    final UserDetails principal = new User(token.getUserName(), "", grantedAuths);
                    //this constructor sets isAuthenticated to true
                    Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "", grantedAuths);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (AuthenticationException e) {
                LOG.error("MapR SASL Authentication failed with exception", e);
            }
            chain.doFilter(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }
}
