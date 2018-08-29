package com.example.apigateway.configuration;

import com.example.apigateway.filter.EnrichmentFilter;
import com.example.apigateway.filter.UniRestSimpleHostRoutingFilter;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZuulFiltersConfig {



    @Bean
    public EnrichmentFilter sampleFilter() {
        return new EnrichmentFilter();
    }

    @Bean
    public UniRestSimpleHostRoutingFilter simpleHostRoutingFilter(ProxyRequestHelper proxyRequestHelper, ZuulProperties zuulProperties) {
        return new UniRestSimpleHostRoutingFilter(proxyRequestHelper,zuulProperties,null);
    }

}
