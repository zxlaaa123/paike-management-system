package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MigrationInitializerStatusVo {

    private String name;
    private String type;
    private String className;
    private String status;
    private String description;
}
