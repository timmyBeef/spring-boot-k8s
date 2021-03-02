package com.example.springbootk8s;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@RestController
public class SpringBootK8sApplication {

    @Value("${hello.color}")
    String color;

    @GetMapping("/color")
    public String helloColor() {
        return color;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringBootK8sApplication.class, args);
    }

}
