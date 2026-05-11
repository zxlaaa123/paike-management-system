package com.paike.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.paike.scheduler.mapper")
public class SchedulerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerBackendApplication.class, args);
    }
}
