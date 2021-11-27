package com.amrut.prabhu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringClientApplication {

    @Autowired
    Service service;

    public static void main(String[] args) {
        SpringApplication.run(SpringClientApplication.class, args);
    }

}
