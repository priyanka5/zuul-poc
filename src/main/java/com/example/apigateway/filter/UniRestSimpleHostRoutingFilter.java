package com.example.apigateway.filter;

import com.google.common.io.ByteStreams;
import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.netflix.client.ClientException;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.cloud.commons.httpclient.ApacheHttpClientConnectionManagerFactory;
import org.springframework.cloud.commons.httpclient.ApacheHttpClientFactory;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.route.SimpleHostRoutingFilter;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;

public class UniRestSimpleHostRoutingFilter extends SimpleHostRoutingFilter {

    private static final Log log = LogFactory.getLog(UniRestSimpleHostRoutingFilter.class);
    private final Timer connectionManagerTimer = new Timer("SimpleHostRoutingFilter.connectionManagerTimer", true);
    private boolean sslHostnameValidationEnabled;
    private boolean forceOriginalQueryStringEncoding;
    private ProxyRequestHelper helper;

    public UniRestSimpleHostRoutingFilter(ProxyRequestHelper helper, ZuulProperties properties, ApacheHttpClientConnectionManagerFactory connectionManagerFactory, ApacheHttpClientFactory httpClientFactory) {
        super(helper, properties, connectionManagerFactory, httpClientFactory);
        this.helper = helper;
    }

    public UniRestSimpleHostRoutingFilter(ProxyRequestHelper helper, ZuulProperties properties, CloseableHttpClient httpClient) {
        super(helper, properties, httpClient);
        this.helper = helper;
    }

    @Override
    public String filterType() {
        return FilterConstants.ROUTE_TYPE;
    }

    @Override
    public int filterOrder() {
        return 100;
    }

    @Override
    public boolean shouldFilter() {
        return RequestContext.getCurrentContext().getRouteHost() != null && RequestContext.getCurrentContext().sendZuulResponse();
    }

    private String getVerb(HttpServletRequest request) {
        String sMethod = request.getMethod();
        return sMethod.toUpperCase();
    }

    protected InputStream getRequestBody(HttpServletRequest request) {
        Object requestEntity = null;

        try {
            requestEntity = (InputStream) RequestContext.getCurrentContext().get("requestEntity");
            if (requestEntity == null) {
                requestEntity = request.getInputStream();
            }
        } catch (IOException var4) {
            log.error("error during getRequestBody", var4);
        }

        return (InputStream) requestEntity;
    }

    protected long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                return Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException var4) {
                ;
            }
        }

        return (long) request.getContentLength();
    }

    @Override
    public Object run() {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletRequest request = context.getRequest();
        MultiValueMap<String, String> headers = this.helper.buildZuulRequestHeaders(request);
        MultiValueMap<String, String> params = this.helper.buildZuulRequestQueryParams(request);
        String verb = this.getVerb(request);
        InputStream requestEntity = this.getRequestBody(request);
        if (this.getContentLength(request) < 0L) {
            context.setChunkedRequestBody();
        }

        String uri = this.helper.buildZuulRequestURI(request);
        this.helper.addIgnoredHeaders(new String[0]);

        try {
            com.mashape.unirest.http.HttpResponse response = this.forward(verb, uri, request, headers, params, requestEntity);
            this.setResponse(response);
            return null;
        } catch (Exception var9) {
            throw new ZuulRuntimeException(this.handleException(var9));
        }
    }

    private com.mashape.unirest.http.HttpResponse forward(String verb, String uri, HttpServletRequest request, MultiValueMap<String, String> headers, MultiValueMap<String, String> params, InputStream requestEntity) throws UnirestException {
        URL host = RequestContext.getCurrentContext().getRouteHost();
        uri = StringUtils.cleanPath((host.getPath() + uri).replaceAll("/{2,}", "/"));
        String uriWithQueryString = uri + (this.forceOriginalQueryStringEncoding ? this.getEncodedQueryString(request) : this.helper.getQueryString(params));

        long contentLength = this.getContentLength(request);
        ContentType contentType = null;
        if (request.getContentType() != null) {
            contentType = ContentType.parse(request.getContentType());
        }
        byte[] entity = null;

        //
        try {
            entity = ByteStreams.toByteArray(requestEntity);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //

        String finalUri = host.getProtocol() + "://" + host.getHost() + uriWithQueryString;
        switch (verb) {
            case "GET":
                return Unirest.get(finalUri).headers(headers.toSingleValueMap()).asBinary();
            case "PUT":
                return Unirest.put(finalUri).headers(headers.toSingleValueMap()).body(entity).asBinary();
            case "POST":
                return Unirest.post(finalUri).headers(headers.toSingleValueMap()).body(entity).asBinary();
            case "PATCH":
                return Unirest.patch(finalUri).headers(headers.toSingleValueMap()).body(entity).asBinary();
            case "DELETE":
                return Unirest.delete(finalUri).headers(headers.toSingleValueMap()).asBinary();
            default:
                return null;
        }
    }

    private String getEncodedQueryString(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null ? "?" + query : "";
    }

    private void setResponse(com.mashape.unirest.http.HttpResponse response) throws IOException {
        RequestContext.getCurrentContext().set("zuulResponse", response);
        this.helper.setResponse(response.getStatus(), response.getRawBody(), this.revertHeaders(response.getHeaders()));
    }

    private MultiValueMap<String, String> revertHeaders(Headers headers) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap();

        headers.forEach(
                (name, value) -> {
                    if (!map.containsKey(name)) {
                        map.put(name, new ArrayList());
                    }
                    map.get(name).add(value.get(0));
                }
        );

        return map;
    }

    protected ZuulException handleException(Exception ex) {
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        Throwable cause = ex;
        String message = ex.getMessage();
        ClientException clientException = this.findClientException(ex);
        if (clientException != null) {
            if (clientException.getErrorType() == ClientException.ErrorType.SERVER_THROTTLED) {
                statusCode = HttpStatus.SERVICE_UNAVAILABLE.value();
            }

            cause = clientException;
            message = clientException.getErrorType().toString();
        }

        return new ZuulException((Throwable) cause, "Forwarding error", statusCode, message);
    }

    protected ClientException findClientException(Throwable t) {
        if (t == null) {
            return null;
        } else {
            return t instanceof ClientException ? (ClientException) t : this.findClientException(t.getCause());
        }
    }
}
