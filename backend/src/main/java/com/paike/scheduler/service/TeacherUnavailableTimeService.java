package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TeacherUnavailableTime;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TeacherUnavailableTimeMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.TeacherUnavailableTimeForm;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherUnavailableTimeService {

    /**
     * 维护教师禁排时间。
     * 这些记录会在自动排课和冲突检测里被当作硬约束使用。
     */
    private final TeacherUnavailableTimeMapper unavailableTimeMapper;
    private final TeacherMapper teacherMapper;
    private final TimeSlotMapper timeSlotMapper;

    /**
     * 列表查询支持按教师、时间段和状态筛选，返回前补齐页面展示所需的关联字段。
     */
    public Page<TeacherUnavailableTime> list(Long teacherId, String teacherName, Long timeSlotId, Integer status, int page, int size) {
        LambdaQueryWrapper<TeacherUnavailableTime> wrapper = new LambdaQueryWrapper<TeacherUnavailableTime>()
                .eq(TeacherUnavailableTime::getDeleted, 0);

        if (teacherId != null) {
            wrapper.eq(TeacherUnavailableTime::getTeacherId, teacherId);
        }
        if (teacherName != null && !teacherName.isBlank()) {
            List<Teacher> teachers = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>()
                    .eq(Teacher::getDeleted, 0)
                    .like(Teacher::getName, teacherName));
            if (teachers.isEmpty()) {
                return new Page<>(page, size, 0);
            }
            List<Long> teacherIds = teachers.stream().map(Teacher::getId).collect(Collectors.toList());
            wrapper.in(TeacherUnavailableTime::getTeacherId, teacherIds);
        }
        if (timeSlotId != null) {
            wrapper.eq(TeacherUnavailableTime::getTimeSlotId, timeSlotId);
        }
        if (status != null) {
            wrapper.eq(TeacherUnavailableTime::getStatus, status);
        }

        wrapper.orderByDesc(TeacherUnavailableTime::getCreateTime);
        Page<TeacherUnavailableTime> result = unavailableTimeMapper.selectPage(new Page<>(page, size), wrapper);

        // 填充关联字段
        fillRelationFields(result.getRecords());
        return result;
    }

    /**
     * 页面表格直接展示教师姓名、部门、时间段名称，因此这里统一做一次关联字段回填。
     */
    private void fillRelationFields(List<TeacherUnavailableTime> records) {
        if (records.isEmpty()) return;

        List<Long> teacherIds = records.stream().map(TeacherUnavailableTime::getTeacherId).distinct().collect(Collectors.toList());
        List<Long> timeSlotIds = records.stream().map(TeacherUnavailableTime::getTimeSlotId).distinct().collect(Collectors.toList());

        Map<Long, Teacher> teacherMap = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>()
                        .in(Teacher::getId, teacherIds)).stream()
                .collect(Collectors.toMap(Teacher::getId, t -> t, (a, b) -> a));

        Map<Long, TimeSlot> timeSlotMap = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                        .in(TimeSlot::getId, timeSlotIds)).stream()
                .collect(Collectors.toMap(TimeSlot::getId, ts -> ts, (a, b) -> a));

        for (TeacherUnavailableTime record : records) {
            Teacher teacher = teacherMap.get(record.getTeacherId());
            if (teacher != null) {
                record.setTeacherName(teacher.getName());
                record.setDepartment(teacher.getDepartment());
            }
            TimeSlot timeSlot = timeSlotMap.get(record.getTimeSlotId());
            if (timeSlot != null) {
                record.setTimeSlotName(timeSlot.getTimeLabel());
                record.setDayOfWeek(timeSlot.getDayOfWeek());
                record.setPeriodNo(timeSlot.getPeriodNo());
            }
        }
    }

    /**
     * 禁排时间只允许配置到有效教师和有效时间段上。
     * 同一教师同一时间段只能有一条有效记录，避免冲突检测口径出现歧义。
     */
    @Transactional(rollbackFor = Exception.class)
    public TeacherUnavailableTime create(TeacherUnavailableTimeForm form) {
        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            throw new BusinessException("所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            throw new BusinessException("停用教师不能设置禁排时间");
        }

        // 校验时间段是否存在
        TimeSlot timeSlot = timeSlotMapper.selectById(form.getTimeSlotId());
        if (timeSlot == null) {
            throw new BusinessException("所选时间段不存在");
        }

        // 校验同一教师同一时间段不能重复
        long count = unavailableTimeMapper.selectCount(new LambdaQueryWrapper<TeacherUnavailableTime>()
                .eq(TeacherUnavailableTime::getTeacherId, form.getTeacherId())
                .eq(TeacherUnavailableTime::getTimeSlotId, form.getTimeSlotId())
                .eq(TeacherUnavailableTime::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException(teacher.getName() + "老师在" + timeSlot.getTimeLabel() + "已存在禁排时间");
        }

        TeacherUnavailableTime entity = new TeacherUnavailableTime();
        entity.setTeacherId(form.getTeacherId());
        entity.setTimeSlotId(form.getTimeSlotId());
        entity.setReason(form.getReason());
        entity.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        entity.setRemark(form.getRemark());
        entity.setDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        try {
            unavailableTimeMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, teacher.getName() + "老师在" + timeSlot.getTimeLabel() + "已存在禁排时间");
        }
        fillRelationFields(java.util.Collections.singletonList(entity));
        return entity;
    }

    /**
     * 更新时沿用创建时的业务约束，但重复校验需要排除当前记录自身。
     */
    @Transactional(rollbackFor = Exception.class)
    public TeacherUnavailableTime update(Long id, TeacherUnavailableTimeForm form) {
        TeacherUnavailableTime existing = unavailableTimeMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("禁排时间记录不存在");
        }

        // 校验教师是否存在且启用
        Teacher teacher = teacherMapper.selectById(form.getTeacherId());
        if (teacher == null || teacher.getDeleted() == 1) {
            throw new BusinessException("所选教师不存在");
        }
        if (teacher.getStatus() != 1) {
            throw new BusinessException("停用教师不能设置禁排时间");
        }

        // 校验时间段是否存在
        TimeSlot timeSlot = timeSlotMapper.selectById(form.getTimeSlotId());
        if (timeSlot == null) {
            throw new BusinessException("所选时间段不存在");
        }

        // 校验同一教师同一时间段不能重复（排除自身）
        long count = unavailableTimeMapper.selectCount(new LambdaQueryWrapper<TeacherUnavailableTime>()
                .eq(TeacherUnavailableTime::getTeacherId, form.getTeacherId())
                .eq(TeacherUnavailableTime::getTimeSlotId, form.getTimeSlotId())
                .eq(TeacherUnavailableTime::getDeleted, 0)
                .ne(TeacherUnavailableTime::getId, id));
        if (count > 0) {
            throw new BusinessException(teacher.getName() + "老师在" + timeSlot.getTimeLabel() + "已存在禁排时间");
        }

        existing.setTeacherId(form.getTeacherId());
        existing.setTimeSlotId(form.getTimeSlotId());
        existing.setReason(form.getReason());
        existing.setStatus(form.getStatus());
        existing.setRemark(form.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        unavailableTimeMapper.updateById(existing);
        fillRelationFields(java.util.Collections.singletonList(existing));
        return existing;
    }

    /**
     * 删除采用逻辑删除（通过 @TableLogic 自动处理），历史数据仍可用于审计和问题排查。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TeacherUnavailableTime existing = unavailableTimeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("禁排时间记录不存在");
        }
        unavailableTimeMapper.deleteById(id);
    }

    /**
     * 状态切换用于临时启停一条禁排规则，不影响记录本身的其他信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        TeacherUnavailableTime existing = unavailableTimeMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("禁排时间记录不存在");
        }
        existing.setStatus(status);
        existing.setUpdateTime(LocalDateTime.now());
        unavailableTimeMapper.updateById(existing);
    }

    /**
     * 查询指定教师在指定时间段是否禁排（供排课冲突检测调用）
     */
    public boolean isUnavailable(Long teacherId, Long timeSlotId) {
        long count = unavailableTimeMapper.selectCount(new LambdaQueryWrapper<TeacherUnavailableTime>()
                .eq(TeacherUnavailableTime::getTeacherId, teacherId)
                .eq(TeacherUnavailableTime::getTimeSlotId, timeSlotId)
                .eq(TeacherUnavailableTime::getStatus, 1)
                .eq(TeacherUnavailableTime::getDeleted, 0));
        return count > 0;
    }
}
