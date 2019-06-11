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
package org.apache.cxf.systest.sts.sendervouches;

import java.net.URL;
import java.security.Principal;
import java.util.List;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.apache.cxf.feature.Features;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.apache.ws.security.handler.WSHandlerResult;
import org.apache.ws.security.util.WSSecurityUtil;

import org.example.contract.doubleit.DoubleItPortType;

@WebService(targetNamespace = "http://www.example.org/contract/DoubleIt", 
            serviceName = "DoubleItService", 
            endpointInterface = "org.example.contract.doubleit.DoubleItPortType")
@Features(features = "org.apache.cxf.feature.LoggingFeature")              
public class DoubleItPortTypeImpl extends AbstractBusClientServerTestBase implements DoubleItPortType {
    
    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");
    
    @Resource
    WebServiceContext wsc;
    
    public int doubleIt(int numberToDouble) {
        // Delegate request to a provider
        URL wsdl = DoubleItPortTypeImpl.class.getResource("DoubleIt.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItTransportSAML2SupportingPort");
        DoubleItPortType transportSAML2SupportingPort = 
            service.getPort(portQName, DoubleItPortType.class);
        try {
            updateAddressPort(transportSAML2SupportingPort, SenderVouchesTest.PORT2);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //
        // Get the principal from the request context and construct a SAML Assertion
        //
        MessageContext context = wsc.getMessageContext();
        final List<WSHandlerResult> handlerResults = 
            CastUtils.cast((List<?>)context.get(WSHandlerConstants.RECV_RESULTS));
        WSSecurityEngineResult actionResult =
            WSSecurityUtil.fetchActionResult(handlerResults.get(0).getResults(), WSConstants.UT);
        Principal principal = 
            (Principal)actionResult.get(WSSecurityEngineResult.TAG_PRINCIPAL);
        
        Saml2CallbackHandler callbackHandler = new Saml2CallbackHandler(principal);
        ((BindingProvider)transportSAML2SupportingPort).getRequestContext().put(
            "ws-security.saml-callback-handler", callbackHandler
        );
        
        return transportSAML2SupportingPort.doubleIt(numberToDouble);
    }
    
}
