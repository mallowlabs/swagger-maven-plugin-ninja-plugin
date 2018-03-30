package com.github.mallowlabs.swagger.docgen.ninja;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.reflections.Reflections;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.ResponseEntity;

import com.github.kongchen.swagger.docgen.jaxrs.BeanParamInjectParamExtention;
import com.github.kongchen.swagger.docgen.jaxrs.JaxrsParameterExtension;
import com.github.kongchen.swagger.docgen.reader.JaxrsReader;
import com.google.inject.Injector;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.jersey.SwaggerJerseyJaxrs;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import ninja.Bootstrap;
import ninja.Route;
import ninja.Router;
import ninja.servlet.NinjaServletBootstrap;
import ninja.utils.NinjaMode;
import ninja.utils.NinjaPropertiesImpl;

public class NinjaReader extends JaxrsReader {
    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();

    public NinjaReader(Swagger swagger, Log LOG) {
        super(swagger, LOG);
    }

    @Override
    protected void updateExtensionChain() {
        List<SwaggerExtension> extensions = new ArrayList<SwaggerExtension>();
        extensions.add(new BeanParamInjectParamExtention());
        extensions.add(new SwaggerJerseyJaxrs());
        extensions.add(new JaxrsParameterExtension());
        extensions.add(new NinjaParameterExtension());
        SwaggerExtensions.setExtensions(extensions);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) {
        if (swagger == null) {
            swagger = new Swagger();
        }

        LOG.debug("Initializing Ninja...");

        NinjaPropertiesImpl ninjaProperties = new NinjaPropertiesImpl(NinjaMode.prod);
        Bootstrap bootstrap = new NinjaServletBootstrap(ninjaProperties);
        bootstrap.boot();

        Injector injector = bootstrap.getInjector();

        Router router = injector.getInstance(Router.class);

        for (Class<?> cls : classes) {
            LOG.debug("Parsing " + cls.getSimpleName());
            Api api = AnnotationUtils.findAnnotation(cls, Api.class);

            HashMap<String, Tag> parentTags = new HashMap<String, Tag>();

            Map<String, Tag> tags = updateTagsForApi(parentTags, api);
            List<SecurityRequirement> securities = getSecurityRequirements(api);
            Map<String, Tag> discoveredTags = scanClasspathForTags();

            for (Method method : cls.getMethods()) {
                ApiOperation apiOperation = AnnotationUtils.findAnnotation(method, ApiOperation.class);

                if (apiOperation != null && apiOperation.hidden()) {
                    continue;
                }

                List<Route> routes = router.getRoutes();
                for (Route route : routes) {
                    if (route.getControllerClass().equals(cls) && route.getControllerMethod().equals(method)) {
                        String operationPath = route.getUri();
                        String httpMethod = StringUtils.lowerCase(route.getHttpMethod());

                        LOG.debug("Making: " + httpMethod + " " + operationPath);

                        Operation operation = parseMethod(method);
                        // updateOperationParameters(parentParameters,
                        // regexMap, operation);
                        updateOperationProtocols(apiOperation, operation);

                        String[] apiConsumes = new String[0];
                        String[] apiProduces = new String[0];

                        Consumes consumes = AnnotationUtils.findAnnotation(cls, Consumes.class);
                        if (consumes != null) {
                            apiConsumes = consumes.value();
                        }
                        Produces produces = AnnotationUtils.findAnnotation(cls, Produces.class);
                        if (produces != null) {
                            apiProduces = produces.value();
                        }

                        apiConsumes = updateOperationConsumes(new String[0], apiConsumes, operation);
                        apiProduces = updateOperationProduces(new String[0], apiProduces, operation);

                        // handleSubResource(apiConsumes, httpMethod,
                        // apiProduces, tags, method, operationPath,
                        // operation);

                        // can't continue without a valid http method
                        updateTagsForOperation(operation, apiOperation);
                        updateOperation(apiConsumes, apiProduces, tags, securities, operation);
                        updatePath(operationPath, httpMethod, operation);
                        updateTagDescriptions(discoveredTags);

                    }
                }

            }
        }

        return swagger;
    }

    private Map<String, Tag> scanClasspathForTags() {
        Map<String, Tag> tags = new HashMap<String, Tag>();
        for (Class<?> aClass : new Reflections("").getTypesAnnotatedWith(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);

            for (io.swagger.annotations.Tag tag : swaggerDefinition.tags()) {

                String tagName = tag.name();
                if (!tagName.isEmpty()) {
                    tags.put(tag.name(), new Tag().name(tag.name()).description(tag.description()));
                }
            }
        }

        return tags;
    }

    private void updateTagDescriptions(Map<String, Tag> discoveredTags) {
        if (swagger.getTags() != null) {
            for (Tag tag : swagger.getTags()) {
                Tag rightTag = discoveredTags.get(tag.getName());
                if (rightTag != null && rightTag.getDescription() != null) {
                    tag.setDescription(rightTag.getDescription());
                }
            }
        }
    }

    private Operation parseMethod(Method method) {
        int responseCode = 200;
        Operation operation = new Operation();

        Type responseClass = null;
        String responseContainer = null;
        String operationId = method.getName();
        Map<String, Property> defaultResponseHeaders = null;

        ApiOperation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method, ApiOperation.class);

        if (apiOperation != null) {
            if (apiOperation.hidden()) {
                return null;
            }
            if (!apiOperation.nickname().isEmpty()) {
                operationId = apiOperation.nickname();
            }

            defaultResponseHeaders = parseResponseHeaders(apiOperation.responseHeaders());

            operation.summary(apiOperation.value()).description(apiOperation.notes());

            Set<Map<String, Object>> customExtensions = parseCustomExtensions(apiOperation.extensions());

            for (Map<String, Object> extension : customExtensions) {
                if (extension == null) {
                    continue;
                }
                for (Map.Entry<String, Object> map : extension.entrySet()) {
                    operation.setVendorExtension(map.getKey().startsWith("x-") ? map.getKey() : "x-" + map.getKey(), map.getValue());
                }
            }

            if (!apiOperation.response().equals(Void.class)) {
                responseClass = apiOperation.response();
            }
            if (!apiOperation.responseContainer().isEmpty()) {
                responseContainer = apiOperation.responseContainer();
            }

            /// security
            List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
            for (Authorization auth : apiOperation.authorizations()) {
                if (!auth.value().isEmpty()) {
                    SecurityRequirement security = new SecurityRequirement();
                    security.setName(auth.value());
                    for (AuthorizationScope scope : auth.scopes()) {
                        if (!scope.scope().isEmpty()) {
                            security.addScope(scope.scope());
                        }
                    }
                    securities.add(security);
                }
            }
            for (SecurityRequirement sec : securities) {
                operation.security(sec);
            }

            responseCode = apiOperation.code();
        }

        if (responseClass == null) {
            // pick out response from method declaration
            LOG.info("picking up response class from method " + method);
            responseClass = method.getGenericReturnType();
        }
        if (responseClass instanceof ParameterizedType && ResponseEntity.class.equals(((ParameterizedType) responseClass).getRawType())) {
            responseClass = ((ParameterizedType) responseClass).getActualTypeArguments()[0];
        }
        boolean hasApiAnnotation = false;
        if (responseClass instanceof Class) {
            hasApiAnnotation = AnnotationUtils.findAnnotation((Class) responseClass, Api.class) != null;
        }
        if (responseClass != null && !responseClass.equals(Void.class) && !responseClass.equals(ResponseEntity.class) && !hasApiAnnotation) {
            if (isPrimitive(responseClass)) {
                Property property = ModelConverters.getInstance().readAsProperty(responseClass);
                if (property != null) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, property);
                    operation.response(responseCode, new Response().description("successful operation").schema(responseProperty).headers(defaultResponseHeaders));
                }
            } else if (!responseClass.equals(Void.class) && !responseClass.equals(void.class)) {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClass);
                if (models.isEmpty()) {
                    Property pp = ModelConverters.getInstance().readAsProperty(responseClass);
                    operation.response(responseCode, new Response().description("successful operation").schema(pp).headers(defaultResponseHeaders));
                }
                for (String key : models.keySet()) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, new RefProperty().asDefault(key));
                    operation.response(responseCode, new Response().description("successful operation").schema(responseProperty).headers(defaultResponseHeaders));
                    swagger.model(key, models.get(key));
                }
            }
            Map<String, Model> models = ModelConverters.getInstance().readAll(responseClass);
            for (Map.Entry<String, Model> entry : models.entrySet()) {
                swagger.model(entry.getKey(), entry.getValue());
            }
        }

        operation.operationId(operationId);

        ApiResponses responseAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            updateApiResponse(operation, responseAnnotation);
        }

        Deprecated annotation = AnnotationUtils.findAnnotation(method, Deprecated.class);
        if (annotation != null) {
            operation.deprecated(true);
        }

        // FIXME `hidden` is never used
        boolean hidden = false;
        if (apiOperation != null) {
            hidden = apiOperation.hidden();
        }

        // process parameters
        Class[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = genericParameterTypes[i];
            List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            List<Parameter> parameters = getParameters(type, annotations);

            for (Parameter parameter : parameters) {
                if (parameter.getName().isEmpty()) {
                    parameter.setName(parameterNames[i]);
                }
                operation.parameter(parameter);
            }
        }

        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("successful operation"));
        }

        // Process @ApiImplicitParams
        this.readImplicitParameters(method, operation);

        processOperationDecorator(operation, method);

        return operation;
    }

    void processOperationDecorator(Operation operation, Method method) {
        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        if (chain.hasNext()) {
            SwaggerExtension extension = chain.next();
            extension.decorateOperation(operation, method, chain);
        }
    }

    static class ResponseContainerConverter {
        Property withResponseContainer(String responseContainer, Property property) {
            if ("list".equalsIgnoreCase(responseContainer)) {
                return new ArrayProperty(property);
            }
            if ("set".equalsIgnoreCase(responseContainer)) {
                return new ArrayProperty(property).uniqueItems();
            }
            if ("map".equalsIgnoreCase(responseContainer)) {
                return new MapProperty(property);
            }
            return property;
        }
    }

}
