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

package org.apache.cxf.systest.jaxrs.security;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.systest.jaxrs.Book;
import org.apache.cxf.systest.jaxrs.BookStore;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;

import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;


public class JAXRSHttpsBookTest extends AbstractBusClientServerTestBase {
    public static final String PORT = BookHttpsServer.PORT;

    private static final String CLIENT_CONFIG_FILE1 =
        "org/apache/cxf/systest/jaxrs/security/jaxrs-https-client1.xml";
    private static final String CLIENT_CONFIG_FILE2 =
        "org/apache/cxf/systest/jaxrs/security/jaxrs-https-client2.xml";
    private static final String CLIENT_CONFIG_FILE3 =
        "org/apache/cxf/systest/jaxrs/security/jaxrs-https-client3.xml";
    private static final String CLIENT_CONFIG_FILE4 =
        "org/apache/cxf/systest/jaxrs/security/jaxrs-https-client4.xml";    
    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly",
                   launchServer(BookHttpsServer.class, true));
    }
    
    @Test
    public void testGetBook123Proxy() throws Exception {
        doTestGetBook123Proxy(CLIENT_CONFIG_FILE1);
    }
    
    @Test
    public void testGetBook123ProxyWithURLConduitId() throws Exception {
        doTestGetBook123Proxy(CLIENT_CONFIG_FILE2);
    }
    
    private void doTestGetBook123Proxy(String configFile) throws Exception {
        BookStore bs = JAXRSClientFactory.create("https://localhost:" + PORT, BookStore.class, 
                configFile);
        // just to verify the interface call goes through CGLIB proxy too
        assertEquals("https://localhost:" + PORT, WebClient.client(bs).getBaseURI().toString());
        Book b = bs.getSecureBook("123");
        assertEquals(b.getId(), 123);
        b = bs.getSecureBook("123");
        assertEquals(b.getId(), 123);
    }
    
    @Test
    public void testGetBook123ProxyFromSpring() throws Exception {
        doTestGetBook123ProxyFromSpring(CLIENT_CONFIG_FILE3);
    }
    @Test
    public void testGetBook123ProxyFromSpringWildcard() throws Exception {
        doTestGetBook123ProxyFromSpring(CLIENT_CONFIG_FILE4);
    }
    
    private void doTestGetBook123ProxyFromSpring(String cfgFile) throws Exception {
        
        ClassPathXmlApplicationContext ctx =
            new ClassPathXmlApplicationContext(new String[] {cfgFile});
        Object bean = ctx.getBean("bookService.proxyFactory");
        assertNotNull(bean);
        JAXRSClientFactoryBean cfb = (JAXRSClientFactoryBean) bean;
        
        BookStore bs = cfb.create(BookStore.class);
        assertEquals("https://localhost:" + PORT, WebClient.client(bs).getBaseURI().toString());
        Book b = bs.getSecureBook("123");
        assertEquals(b.getId(), 123);
        b = bs.getSecureBook("123");
        assertEquals(b.getId(), 123);
    }
    
    @Test
    public void testGetBook123ProxyToWebClient() throws Exception {
        
        BookStore bs = JAXRSClientFactory.create("https://localhost:" + PORT, BookStore.class, 
                                                 CLIENT_CONFIG_FILE1);
        Book b = bs.getSecureBook("123");
        assertEquals(b.getId(), 123);
        WebClient wc = WebClient.fromClient(WebClient.client(bs));
        wc.path("/bookstore/securebooks/123").accept(MediaType.APPLICATION_XML_TYPE);
        Book b2 = wc.get(Book.class);
        assertEquals(123, b2.getId());
    }
    
    
    @Test
    public void testGetBook123WebClientToProxy() throws Exception {
        
        WebClient wc = WebClient.create("https://localhost:" + PORT, CLIENT_CONFIG_FILE1);
        wc.path("/bookstore/securebooks/123").accept(MediaType.APPLICATION_XML_TYPE);
        Book b = wc.get(Book.class);
        assertEquals(123, b.getId());
        
        wc.back(true);
        
        BookStore bs = JAXRSClientFactory.fromClient(wc, BookStore.class);
        Book b2 = bs.getSecureBook("123");
        assertEquals(b2.getId(), 123);
        
    }
    
    
    @Test
    public void testGetBook123WebClient() throws Exception {
        doTestGetBook123WebClient(CLIENT_CONFIG_FILE1);
    }
    
    @Test
    public void testGetBook123WebClientWithURLConduitId() throws Exception {
        doTestGetBook123WebClient(CLIENT_CONFIG_FILE2);
    }
    
    private void doTestGetBook123WebClient(String configFile) throws Exception {
        WebClient client = WebClient.create("https://localhost:" + PORT, configFile);
        assertEquals("https://localhost:" + PORT, client.getBaseURI().toString());
        
        client.path("/bookstore/securebooks/123").accept(MediaType.APPLICATION_XML_TYPE);
        Book b = client.get(Book.class);
        assertEquals(123, b.getId());
    }     
    
}
