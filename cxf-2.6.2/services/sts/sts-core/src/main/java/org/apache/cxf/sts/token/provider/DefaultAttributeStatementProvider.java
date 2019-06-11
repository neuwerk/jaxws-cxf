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
package org.apache.cxf.sts.token.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;

import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.cxf.ws.security.sts.provider.STSException;
import org.apache.cxf.ws.security.sts.provider.model.secext.UsernameTokenType;
import org.apache.ws.security.SAMLTokenPrincipal;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.saml.ext.AssertionWrapper;
import org.apache.ws.security.saml.ext.bean.AttributeBean;
import org.apache.ws.security.saml.ext.bean.AttributeStatementBean;

/**
 * A default AttributeStatementProvider implementation. It creates a default attribute with
 * value "authenticated". It also shows how to handle OnBehalfOf or ActAs elements by adding an
 * Attribute for them.
 */
public class DefaultAttributeStatementProvider implements AttributeStatementProvider {

    /**
     * Get an AttributeStatementBean using the given parameters.
     */
    public AttributeStatementBean getStatement(TokenProviderParameters providerParameters) {
        AttributeStatementBean attrBean = new AttributeStatementBean();
        List<AttributeBean> attributeList = new ArrayList<AttributeBean>();

        TokenRequirements tokenRequirements = providerParameters.getTokenRequirements();
        String tokenType = tokenRequirements.getTokenType();
        AttributeBean attributeBean = createDefaultAttribute(tokenType);
        attributeList.add(attributeBean);
        
        ReceivedToken onBehalfOf = tokenRequirements.getOnBehalfOf();
        ReceivedToken actAs = tokenRequirements.getActAs();
        try {
            if (onBehalfOf != null) {
                AttributeBean parameterBean = 
                    handleAdditionalParameters(false, onBehalfOf.getToken(), tokenType);
                if (!parameterBean.getAttributeValues().isEmpty()) {
                    attributeList.add(parameterBean);
                }
            }
            if (actAs != null) {
                AttributeBean parameterBean = 
                    handleAdditionalParameters(true, actAs.getToken(), tokenType);
                if (!parameterBean.getAttributeValues().isEmpty()) {
                    attributeList.add(parameterBean);
                }
            }
        } catch (WSSecurityException ex) {
            throw new STSException(ex.getMessage(), ex);
        }
        
        attrBean.setSamlAttributes(attributeList);
        
        return attrBean;
    }
    
    /**
     * Create a default attribute
     */
    private AttributeBean createDefaultAttribute(String tokenType) {
        AttributeBean attributeBean = new AttributeBean();

        if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
            || WSConstants.SAML2_NS.equals(tokenType)) {
            attributeBean.setQualifiedName("token-requestor");
            attributeBean.setNameFormat("http://cxf.apache.org/sts");
        } else {
            attributeBean.setSimpleName("token-requestor");
            attributeBean.setQualifiedName("http://cxf.apache.org/sts");
        }
        
        attributeBean.setAttributeValues(Collections.singletonList("authenticated"));
        
        return attributeBean;
    }

    /**
     * Handle ActAs or OnBehalfOf elements.
     */
    private AttributeBean handleAdditionalParameters(
        boolean actAs, 
        Object parameter, 
        String tokenType
    ) throws WSSecurityException {
        AttributeBean parameterBean = new AttributeBean();

        String claimType = actAs ? "ActAs" : "OnBehalfOf";
        if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType) || WSConstants.SAML2_NS.equals(tokenType)) {
            parameterBean.setQualifiedName(claimType);
            parameterBean.setNameFormat("http://cxf.apache.org/sts");
        } else {
            parameterBean.setSimpleName(claimType);
            parameterBean.setQualifiedName("http://cxf.apache.org/sts");
        }
        if (parameter instanceof UsernameTokenType) {
            parameterBean.setAttributeValues(
                Collections.singletonList(((UsernameTokenType)parameter).getUsername().getValue())
            );
        } else if (parameter instanceof Element) {
            AssertionWrapper wrapper = new AssertionWrapper((Element)parameter);
            SAMLTokenPrincipal principal = new SAMLTokenPrincipal(wrapper);
            parameterBean.setAttributeValues(Collections.singletonList(principal.getName()));
        }

        return parameterBean;
    }


}
