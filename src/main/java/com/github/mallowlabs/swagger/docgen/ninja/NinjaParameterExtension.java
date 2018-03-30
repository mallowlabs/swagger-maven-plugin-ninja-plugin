package com.github.mallowlabs.swagger.docgen.ninja;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.AbstractSwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.parameters.SerializableParameter;
import io.swagger.models.properties.Property;
import ninja.params.Header;
import ninja.params.Param;
import ninja.params.Params;
import ninja.params.PathParam;

public class NinjaParameterExtension extends AbstractSwaggerExtension {

    @Override
    public List<Parameter> extractParameters(List<Annotation> annotations, Type type, Set<Type> typesToSkip, Iterator<SwaggerExtension> chain) {
        if (this.shouldIgnoreType(type, typesToSkip)) {
            return new ArrayList<Parameter>();
        }

        List<Parameter> parameters = new ArrayList<Parameter>();
        SerializableParameter parameter = null;
        for (Annotation annotation : annotations) {
            parameter = getParameter(type, parameter, annotation);
        }
        if (parameter != null) {
            parameters.add(parameter);
        }

        return parameters;
    }

    public static SerializableParameter getParameter(Type type, SerializableParameter parameter, Annotation annotation) {
        String defaultValue = "";

        if (annotation instanceof Param) {
            Param param = (Param) annotation;
            QueryParameter queryParameter = new QueryParameter().name(param.value());

            if (!defaultValue.isEmpty()) {
                queryParameter.setDefaultValue(defaultValue);
            }
            Property schema = ModelConverters.getInstance().readAsProperty(type);
            if (schema != null) {
                queryParameter.setProperty(schema);
            }

            String parameterType = queryParameter.getType();
            if (parameterType == null || parameterType.equals("ref")) {
                queryParameter.setType("string");
            }
            parameter = queryParameter;
        } else if (annotation instanceof Params) {
            Params param = (Params) annotation;
            QueryParameter queryParameter = new QueryParameter().name(param.value());

            if (!defaultValue.isEmpty()) {
                queryParameter.setDefaultValue(defaultValue);
            }
            Property schema = ModelConverters.getInstance().readAsProperty(type);
            if (schema != null) {
                queryParameter.setProperty(schema);
            }

            String parameterType = queryParameter.getType();
            if (parameterType == null || parameterType.equals("ref")) {
                queryParameter.setType("string");
            }
            parameter = queryParameter;
        } else if (annotation instanceof PathParam) {
            PathParam param = (PathParam) annotation;
            PathParameter pathParameter = new PathParameter().name(param.value());
            if (!defaultValue.isEmpty()) {
                pathParameter.setDefaultValue(defaultValue);
            }
            Property schema = ModelConverters.getInstance().readAsProperty(type);
            if (schema != null) {
                pathParameter.setProperty(schema);
            }

            String parameterType = pathParameter.getType();
            if (parameterType == null || parameterType.equals("ref")) {
                pathParameter.setType("string");
            }
            parameter = pathParameter;
        } else if (annotation instanceof Header) {
            Header param = (Header) annotation;
            HeaderParameter headerParameter = new HeaderParameter().name(param.value());
            headerParameter.setDefaultValue(defaultValue);
            Property schema = ModelConverters.getInstance().readAsProperty(type);
            if (schema != null) {
                headerParameter.setProperty(schema);
            }

            String parameterType = headerParameter.getType();
            if (parameterType == null || parameterType.equals("ref") || parameterType.equals("array")) {
                headerParameter.setType("string");
            }
            parameter = headerParameter;

        }

        // TODO MyObject

        return parameter;
    }
}
