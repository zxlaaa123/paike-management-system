package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorCodeVo {

    private String code;
    private Integer numericCode;
    private String category;
    private Integer httpStatus;
    private String defaultMessage;
    private String frontendPrompt;
    private String handlingSuggestion;
}
