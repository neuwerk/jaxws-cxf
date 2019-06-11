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
package org.apache.cxf.transport.http_jetty;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.classloader.ClassLoaderUtils.ClassLoaderHolder;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.CertificateConstraintsType;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.continuations.SuspendedInvocationException;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HTTPSession;
import org.apache.cxf.transport.http_jetty.continuations.JettyContinuationProvider;
import org.apache.cxf.transport.https.CertConstraintsJaxBUtils;
import org.apache.cxf.transports.http.QueryHandler;
import org.apache.cxf.transports.http.QueryHandlerRegistry;
import org.apache.cxf.transports.http.StemMatchingQueryHandler;
import org.eclipse.jetty.http.Generator;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.server.Request;
import org.springframework.util.ClassUtils;

public class JettyHTTPDestination extends AbstractHTTPDestination {
    
    private static final Logger LOG =
        LogUtils.getL7dLogger(JettyHTTPDestination.class);

    protected JettyHTTPServerEngine engine;
    protected JettyHTTPServerEngineFactory serverEngineFactory;
    protected ServletContext servletContext;
    protected URL nurl;
    protected ClassLoader loader;
    
    /**
     * This variable signifies that finalizeConfig() has been called.
     * It gets called after this object has been spring configured.
     * It is used to automatically reinitialize things when resources
     * are reset, such as setTlsServerParameters().
     */
    private boolean configFinalized;
     
    /**
     * Constructor, using Jetty server engine.
     * 
     * @param b the associated Bus
     * @param ci the associated conduit initiator
     * @param endpointInfo the endpoint info of the destination
     * @param serverEngineFactory 
     * @throws IOException
     */
    public JettyHTTPDestination(
            Bus bus,
            DestinationRegistry registry, 
            EndpointInfo ei, 
            JettyHTTPServerEngineFactory serverEngineFactory
    ) throws IOException {
        //Add the defualt port if the address is missing it
        super(bus, registry, ei, getAddressValue(ei, true).getAddress(), true);
        this.serverEngineFactory = serverEngineFactory;
        nurl = new URL(endpointInfo.getAddress());
        loader = bus.getExtension(ClassLoader.class);
    }

    protected Logger getLogger() {
        return LOG;
    }
    
    public void setServletContext(ServletContext sc) {
        servletContext = sc;
    }
    
    /**
     * Post-configure retreival of server engine.
     */
    protected void retrieveEngine()
        throws GeneralSecurityException, 
               IOException {
        
        engine = 
            serverEngineFactory.retrieveJettyHTTPServerEngine(nurl.getPort());
        if (engine == null) {
            engine = serverEngineFactory.
                createJettyHTTPServerEngine(nurl.getHost(), nurl.getPort(), nurl.getProtocol());
        }

        assert engine != null;
        TLSServerParameters serverParameters = engine.getTlsServerParameters();
        if (serverParameters != null && serverParameters.getCertConstraints() != null) {
            CertificateConstraintsType constraints = serverParameters.getCertConstraints();
            if (constraints != null) {
                certConstraints = CertConstraintsJaxBUtils.createCertConstraints(constraints);
            }
        }
        
        // When configuring for "http", however, it is still possible that
        // Spring configuration has configured the port for https. 
        if (!nurl.getProtocol().equals(engine.getProtocol())) {
            throw new IllegalStateException(
                "Port " + engine.getPort() 
                + " is configured with wrong protocol \"" 
                + engine.getProtocol()
                + "\" for \"" + nurl + "\"");
        }
    }
    
    /**
     * This method is used to finalize the configuration
     * after the configuration items have been set.
     *
     */
    public void finalizeConfig() {
        assert !configFinalized;
        
        try {
            retrieveEngine();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        configFinalized = true;
    }
    
    /**
     * Activate receipt of incoming messages.
     */
    protected void activate() {
        super.activate();
        LOG.log(Level.FINE, "Activating receipt of incoming messages");
        URL url = null;
        try {
            url = new URL(endpointInfo.getAddress());
        } catch (Exception e) {
            throw new Fault(e);
        }
        engine.addServant(url, 
                          new JettyHTTPHandler(this, contextMatchOnExact()));
    }

    /**
     * Deactivate receipt of incoming messages.
     */
    protected void deactivate() {
        super.deactivate();
        LOG.log(Level.FINE, "Deactivating receipt of incoming messages");
        engine.removeServant(nurl);   
    }   
     

    
    protected String getBasePathForFullAddress(String addr) {
        try {
            return new URL(addr).getPath();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private String removeTrailingSeparator(String addr) {
        if (addr != null && addr.length() > 0 
            && addr.lastIndexOf('/') == addr.length() - 1) {
            return addr.substring(0, addr.length() - 1);
        } else {
            return addr;
        }
    }
    
    private synchronized String updateEndpointAddress(String addr) {
        // only update the EndpointAddress if the base path is equal
        // make sure we don't broke the get operation?parament query 
        String address = removeTrailingSeparator(endpointInfo.getAddress());
        if (getBasePathForFullAddress(address)
            .equals(removeTrailingSeparator(getStem(getBasePathForFullAddress(addr))))) {
            endpointInfo.setAddress(addr);
        }
        return address;
    }
   
    protected void doService(HttpServletRequest req,
                             HttpServletResponse resp) throws IOException {
        doService(servletContext, req, resp);
    }
    
    static AbstractConnection getConnectionForRequest(Request r) {
        try {
            return (AbstractConnection)r.getClass().getMethod("getConnection").invoke(r);
        } catch (Exception ex) {
            return null;
        }
    }
    
    private void setHeadFalse(AbstractConnection con) {
        try {
            Generator gen = (Generator)con.getClass().getMethod("getGenerator").invoke(con);
            gen.setHead(false);
        } catch (Exception ex) {
            //ignore - can continue
        }
    }
    
    protected void doService(ServletContext context,
                             HttpServletRequest req,
                             HttpServletResponse resp) throws IOException {
        if (context == null) {
            context = servletContext;
        }
        Request baseRequest = (req instanceof Request) 
            ? (Request)req : getCurrentRequest();
            
        if (!"HEAD".equals(req.getMethod())) {
            //bug in Jetty with persistent connections that if a HEAD is
            //sent, a _head flag is never reset
            AbstractConnection c = getConnectionForRequest(baseRequest);
            if (c != null) {
                setHeadFalse(c);
            }
        }
        if (getServer().isSetRedirectURL()) {
            resp.sendRedirect(getServer().getRedirectURL());
            resp.flushBuffer();
            baseRequest.setHandled(true);
            return;
        }
        QueryHandlerRegistry queryHandlerRegistry = bus.getExtension(QueryHandlerRegistry.class);
        
        if (null != req.getQueryString() && queryHandlerRegistry != null) {   
            String reqAddr = req.getRequestURL().toString();
            String requestURL =  reqAddr + "?" + req.getQueryString();
            String pathInfo = req.getPathInfo();                     
            for (QueryHandler qh : queryHandlerRegistry.getHandlers()) {
                boolean recognized =
                    qh instanceof StemMatchingQueryHandler
                    ? ((StemMatchingQueryHandler)qh).isRecognizedQuery(requestURL,
                                                                       pathInfo,
                                                                       endpointInfo,
                                                                       contextMatchOnExact())
                    : qh.isRecognizedQuery(requestURL, pathInfo, endpointInfo);
                if (recognized) {
                    //replace the endpointInfo address with request url only for get wsdl
                    String errorMsg = null;
                    CachedOutputStream out = new CachedOutputStream();
                    try {
                        synchronized (endpointInfo) {
                            String oldAddress = updateEndpointAddress(reqAddr);   
                            resp.setContentType(qh.getResponseContentType(requestURL, pathInfo));
                            try {
                                qh.writeResponse(requestURL, pathInfo, endpointInfo, out);
                            } catch (Exception ex) {
                                LOG.log(Level.WARNING, "writeResponse failed: ", ex);
                                errorMsg = ex.getMessage();
                            }
                            endpointInfo.setAddress(oldAddress);
                        }
                        if (errorMsg != null) {
                            resp.sendError(500, errorMsg);
                        } else {
                            out.writeCacheTo(resp.getOutputStream());
                            resp.getOutputStream().flush();                     
                        }
                    } finally {
                        out.close();
                    }
                    baseRequest.setHandled(true);
                    return;
                }
            }
        }

        // REVISIT: service on executor if associated with endpoint
        ClassLoaderHolder origLoader = null;
        Bus origBus = BusFactory.getAndSetThreadDefaultBus(bus);
        try {
            if (loader != null) {
                origLoader = ClassLoaderUtils.setThreadContextClassloader(loader);
            }
            serviceRequest(context, req, resp);
        } finally {
            if (origBus != bus) {
                BusFactory.setThreadDefaultBus(origBus);
            }
            if (origLoader != null) { 
                origLoader.reset();
            }
        }    
    }

    protected void serviceRequest(final ServletContext context, 
                                  final HttpServletRequest req, 
                                  final HttpServletResponse resp)
        throws IOException {
        Request baseRequest = (req instanceof Request) 
            ? (Request)req : getCurrentRequest();
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Service http request on thread: " + Thread.currentThread());
        }
        Message inMessage = retrieveFromContinuation(req);
        
        if (inMessage == null) {
            
            inMessage = new MessageImpl();
            setupMessage(inMessage, context, req, resp);
            
            ((MessageImpl)inMessage).setDestination(this);
    
            ExchangeImpl exchange = new ExchangeImpl();
            exchange.setInMessage(inMessage);
            exchange.setSession(new HTTPSession(req));
        }
        
        try {    
            incomingObserver.onMessage(inMessage);
            resp.flushBuffer();
            baseRequest.setHandled(true);
        } catch (SuspendedInvocationException ex) {
            if (ex.getRuntimeException() != null) {
                throw ex.getRuntimeException();
            }
            //else nothing to do
        } catch (Fault ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else {
                throw ex;
            }
        } catch (RuntimeException ex) {
            throw ex;
        } finally {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Finished servicing http request on thread: " + Thread.currentThread());
            }
        }
    }
 
    public ServerEngine getEngine() {
        return engine;
    }
   
    private String getStem(String baseURI) {    
        return baseURI.substring(0, baseURI.lastIndexOf("/"));
    }
    
    protected Message retrieveFromContinuation(HttpServletRequest req) {
        return (Message)req.getAttribute(CXF_CONTINUATION_MESSAGE);
    }
    protected void setupContinuation(Message inMessage,
                      final HttpServletRequest req, 
                      final HttpServletResponse resp) {
        if (engine.getContinuationsEnabled()) {
            inMessage.put(ContinuationProvider.class.getName(), 
                      new JettyContinuationProvider(req, resp, inMessage));
        }
    }
    
    private AbstractConnection getCurrentConnection() {
        // AbstractHttpConnection on Jetty 7.6, HttpConnection on Jetty <=7.5
        Class<?> cls = null;
        try {
            cls = ClassUtils.forName("org.eclipse.jetty.server.AbstractHttpConnection",
                                     AbstractConnection.class.getClassLoader());
        } catch (Exception e) {
            //ignore
        }
        if (cls == null) {
            try {
                cls = ClassUtils.forName("org.eclipse.jetty.server.HttpConnection",
                                         AbstractConnection.class.getClassLoader());
            } catch (Exception e) {
                //ignore
            }
        }

        try {
            return (AbstractConnection)ReflectionUtil
                .setAccessible(cls.getMethod("getCurrentConnection")).invoke(null);
        } catch (Exception e) {
            //ignore
        }
        return null;
    }
    private Request getCurrentRequest() {
        AbstractConnection con = getCurrentConnection();
        try {
            return (Request)ReflectionUtil
                .setAccessible(con.getClass().getMethod("getRequest")).invoke(con);
        } catch (Exception e) {
            //ignore
        }
        return null;
    }
}
