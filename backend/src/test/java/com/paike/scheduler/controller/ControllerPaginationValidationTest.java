package com.paike.scheduler.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControllerPaginationValidationTest {

    private static final List<Class<?>> PAGINATED_CONTROLLERS = List.of(
            AutoScheduleBatchController.class,
            ClassInfoController.class,
            ClassroomController.class,
            CourseController.class,
            ScheduleAdjustLogController.class,
            ScheduleConflictReportController.class,
            ScheduleController.class,
            SchedulePlanController.class,
            ScheduleScoreReportController.class,
            SemesterController.class,
            SystemAuditLogController.class,
            TeacherController.class,
            TeacherUnavailableTimeController.class,
            TeachingTaskController.class,
            UnscheduledTaskController.class
    );

    @Test
    void paginatedRequestParamsHaveMinMaxConstraints() {
        for (Class<?> controllerClass : PAGINATED_CONTROLLERS) {
            assertNotNull(controllerClass.getAnnotation(Validated.class),
                    controllerClass.getSimpleName() + " must enable request parameter validation");

            for (Method method : controllerClass.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    if (requestParam == null) {
                        continue;
                    }
                    if ("1".equals(requestParam.defaultValue())) {
                        Min min = parameter.getAnnotation(Min.class);
                        assertNotNull(min, controllerClass.getSimpleName() + "#" + method.getName() + " page param must have @Min");
                        assertEquals(1, min.value());
                    }
                    if ("10".equals(requestParam.defaultValue())) {
                        Min min = parameter.getAnnotation(Min.class);
                        Max max = parameter.getAnnotation(Max.class);
                        assertNotNull(min, controllerClass.getSimpleName() + "#" + method.getName() + " size param must have @Min");
                        assertEquals(1, min.value());
                        assertNotNull(max, controllerClass.getSimpleName() + "#" + method.getName() + " size param must have @Max");
                        assertEquals(200, max.value());
                    }
                }
            }
        }
    }
}
