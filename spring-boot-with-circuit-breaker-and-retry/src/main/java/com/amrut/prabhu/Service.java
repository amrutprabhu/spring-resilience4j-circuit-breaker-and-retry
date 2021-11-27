package com.amrut.prabhu;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@org.springframework.stereotype.Service
public class Service {

    @Value("${service2.url:http://localhost:6060/service2}")
    String serviceUrl;

    @CircuitBreaker(name = "myCircuitBreaker", fallbackMethod = "fallback")
    @Retry(name = "myRetry")
    public String fetchData() {
        System.out.println(" Making a request to " + serviceUrl + " at :" + LocalDateTime.now());

        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(serviceUrl, String.class);
    }

    public String fallback(Exception e) {
        return "fallback value";
    }
}
