package com.example.apigateway.filter;

import com.google.common.base.Strings;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;

public class EnrichmentFilter extends ZuulFilter {

    @Override
    public String filterType() {
        return FilterConstants.PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return FilterConstants.SIMPLE_HOST_ROUTING_FILTER_ORDER - 1;
    }


    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext context = RequestContext.getCurrentContext();
        if (!Strings.isNullOrEmpty(context.getRequest().getHeader("x-port-forwarded-for"))
                && !Strings.isNullOrEmpty(context.getRequest().getHeader("x-forwarded-for"))) {
            System.out.println(context.getRequest().getHeader("x-port-forwarded-for"));
            System.out.println(context.getRequest().getHeader("x-forwarded-for"));
            context.addZuulRequestHeader("x-msisdn", "value");
        }
        return null;
    }
}
