/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.oauth2.common;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a login name which AuthorizationService
 * may capture after the end user approved a given third party request
 */
@XmlRootElement
public class UserSubject {
    
    private String login;
    private List<String> roles = Collections.emptyList();
    
    public UserSubject() {
        
    }
    
    public UserSubject(String login) {
        this.login = login;
    }
    
    public UserSubject(String login, List<String> roles) {
        this.login = login;
        this.roles = roles;
    }
    
    /**
     * Return the user login name
     * @return the login name
     */
    public String getLogin() {
        return login;
    }
    
    /**
     * Set the user login name
     * @param login the login name
     */
    public void setLogin(String login) {
        this.login = login;
    }

    /**
     * Return the optional list of user roles which may have 
     * been captured during the authentication process 
     * @return the list of roles
     */
    public List<String> getRoles() {
        return roles;
    }
    
    /**
     * Set the optional list of user roles which may have 
     * been captured during the authentication process 
     * @param roles the list of roles
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    

}
