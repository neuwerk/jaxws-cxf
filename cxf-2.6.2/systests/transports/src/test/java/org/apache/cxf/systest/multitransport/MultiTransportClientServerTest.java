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

package org.apache.cxf.systest.multitransport;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.wsdl.Definition;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;
import org.apache.cxf.testutil.common.EmbeddedJMSBrokerLauncher;
import org.apache.hello_world_doc_lit.Greeter;
import org.apache.hello_world_doc_lit.HTTPGreeterImpl;
import org.apache.hello_world_doc_lit.JMSGreeterImpl;
import org.apache.hello_world_doc_lit.MultiTransportService;
import org.apache.hello_world_doc_lit.PingMeFault;
import org.junit.BeforeClass;
import org.junit.Test;

public class MultiTransportClientServerTest extends AbstractBusClientServerTestBase {
    static final String JMS_PORT = EmbeddedJMSBrokerLauncher.PORT;
    static final String PORT = allocatePort(MyServer.class);
    static final Logger LOG = LogUtils.getLogger(MultiTransportClientServerTest.class);
    private final QName serviceName = new QName(
                                      "http://apache.org/hello_world_doc_lit",
                                                "MultiTransportService");

    public static class MyServer extends AbstractBusTestServerBase {
        Definition def;
        Endpoint ep1;
        Endpoint ep2;
        protected void run() {
            Object implementor = new HTTPGreeterImpl();
            String address = "http://localhost:" + PORT + "/SOAPDocLitService/SoapPort";
            ep1 = Endpoint.publish(address, implementor);
            EmbeddedJMSBrokerLauncher.updateWsdlExtensors(getBus(),
                                                          "testutils/hello_world_doc_lit.wsdl");
            implementor = new JMSGreeterImpl();
            ep2 = Endpoint.publish(null, implementor);
        }
        public void tearDown() {
            if (ep1 != null) {
                ep1.stop();
            }
            if (ep2 != null) {
                ep2.stop();
            }
        }

        public static void main(String[] args) {
            try {
                MyServer s = new MyServer();
                s.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(-1);
            } finally {
                LOG.info("done!");
            }
        }
    }

    @BeforeClass
    public static void startServers() throws Exception {
        Map<String, String> props = new HashMap<String, String>();                
        if (System.getProperty("org.apache.activemq.default.directory.prefix") != null) {
            props.put("org.apache.activemq.default.directory.prefix", 
                      System.getProperty("org.apache.activemq.default.directory.prefix"));
        }
        //props.put("java.util.logging.config.file", 
        //          System.getProperty("java.util.logging.config.file"));
        
        assertTrue("server did not launch correctly", 
                   launchServer(EmbeddedJMSBrokerLauncher.class, props, null));

        assertTrue("server did not launch correctly", launchServer(MyServer.class, true));
        createStaticBus();
    }
    
    // the purpose of this test shows how one service include two ports with different
    // transport work
    @Test
    public void testMultiTransportInOneService() throws Exception {
        
        QName portName1 = new QName("http://apache.org/hello_world_doc_lit", "HttpPort");
        QName portName2 = new QName("http://apache.org/hello_world_doc_lit", "JMSPort");
        URL wsdl = getClass().getResource("/wsdl/hello_world_doc_lit.wsdl");
        assertNotNull(wsdl);
        String wsdlString = wsdl.toString();
        MultiTransportService service = new MultiTransportService(wsdl, serviceName);
        assertNotNull(service);
        EmbeddedJMSBrokerLauncher.updateWsdlExtensors(getBus(), wsdlString);
        
        
        String response1 = new String("Hello Milestone-");
        String response2 = new String("Bonjour");
        try {
            Greeter greeter = service.getPort(portName1, Greeter.class);
            updateAddressPort(greeter, PORT);
            for (int idx = 0; idx < 5; idx++) {
                String greeting = greeter.greetMe("Milestone-" + idx);
                assertNotNull("no response received from service", greeting);
                String exResponse = response1 + idx;
                assertEquals(exResponse, greeting);

                String reply = greeter.sayHi();
                assertNotNull("no response received from service", reply);
                assertEquals(response2, reply);
                
                try {
                    greeter.pingMe();
                    fail("Should have thrown FaultException");
                } catch (PingMeFault ex) {
                    assertNotNull(ex.getFaultInfo());
                }                
              
            }
            
            greeter = null;
            
            greeter = service.getPort(portName2, Greeter.class);
            for (int idx = 0; idx < 5; idx++) {
                String greeting = greeter.greetMe("Milestone-" + idx);
                assertNotNull("no response received from service", greeting);
                String exResponse = response1 + idx;
                assertEquals(exResponse, greeting);

                String reply = greeter.sayHi();
                assertNotNull("no response received from service", reply);
                assertEquals(response2, reply);
                
                try {
                    greeter.pingMe();
                    fail("Should have thrown FaultException");
                } catch (PingMeFault ex) {
                    assertNotNull(ex.getFaultInfo());
                }                
              
            }
        } catch (UndeclaredThrowableException ex) {
            throw (Exception)ex.getCause();
        }
    }


}
