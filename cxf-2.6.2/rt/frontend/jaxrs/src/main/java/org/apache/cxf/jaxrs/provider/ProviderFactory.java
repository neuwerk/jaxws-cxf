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

package org.apache.cxf.jaxrs.provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxrs.client.ResponseExceptionMapper;
import org.apache.cxf.jaxrs.ext.ContextProvider;
import org.apache.cxf.jaxrs.ext.ParameterHandler;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.ext.ResponseHandler;
import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.jaxrs.impl.RequestPreprocessor;
import org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.wadl.WadlGenerator;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;

public final class ProviderFactory {
    private static final String ACTIVE_JAXRS_PROVIDER_KEY = "active.jaxrs.provider";
    private static final Logger LOG = LogUtils.getL7dLogger(ProviderFactory.class);
    private static final ProviderFactory SHARED_FACTORY = getInstance();
    
    private static final String JAXB_PROVIDER_NAME = "org.apache.cxf.jaxrs.provider.JAXBElementProvider";
    private static final String JSON_PROVIDER_NAME = "org.apache.cxf.jaxrs.provider.json.JSONProvider";
    
    static {
        SHARED_FACTORY.setProviders(new BinaryDataProvider<Object>(),
                                    new SourceProvider<Object>(),
                                    new DataSourceProvider<Object>(),
                                    new FormEncodingProvider<Object>(),
                                    new PrimitiveTextProvider<Object>(),
                                    new MultipartProvider(),
                                    new WebApplicationExceptionMapper(),
                                    new WadlGenerator());
    }
    
    private List<ProviderInfo<MessageBodyReader<?>>> messageReaders = 
        new ArrayList<ProviderInfo<MessageBodyReader<?>>>();
    private List<ProviderInfo<MessageBodyWriter<?>>> messageWriters = 
        new ArrayList<ProviderInfo<MessageBodyWriter<?>>>();
    private List<ProviderInfo<ContextResolver<?>>> contextResolvers = 
        new ArrayList<ProviderInfo<ContextResolver<?>>>(1);
    private List<ProviderInfo<ContextProvider<?>>> contextProviders = 
        new ArrayList<ProviderInfo<ContextProvider<?>>>(1);
    private List<ProviderInfo<ExceptionMapper<?>>> exceptionMappers = 
        new ArrayList<ProviderInfo<ExceptionMapper<?>>>(1);
    private List<ProviderInfo<RequestHandler>> requestHandlers = 
        new ArrayList<ProviderInfo<RequestHandler>>(1);
    private List<ProviderInfo<ResponseHandler>> responseHandlers = 
        new ArrayList<ProviderInfo<ResponseHandler>>(1);
    private List<ProviderInfo<ParameterHandler<?>>> paramHandlers = 
        new ArrayList<ProviderInfo<ParameterHandler<?>>>(1);
    private List<ProviderInfo<ResponseExceptionMapper<?>>> responseExceptionMappers = 
        new ArrayList<ProviderInfo<ResponseExceptionMapper<?>>>(1);
    private RequestPreprocessor requestPreprocessor;
    private ProviderInfo<Application> application;
    
    private List<ProviderInfo<MessageBodyReader<?>>> jaxbReaders = 
        new ArrayList<ProviderInfo<MessageBodyReader<?>>>();
    private List<ProviderInfo<MessageBodyWriter<?>>> jaxbWriters = 
        new ArrayList<ProviderInfo<MessageBodyWriter<?>>>();
    
    private Bus bus;
    
    private ProviderFactory(Bus bus) {
        this.bus = bus;
        initJaxbProviders();
    }
    
    // Not ideal but in the end seems like the simplest option compared 
    // to adding default readers/writers to existing messageReaders/Writers 
    // (due to all sort of conflicts with custom providers) and cloning 
    // at the request time
    private void initJaxbProviders() {
        Object jaxbProvider = createProvider(JAXB_PROVIDER_NAME);
        if (jaxbProvider != null) {
            jaxbReaders.add(new ProviderInfo<MessageBodyReader<?>>((MessageBodyReader<?>)jaxbProvider, bus));
            jaxbWriters.add(new ProviderInfo<MessageBodyWriter<?>>((MessageBodyWriter<?>)jaxbProvider, bus));
        }
        Object jsonProvider = createProvider(JSON_PROVIDER_NAME);
        if (jsonProvider != null) {
            jaxbReaders.add(new ProviderInfo<MessageBodyReader<?>>((MessageBodyReader<?>)jsonProvider, bus));
            jaxbWriters.add(new ProviderInfo<MessageBodyWriter<?>>((MessageBodyWriter<?>)jsonProvider, bus));
        }
        injectContextProxies(jaxbReaders, jaxbWriters);
    }
    
    
    
    private static Object createProvider(String className) {
        
        try {
            return ClassLoaderUtils.loadClass(className, ProviderFactory.class).newInstance();
        } catch (Throwable ex) {
            String message = "Problem with creating the default provider " + className;
            if (ex.getMessage() != null) {
                message += ": " + ex.getMessage();
            } else {
                message += ", exception class : " + ex.getClass().getName();  
            }
            LOG.fine(message);
        }
        return null;
    }
    
    public static ProviderFactory getInstance() {
        return new ProviderFactory(BusFactory.getThreadDefaultBus());
    }
    
    public static ProviderFactory getInstance(Bus bus) {
        return new ProviderFactory(bus);
    }
    
    public static ProviderFactory getInstance(Message m) {
        Endpoint e = m.getExchange().get(Endpoint.class);
        return (ProviderFactory)e.get(ProviderFactory.class.getName());
    }
    
    public static ProviderFactory getSharedInstance() {
        return SHARED_FACTORY;
    }
    
    public <T> ContextResolver<T> createContextResolver(Type contextType, 
                                                        Message m) {
        boolean isRequestor = MessageUtils.isRequestor(m);
        Message requestMessage = isRequestor ? m.getExchange().getOutMessage() 
                                             : m.getExchange().getInMessage();
        HttpHeaders requestHeaders = new HttpHeadersImpl(requestMessage);
        MediaType mt = null;
        
        Message responseMessage = isRequestor ? m.getExchange().getInMessage() 
                                              : m.getExchange().getOutMessage();
        if (responseMessage != null) {
            if (!responseMessage.containsKey(Message.CONTENT_TYPE)) {
                List<MediaType> accepts = requestHeaders.getAcceptableMediaTypes();
                if (accepts.size() > 0) {
                    mt = accepts.get(0);
                }
            } else {
                mt = MediaType.valueOf(responseMessage.get(Message.CONTENT_TYPE).toString());
            }
        } else {
            mt = requestHeaders.getMediaType();
        }
        
        return createContextResolver(contextType, m,
               mt == null ? MediaType.WILDCARD_TYPE : mt);
        
    }
    
    @SuppressWarnings("unchecked")
    public <T> ContextResolver<T> createContextResolver(Type contextType, 
                                                        Message m,
                                                        MediaType type) {
        Class<?> contextCls = InjectionUtils.getActualType(contextType);
        if (contextCls == null) {
            return null;
        }
        List<ContextResolver<T>> candidates = new LinkedList<ContextResolver<T>>();
        for (ProviderInfo<ContextResolver<?>> cr : contextResolvers) {
            Type[] types = cr.getProvider().getClass().getGenericInterfaces();
            for (Type t : types) {
                if (t instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType)t;
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > 0) {
                        Class<?> argCls = InjectionUtils.getActualType(args[0]);
                        
                        if (argCls != null && argCls.isAssignableFrom(contextCls)) {
                            List<MediaType> mTypes = JAXRSUtils.getProduceTypes(
                                 cr.getProvider().getClass().getAnnotation(Produces.class));
                            if (JAXRSUtils.intersectMimeTypes(mTypes, type).size() > 0) {
                                injectContextValues(cr, m);
                                candidates.add((ContextResolver<T>)cr.getProvider());
                            }
                        }
                    }
                }
            }
        }
        if (candidates.size() == 0) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            Collections.sort(candidates, new ClassComparator());
            return new ContextResolverProxy<T>(candidates);
        }
        
    }
    
    @SuppressWarnings("unchecked")
    public <T> ContextProvider<T> createContextProvider(Type contextType, 
                                                        Message m) {
        Class<?> contextCls = InjectionUtils.getActualType(contextType);
        if (contextCls == null) {
            return null;
        }
        for (ProviderInfo<ContextProvider<?>> cr : contextProviders) {
            Type[] types = cr.getProvider().getClass().getGenericInterfaces();
            for (Type t : types) {
                if (t instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType)t;
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > 0) {
                        Class<?> argCls = InjectionUtils.getActualType(args[0]);
                        
                        if (argCls != null && argCls.isAssignableFrom(contextCls)) {
                            return (ContextProvider<T>)cr.getProvider();
                        }
                    }
                }
            }
        }
        return null;
    }
    
    
    public <T extends Throwable> ExceptionMapper<T> createExceptionMapper(Class<?> exceptionType, Message m) {
        
        ExceptionMapper<T> mapper = doCreateExceptionMapper(exceptionType, m);
        if (mapper != null || this == SHARED_FACTORY) {
            return mapper;
        }
        
        return SHARED_FACTORY.createExceptionMapper(exceptionType, m);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Throwable> ExceptionMapper<T> doCreateExceptionMapper(
        Class<?> exceptionType, Message m) {
        
        List<ExceptionMapper<?>> candidates = new LinkedList<ExceptionMapper<?>>();
        
        for (ProviderInfo<ExceptionMapper<?>> em : exceptionMappers) {
            handleMapper(candidates, em, exceptionType, m, ExceptionMapper.class, true);
        }
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ExceptionMapperComparator());
        return (ExceptionMapper<T>) candidates.get(0);
    }
    
    @SuppressWarnings("unchecked")
    public <T> ParameterHandler<T> createParameterHandler(Class<?> paramType) {
        
        List<ParameterHandler<?>> candidates = new LinkedList<ParameterHandler<?>>();
        
        for (ProviderInfo<ParameterHandler<?>> em : paramHandlers) {
            handleMapper(candidates, em, paramType, null, ParameterHandler.class, true);
        }
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ClassComparator());
        return (ParameterHandler<T>) candidates.get(0);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Throwable> ResponseExceptionMapper<T> createResponseExceptionMapper(
                                 Class<?> paramType) {
        
        List<ResponseExceptionMapper<?>> candidates = new LinkedList<ResponseExceptionMapper<?>>();
        
        for (ProviderInfo<ResponseExceptionMapper<?>> em : responseExceptionMappers) {
            handleMapper(candidates, em, paramType, null, ResponseExceptionMapper.class, true);
        }
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ClassComparator());
        return (ResponseExceptionMapper<T>) candidates.get(0);
    }
    
    private static <T> void handleMapper(List<T> candidates, 
                                     ProviderInfo<T> em, 
                                     Class<?> expectedType, 
                                     Message m, 
                                     Class<?> providerClass,
                                     boolean injectContext) {
        
        Class<?> mapperClass =  ClassHelper.getRealClass(em.getProvider());
        Type[] types = getGenericInterfaces(mapperClass);
        for (Type t : types) {
            if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType)t;
                Type[] args = pt.getActualTypeArguments();
                for (int i = 0; i < args.length; i++) {
                    Type arg = args[i];
                    if (arg instanceof TypeVariable) {
                        TypeVariable<?> var = (TypeVariable<?>)arg;
                        Type[] bounds = var.getBounds();
                        boolean isResolved = false;
                        for (int j = 0; j < bounds.length; j++) {
                            Class<?> cls = InjectionUtils.getRawType(bounds[j]);
                            if (cls != null && cls.isAssignableFrom(expectedType)) {
                                isResolved = true;
                                break;
                            }
                        }
                        if (!isResolved) {
                            return;
                        }
                        if (injectContext) {
                            injectContextValues(em, m);
                        }
                        candidates.add(em.getProvider());
                        return;
                    }
                    Class<?> actualClass = InjectionUtils.getRawType(arg);
                    if (actualClass == null) {
                        continue;
                    }
                    if (actualClass.isAssignableFrom(expectedType)) {
                        if (injectContext) {
                            injectContextValues(em, m);
                        }
                        candidates.add(em.getProvider());
                        return;
                    }
                }
            } else if (t instanceof Class && ((Class<?>)t).isAssignableFrom(providerClass)) {
                if (injectContext) {
                    injectContextValues(em, m);
                }
                candidates.add(em.getProvider());
            }
        }
    }
    
    
    
    public <T> MessageBodyReader<T> createMessageBodyReader(Class<T> bodyType,
                                                            Type parameterType,
                                                            Annotation[] parameterAnnotations,
                                                            MediaType mediaType,
                                                            Message m) {
        // Try user provided providers
        MessageBodyReader<T> mr = chooseMessageReader(messageReaders,
                                                      bodyType,
                                                      parameterType,
                                                      parameterAnnotations,
                                                      mediaType,
                                                      m);
        
        if (mr == null) {
            mr = chooseMessageReader(jaxbReaders,
                                     bodyType,
                                     parameterType,
                                     parameterAnnotations,
                                     mediaType,
                                     m);
        }
        
        if (mr != null || SHARED_FACTORY == this) {
            return mr;
        }
        return SHARED_FACTORY.createMessageBodyReader(bodyType,
                                                      parameterType,
                                                      parameterAnnotations,
                                                      mediaType,
                                                      m);
    }
    
    private boolean isJaxbBasedProvider(Object sharedProvider) {
        String clsName = sharedProvider.getClass().getName();
        return JAXB_PROVIDER_NAME.equals(clsName) || JSON_PROVIDER_NAME.equals(clsName);
    }
    
    public List<ProviderInfo<RequestHandler>> getRequestHandlers() {
        List<ProviderInfo<RequestHandler>> handlers = null;
        if (requestHandlers.size() == 0) {
            handlers = SHARED_FACTORY.requestHandlers;
        } else {
            handlers = new ArrayList<ProviderInfo<RequestHandler>>();
            boolean customWADLHandler = false;
            for (int i = 0; i < requestHandlers.size(); i++) {
                if (requestHandlers.get(i).getProvider() instanceof WadlGenerator) {
                    customWADLHandler = true;
                    break;
                }
            }
            if (!customWADLHandler) {
                // TODO : this works only because we know we only have a single 
                // system handler which is a default WADLGenerator, think of a better approach
                handlers.addAll(SHARED_FACTORY.requestHandlers);    
            }
            handlers.addAll(requestHandlers);
            
        }
        return Collections.unmodifiableList(handlers);
    }
    
    public List<ProviderInfo<ResponseHandler>> getResponseHandlers() {
        return Collections.unmodifiableList(responseHandlers);
    }

    public <T> MessageBodyWriter<T> createMessageBodyWriter(Class<T> bodyType,
                                                            Type parameterType,
                                                            Annotation[] parameterAnnotations,
                                                            MediaType mediaType,
                                                            Message m) {
        // Try user provided providers
        MessageBodyWriter<T> mw = chooseMessageWriter(messageWriters, 
                                                      bodyType,
                                                      parameterType,
                                                      parameterAnnotations,
                                                      mediaType,
                                                      m);
        
        if (mw == null) {
            mw = chooseMessageWriter(jaxbWriters, 
                                     bodyType,
                                     parameterType,
                                     parameterAnnotations,
                                     mediaType,
                                     m);
        }
        
        if (mw != null || SHARED_FACTORY == this) {
            return mw;
        }
        
        return SHARED_FACTORY.createMessageBodyWriter(bodyType,
                                                  parameterType,
                                                  parameterAnnotations,
                                                  mediaType,
                                                  m);
    }
    
//CHECKSTYLE:OFF       
    private void setProviders(Object... providers) {
        
        for (Object o : providers) {
            if (o == null) {
                continue;
            }
            Class<?> oClass = ClassHelper.getRealClass(o);
            
            if (MessageBodyReader.class.isAssignableFrom(oClass)) {
                messageReaders.add(new ProviderInfo<MessageBodyReader<?>>((MessageBodyReader<?>)o, bus)); 
            }
            
            if (MessageBodyWriter.class.isAssignableFrom(oClass)) {
                messageWriters.add(new ProviderInfo<MessageBodyWriter<?>>((MessageBodyWriter<?>)o, bus)); 
            }
            
            if (ContextResolver.class.isAssignableFrom(oClass)) {
                contextResolvers.add(new ProviderInfo<ContextResolver<?>>((ContextResolver<?>)o, bus)); 
            }
            
            if (ContextProvider.class.isAssignableFrom(oClass)) {
                contextProviders.add(new ProviderInfo<ContextProvider<?>>((ContextProvider<?>)o, bus)); 
            }
            
            if (RequestHandler.class.isAssignableFrom(oClass)) {
                requestHandlers.add(new ProviderInfo<RequestHandler>((RequestHandler)o, bus)); 
            }
            
            if (ResponseHandler.class.isAssignableFrom(oClass)) {
                responseHandlers.add(new ProviderInfo<ResponseHandler>((ResponseHandler)o, bus)); 
            }
            
            if (ExceptionMapper.class.isAssignableFrom(oClass)) {
                exceptionMappers.add(new ProviderInfo<ExceptionMapper<?>>((ExceptionMapper<?>)o, bus)); 
            }
            
            if (ResponseExceptionMapper.class.isAssignableFrom(oClass)) {
                responseExceptionMappers.add(new ProviderInfo<ResponseExceptionMapper<?>>((ResponseExceptionMapper<?>)o, bus)); 
            }
            
            if (ParameterHandler.class.isAssignableFrom(oClass)) {
                paramHandlers.add(new ProviderInfo<ParameterHandler<?>>((ParameterHandler<?>)o, bus)); 
            }
        }
        sortReaders();
        sortWriters();
        sortContextResolvers();
        
        injectContextProxies(messageReaders, messageWriters, contextResolvers, 
        			requestHandlers, responseHandlers,
                       exceptionMappers);
    }
//CHECKSTYLE:ON
    
    static void injectContextValues(ProviderInfo<?> pi, Message m) {
        if (m != null) {
            InjectionUtils.injectContextFields(pi.getProvider(), pi, m);
            InjectionUtils.injectContextMethods(pi.getProvider(), pi, m);
        }
    }
    
    void injectContextProxies(List<?> ... providerLists) {
        for (List<?> list : providerLists) {
            List<ProviderInfo<?>> l2 = CastUtils.cast(list);
            for (ProviderInfo<?> pi : l2) {
                if (ProviderFactory.SHARED_FACTORY == this && isJaxbBasedProvider(pi.getProvider())) {
                    continue;
                }
                InjectionUtils.injectContextProxies(pi, pi.getProvider());
            }
        }
    }
    
    /*
     * sorts the available providers according to the media types they declare
     * support for. Sorting of media types follows the general rule: x/y < * x < *,
     * i.e. a provider that explicitly lists a media types is sorted before a
     * provider that lists *. Quality parameter values are also used such that
     * x/y;q=1.0 < x/y;q=0.7.
     */    
    private void sortReaders() {
        Collections.sort(messageReaders, new MessageBodyReaderComparator());
    }
    
    private void sortWriters() {
        Collections.sort(messageWriters, new MessageBodyWriterComparator());
    }
    
    private void sortContextResolvers() {
        Collections.sort(contextResolvers, new ContextResolverComparator());
    }
    
        
    
    /**
     * Choose the first body reader provider that matches the requestedMimeType 
     * for a sorted list of Entity providers
     * Returns null if none is found.
     * @param <T>
     * @param messageBodyReaders
     * @param type
     * @param requestedMimeType
     * @return
     */
    @SuppressWarnings("unchecked")
    private <T> MessageBodyReader<T> chooseMessageReader(List<ProviderInfo<MessageBodyReader<?>>> readers,
                                                         Class<T> type,
                                                         Type genericType,
                                                         Annotation[] annotations,
                                                         MediaType mediaType,
                                                         Message m) {
        List<MessageBodyReader<?>> candidates = new LinkedList<MessageBodyReader<?>>();
        for (ProviderInfo<MessageBodyReader<?>> ep : readers) {
            if (matchesReaderCriterias(ep, type, genericType, annotations, mediaType, m)) {
                if (this == SHARED_FACTORY) {
                    return (MessageBodyReader<T>) ep.getProvider();
                }
                handleMapper(candidates, ep, type, m, MessageBodyReader.class, false);
            }
        }     
        
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ClassComparator());
        return (MessageBodyReader<T>) candidates.get(0);
        
    }
    
    private <T> boolean matchesReaderCriterias(ProviderInfo<MessageBodyReader<?>> pi,
                                               Class<T> type,
                                               Type genericType,
                                               Annotation[] annotations,
                                               MediaType mediaType,
                                               Message m) {
        MessageBodyReader<?> ep = pi.getProvider();
        List<MediaType> supportedMediaTypes = JAXRSUtils.getProviderConsumeTypes(ep);
        
        List<MediaType> availableMimeTypes = 
            JAXRSUtils.intersectMimeTypes(Collections.singletonList(mediaType), supportedMediaTypes, false);

        if (availableMimeTypes.size() == 0) {
            return false;
        }
        boolean injected = false;
        if (this != SHARED_FACTORY || !isJaxbBasedProvider(ep)) {
            injectContextValues(pi, m);
            injected = true;
        }
        boolean matches = ep.isReadable(type, genericType, annotations, mediaType);
        if (!matches && injected) {
            pi.clearThreadLocalProxies();
        }
        return matches;
    }
        
    /**
     * Choose the first body writer provider that matches the requestedMimeType 
     * for a sorted list of Entity providers
     * Returns null if none is found.
     * @param <T>
     * @param messageBodyWriters
     * @param type
     * @param requestedMimeType
     * @return
     */
    @SuppressWarnings("unchecked")
    private <T> MessageBodyWriter<T> chooseMessageWriter(List<ProviderInfo<MessageBodyWriter<?>>> writers,
                                                         Class<T> type,
                                                         Type genericType,
                                                         Annotation[] annotations,
                                                         MediaType mediaType,
                                                         Message m) {
        List<MessageBodyWriter<?>> candidates = new LinkedList<MessageBodyWriter<?>>();
        for (ProviderInfo<MessageBodyWriter<?>> ep : writers) {
            if (matchesWriterCriterias(ep, type, genericType, annotations, mediaType, m)) {
                if (this == SHARED_FACTORY) {
                    return (MessageBodyWriter<T>) ep.getProvider();
                }
                handleMapper(candidates, ep, type, m, MessageBodyWriter.class, false);
            }
        }     
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ClassComparator());
        return (MessageBodyWriter<T>) candidates.get(0);
    }
    
    private <T> boolean matchesWriterCriterias(ProviderInfo<MessageBodyWriter<?>> pi,
                                               Class<T> type,
                                               Type genericType,
                                               Annotation[] annotations,
                                               MediaType mediaType,
                                               Message m) {
        MessageBodyWriter<?> ep = pi.getProvider();
        List<MediaType> supportedMediaTypes = JAXRSUtils.getProviderProduceTypes(ep);
        
        List<MediaType> availableMimeTypes = 
            JAXRSUtils.intersectMimeTypes(Collections.singletonList(mediaType),
                                          supportedMediaTypes, false);

        if (availableMimeTypes.size() == 0) {
            return false;
        }
        boolean injected = false;
        if ((this != SHARED_FACTORY || !isJaxbBasedProvider(ep))
            && m.get(ACTIVE_JAXRS_PROVIDER_KEY) != ep) {
            injectContextValues(pi, m);
            injected = true;
        }
        boolean matches = ep.isWriteable(type, genericType, annotations, mediaType);
        if (!matches && injected) {
            pi.clearThreadLocalProxies();
        }
        return matches;
    }
    
    List<ProviderInfo<MessageBodyReader<?>>> getMessageReaders() {
        return Collections.unmodifiableList(messageReaders);
    }

    List<ProviderInfo<MessageBodyWriter<?>>> getMessageWriters() {
        return Collections.unmodifiableList(messageWriters);
    }
    
    List<ProviderInfo<ContextResolver<?>>> getContextResolvers() {
        return Collections.unmodifiableList(contextResolvers);
    }
    
     
    public void registerUserProvider(Object provider) {
        setUserProviders(Collections.singletonList(provider));    
    }
    /**
     * Use for injection of entityProviders
     * @param entityProviders the entityProviders to set
     */
    public void setUserProviders(List<?> userProviders) {
        setProviders(userProviders.toArray());
    }

    private static class MessageBodyReaderComparator 
        implements Comparator<ProviderInfo<MessageBodyReader<?>>> {
        
        public int compare(ProviderInfo<MessageBodyReader<?>> p1, 
                           ProviderInfo<MessageBodyReader<?>> p2) {
            MessageBodyReader<?> e1 = p1.getProvider();
            MessageBodyReader<?> e2 = p2.getProvider();
            List<MediaType> types1 = JAXRSUtils.getProviderConsumeTypes(e1);
            types1 = JAXRSUtils.sortMediaTypes(types1);
            List<MediaType> types2 = JAXRSUtils.getProviderConsumeTypes(e2);
            types2 = JAXRSUtils.sortMediaTypes(types2);
    
            return JAXRSUtils.compareSortedMediaTypes(types1, types2);
        }
    }
    
    private static class MessageBodyWriterComparator 
        implements Comparator<ProviderInfo<MessageBodyWriter<?>>> {
        
        public int compare(ProviderInfo<MessageBodyWriter<?>> p1, 
                           ProviderInfo<MessageBodyWriter<?>> p2) {
            MessageBodyWriter<?> e1 = p1.getProvider();
            MessageBodyWriter<?> e2 = p2.getProvider();
            
            List<MediaType> types1 =
                JAXRSUtils.sortMediaTypes(JAXRSUtils.getProviderProduceTypes(e1));
            List<MediaType> types2 =
                JAXRSUtils.sortMediaTypes(JAXRSUtils.getProviderProduceTypes(e2));
    
            return JAXRSUtils.compareSortedMediaTypes(types1, types2);
        }
    }
    
    private static class ContextResolverComparator 
        implements Comparator<ProviderInfo<ContextResolver<?>>> {
        
        public int compare(ProviderInfo<ContextResolver<?>> p1, 
                           ProviderInfo<ContextResolver<?>> p2) {
            ContextResolver<?> e1 = p1.getProvider();
            ContextResolver<?> e2 = p2.getProvider();
            
            List<MediaType> types1 =
                JAXRSUtils.sortMediaTypes(JAXRSUtils.getProduceTypes(
                                               e1.getClass().getAnnotation(Produces.class)));
            List<MediaType> types2 =
                JAXRSUtils.sortMediaTypes(JAXRSUtils.getProduceTypes(
                                               e2.getClass().getAnnotation(Produces.class)));
    
            return JAXRSUtils.compareSortedMediaTypes(types1, types2);
        }
    }
    
    public void setApplicationProvider(ProviderInfo<Application> app) {
        application = app;
    }
    
    public void setRequestPreprocessor(RequestPreprocessor rp) {
        this.requestPreprocessor = rp;
    }
    
    public RequestPreprocessor getRequestPreprocessor() {
        return requestPreprocessor;
    }
    
    public void clearThreadLocalProxies() {
        clearProxies(messageReaders,
                     messageWriters,
                     jaxbReaders,
                     jaxbWriters,
                     contextResolvers,
                     requestHandlers,
                     responseHandlers,
                     exceptionMappers);
        if (application != null) {
            application.clearThreadLocalProxies();
        }
        if (this != SHARED_FACTORY) {
            SHARED_FACTORY.clearThreadLocalProxies();
        }
    }
    
    void clearProxies(List<?> ...lists) {
        for (List<?> list : lists) {
            List<ProviderInfo<?>> l2 = CastUtils.cast(list);
            for (ProviderInfo<?> pi : l2) {
                pi.clearThreadLocalProxies();
            }
        }
    }
    
    public void clearProviders() {
        messageReaders.clear();
        messageWriters.clear();
        contextResolvers.clear();
        exceptionMappers.clear();
        requestHandlers.clear();
        responseHandlers.clear();
        paramHandlers.clear();
        responseExceptionMappers.clear();
    }
    
    public void setBus(Bus bus) {
        if (bus == null) {
            return;
        }
        for (ProviderInfo<MessageBodyReader<?>> r : messageReaders) {
            injectProviderProperty(r.getProvider(), "setBus", Bus.class, bus);
        }
    }
    
    private boolean injectProviderProperty(Object provider, String mName, Class<?> pClass, 
                                        Object pValue) {
        try {
            Method m = provider.getClass().getMethod(mName, new Class[]{pClass});
            m.invoke(provider, new Object[]{pValue});
            return true;
        } catch (Exception ex) {
            // ignore
        }
        return false;
    }
    
    public void setSchemaLocations(List<String> schemas) {
        boolean schemasMethodAvailable = false;
        for (ProviderInfo<MessageBodyReader<?>> r : messageReaders) {
            schemasMethodAvailable = injectProviderProperty(r.getProvider(), "setSchemas", 
                                                            List.class, schemas);
        }
        if (!schemasMethodAvailable) {
            for (ProviderInfo<MessageBodyReader<?>> r : jaxbReaders) {
                schemasMethodAvailable = injectProviderProperty(r.getProvider(), "setSchemas", 
                                                                List.class, schemas);
            }
        }
    }

    public void initProviders(List<ClassResourceInfo> cris) {
        Set<Object> set = getReadersWriters();
        for (Object o : set) {
            Object provider = ((ProviderInfo<?>)o).getProvider();
            if (provider instanceof AbstractConfigurableProvider) {
                ((AbstractConfigurableProvider)provider).init(cris);
            }
        }
        if (this != SHARED_FACTORY) {
            SHARED_FACTORY.initProviders(cris);
        }
    }
    
    Set<Object> getReadersWriters() {
        Set<Object> set = new HashSet<Object>();
        set.addAll(messageReaders);
        set.addAll(jaxbReaders);
        set.addAll(messageWriters);
        set.addAll(jaxbWriters);
        return set;
    }
    
    private static class ExceptionMapperComparator implements 
        Comparator<ExceptionMapper<? extends Throwable>> {

        public int compare(ExceptionMapper<? extends Throwable> em1, 
                           ExceptionMapper<? extends Throwable> em2) {
            return compareClasses(em1, em2);
        }
        
    }
    
    private static class ClassComparator implements 
        Comparator<Object> {
    
        public int compare(Object em1, Object em2) {
            return compareClasses(em1, em2);
        }
        
    }
    
    private static int compareClasses(Object o1, Object o2) {
        Class<?> cl1 = ClassHelper.getRealClass(o1); 
        Class<?> cl2 = ClassHelper.getRealClass(o2);
        
        Type[] types1 = getGenericInterfaces(cl1);
        Type[] types2 = getGenericInterfaces(cl2);
        
        if (types1.length == 0 && types2.length > 0) {
            return 1;
        } else if (types1.length > 0 && types2.length == 0) {
            return -1;
        }
        
        Class<?> realClass1 = InjectionUtils.getActualType(types1[0]);
        Class<?> realClass2 = InjectionUtils.getActualType(types2[0]);
        if (realClass1 == realClass2) {
            return 0;
        }
        if (realClass1.isAssignableFrom(realClass2)) {
            // subclass should go first
            return 1;
        }
        return -1;
    }
    
    private static Type[] getGenericInterfaces(Class<?> cls) {
        if (Object.class == cls) {
            return new Type[]{};
        }
        Type genericSuperCls = cls.getGenericSuperclass();
        if (genericSuperCls instanceof ParameterizedType) {
            return new Type[]{genericSuperCls};
        }
        Type[] types = cls.getGenericInterfaces();
        if (types.length > 0) {
            return types;
        }
        return getGenericInterfaces(cls.getSuperclass());
    }
    
    static class ContextResolverProxy<T> implements ContextResolver<T> {
        private List<ContextResolver<T>> candidates; 
        public ContextResolverProxy(List<ContextResolver<T>> candidates) {
            this.candidates = candidates;
        }
        public T getContext(Class<?> cls) {
            for (ContextResolver<T> resolver : candidates) {
                T context = resolver.getContext(cls);
                if (context != null) {
                    return context;
                }
            }
            return null;
        } 
        
        public List<ContextResolver<T>> getResolvers() {
            return candidates;
        }
    }
}
