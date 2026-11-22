package com.mkalki.cloudtaskapi.requestlimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RequestSizeLimitProperties.class)
public class RequestSizeLimitConfig {
}
