package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceTransactionAnnotationTest {

    @Test
    void smallWriteMethodsDeclareRollbackTransactions() throws NoSuchMethodException {
        assertRollbackForException(ScheduleRuleWeightService.class, "updateWeight",
                Long.class, BigDecimal.class, Integer.class, String.class);
        assertRollbackForException(TeacherUnavailableTimeService.class, "delete", Long.class);
    }

    private void assertRollbackForException(Class<?> serviceClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Transactional transactional = serviceClass.getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional, serviceClass.getSimpleName() + "#" + methodName + " must be transactional");
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }
}
