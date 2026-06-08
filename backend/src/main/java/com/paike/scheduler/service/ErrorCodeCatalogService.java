package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.common.exception.SystemErrorCode;
import com.paike.scheduler.service.vo.ErrorCodeVo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ErrorCodeCatalogService {

    public List<ErrorCodeVo> list(String category) {
        return Arrays.stream(SystemErrorCode.values())
                .filter(errorCode -> category == null
                        || category.isBlank()
                        || errorCode.getCategory().equalsIgnoreCase(category.trim()))
                .map(this::toVo)
                .toList();
    }

    public ErrorCodeVo getByCode(String code) {
        return Arrays.stream(SystemErrorCode.values())
                .filter(errorCode -> errorCode.getCode().equalsIgnoreCase(code))
                .findFirst()
                .map(this::toVo)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.RESOURCE_NOT_FOUND.getNumericCode(), "错误码不存在"));
    }

    private ErrorCodeVo toVo(SystemErrorCode errorCode) {
        ErrorCodeVo vo = new ErrorCodeVo();
        vo.setCode(errorCode.getCode());
        vo.setNumericCode(errorCode.getNumericCode());
        vo.setCategory(errorCode.getCategory());
        vo.setHttpStatus(errorCode.getHttpStatus());
        vo.setDefaultMessage(errorCode.getDefaultMessage());
        vo.setFrontendPrompt(errorCode.getFrontendPrompt());
        vo.setHandlingSuggestion(errorCode.getHandlingSuggestion());
        return vo;
    }
}
