package com.paike.scheduler.controller;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControllerRequestStringValidationTest {

    private record FieldSize(Class<?> formClass, String fieldName, int max) {
    }

    private static final List<FieldSize> STRING_FIELD_SIZES = List.of(
            new FieldSize(ClassInfoController.ClassForm.class, "className", 100),
            new FieldSize(ClassInfoController.ClassForm.class, "major", 100),
            new FieldSize(ClassInfoController.ClassForm.class, "grade", 20),
            new FieldSize(ClassInfoController.ClassForm.class, "headTeacher", 50),
            new FieldSize(ClassInfoController.ClassForm.class, "remark", 255),
            new FieldSize(ClassroomController.ClassroomForm.class, "roomName", 100),
            new FieldSize(ClassroomController.ClassroomForm.class, "building", 100),
            new FieldSize(ClassroomController.ClassroomForm.class, "roomType", 30),
            new FieldSize(ClassroomController.ClassroomForm.class, "remark", 255),
            new FieldSize(CourseController.CourseForm.class, "courseNo", 50),
            new FieldSize(CourseController.CourseForm.class, "courseName", 100),
            new FieldSize(CourseController.CourseForm.class, "courseType", 30),
            new FieldSize(CourseController.CourseForm.class, "courseNature", 50),
            new FieldSize(CourseController.CourseForm.class, "remark", 255),
            new FieldSize(TeacherController.TeacherForm.class, "teacherNo", 50),
            new FieldSize(TeacherController.TeacherForm.class, "name", 50),
            new FieldSize(TeacherController.TeacherForm.class, "department", 100),
            new FieldSize(TeacherController.TeacherForm.class, "phone", 30),
            new FieldSize(TeacherController.TeacherForm.class, "remark", 255),
            new FieldSize(TeachingTaskController.TaskForm.class, "remark", 255)
    );

    @Test
    void requestStringFieldsHaveDatabaseAlignedSizeLimits() throws NoSuchFieldException {
        for (FieldSize expected : STRING_FIELD_SIZES) {
            Field field = expected.formClass().getDeclaredField(expected.fieldName());
            Size size = field.getAnnotation(Size.class);
            assertNotNull(size, expected.formClass().getSimpleName() + "." + expected.fieldName() + " must have @Size");
            assertEquals(expected.max(), size.max(), expected.formClass().getSimpleName() + "." + expected.fieldName());
        }
    }
}
