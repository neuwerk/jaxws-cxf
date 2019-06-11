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

package org.apache.cxf.systest.ws.wssec10;


import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.systest.ws.wssec10.server.Server;
import org.apache.cxf.systest.ws.wssec11.WSSecurity11Common;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import org.junit.BeforeClass;
import org.junit.Test;

import wssec.wssec10.IPingService;
import wssec.wssec10.PingService;


/**
 *
 */
public class WSSecurity10Test extends AbstractBusClientServerTestBase {
    static final String PORT = allocatePort(Server.class);
    static final String SSL_PORT = allocatePort(Server.class, 1);

    private static final String INPUT = "foo";
    private static boolean unrestrictedPoliciesInstalled;
    
    static {
        unrestrictedPoliciesInstalled = WSSecurity11Common.checkUnrestrictedPoliciesInstalled();
    };    

    @BeforeClass
    public static void startServers() throws Exception {

        assertTrue(
            "Server failed to launch",
            // run the server in the same process
            // set this to false to fork
            launchServer(Server.class, true)
        );
    }
    
    @org.junit.AfterClass
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }

    @Test
    public void testClientServer() {

        String[] argv = new String[] {
            "UserName",
            "UserNameOverTransport",
            "MutualCertificate10SignEncrypt",
            "MutualCertificate10SignEncryptRsa15TripleDes"
        };
        //argv = new String[] {argv[1]};
        Bus bus = null;
        if (unrestrictedPoliciesInstalled) {
            bus = new SpringBusFactory().createBus("org/apache/cxf/systest/ws/wssec10/client/client.xml");
        } else {
            bus = new SpringBusFactory().createBus(
                    "org/apache/cxf/systest/ws/wssec10/client/client_restricted.xml");
        }
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        URL wsdlLocation = null;
        for (String portPrefix : argv) {
            PingService svc = null; 
            wsdlLocation = getWsdlLocation(portPrefix); 
            svc = new PingService(wsdlLocation);
            final IPingService port = 
                svc.getPort(
                    new QName(
                        "http://WSSec/wssec10",
                        portPrefix + "_IPingService"
                    ),
                    IPingService.class
                );
         
            Client cl = ClientProxy.getClient(port);
            
            HTTPConduit http = (HTTPConduit) cl.getConduit();
             
            HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
            httpClientPolicy.setConnectionTimeout(0);
            httpClientPolicy.setReceiveTimeout(0);
             
            http.setClient(httpClientPolicy);
            final String output = port.echo(INPUT);
            assertEquals(INPUT, output);
        }
        
        bus.shutdown(true);
    }
    
    private static URL getWsdlLocation(String portPrefix) {
        try {
            if ("UserNameOverTransport".equals(portPrefix)) {
                return new URL("https://localhost:" + SSL_PORT + "/" + portPrefix + "?wsdl");
            } else if ("UserName".equals(portPrefix)) {
                return new URL("http://localhost:" + PORT + "/" + portPrefix + "?wsdl");
            } else if ("MutualCertificate10SignEncrypt".equals(portPrefix)) {
                return new URL("http://localhost:" + PORT + "/" + portPrefix + "?wsdl");
            } else if ("MutualCertificate10SignEncryptRsa15TripleDes".equals(portPrefix)) {
                return new URL("http://localhost:" + PORT + "/" + portPrefix + "?wsdl");
            }
        } catch (MalformedURLException mue) {
            return null;
        }
        return null;
    }

    
}
