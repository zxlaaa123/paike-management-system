package com.paike.scheduler.service;

import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.service.vo.ErrorCodeVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeCatalogServiceTest {

    private final ErrorCodeCatalogService service = new ErrorCodeCatalogService();

    @Test
    void list_filtersByCategory() {
        List<ErrorCodeVo> authCodes = service.list("AUTH");

        assertTrue(authCodes.size() >= 3);
        assertTrue(authCodes.stream().allMatch(code -> "AUTH".equals(code.getCategory())));
        assertTrue(authCodes.stream().anyMatch(code -> "AUTH_UNAUTHORIZED".equals(code.getCode())));
    }

    @Test
    void getByCode_returnsDetailIgnoringCase() {
        ErrorCodeVo detail = service.getByCode("system_error");

        assertEquals("SYSTEM_ERROR", detail.getCode());
        assertEquals(500, detail.getNumericCode());
        assertEquals("SYSTEM", detail.getCategory());
    }

    @Test
    void getByCode_rejectsUnknownCode() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.getByCode("NO_SUCH_CODE"));

        assertEquals(404, error.getCode());
        assertEquals("错误码不存在", error.getMessage());
    }
}
