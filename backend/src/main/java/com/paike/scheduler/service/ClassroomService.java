package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.mapper.ClassroomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomMapper classroomMapper;

    public Page<Classroom> list(String roomName, String building, String roomType, Integer status, int page, int size) {
        LambdaQueryWrapper<Classroom> wrapper = new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getDeleted, 0);
        if (roomName != null && !roomName.isBlank()) {
            wrapper.like(Classroom::getRoomName, roomName);
        }
        if (building != null && !building.isBlank()) {
            wrapper.like(Classroom::getBuilding, building);
        }
        if (roomType != null && !roomType.isBlank()) {
            wrapper.eq(Classroom::getRoomType, roomType);
        }
        if (status != null) {
            wrapper.eq(Classroom::getStatus, status);
        }
        wrapper.orderByDesc(Classroom::getCreateTime);
        return classroomMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Classroom getById(Long id) {
        Classroom classroom = classroomMapper.selectById(id);
        if (classroom == null || Integer.valueOf(1).equals(classroom.getDeleted())) {
            throw new BusinessException(404, "教室不存在");
        }
        return classroom;
    }

    @Transactional(rollbackFor = Exception.class)
    public Classroom create(Classroom classroom) {
        long count = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getRoomName, classroom.getRoomName())
            .eq(Classroom::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException(400, "教室名称已存在");
        }
        classroom.setDeleted(0);
        classroom.setCreateTime(LocalDateTime.now());
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.insert(classroom);
        return classroom;
    }

    @Transactional(rollbackFor = Exception.class)
    public Classroom update(Long id, Classroom classroom) {
        Classroom existing = getById(id);
        long count = classroomMapper.selectCount(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getRoomName, classroom.getRoomName())
            .eq(Classroom::getDeleted, 0)
            .ne(Classroom::getId, id));
        if (count > 0) {
            throw new BusinessException(400, "教室名称已存在");
        }
        existing.setRoomName(classroom.getRoomName());
        existing.setBuilding(classroom.getBuilding());
        existing.setCapacity(classroom.getCapacity());
        existing.setRoomType(classroom.getRoomType());
        existing.setStatus(classroom.getStatus());
        existing.setRemark(classroom.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        classroomMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        classroomMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Classroom classroom = getById(id);
        classroom.setStatus(status);
        classroom.setUpdateTime(LocalDateTime.now());
        classroomMapper.updateById(classroom);
    }

    public List<Classroom> listAll() {
        return classroomMapper.selectList(new LambdaQueryWrapper<Classroom>()
            .eq(Classroom::getDeleted, 0)
            .eq(Classroom::getStatus, 1)
            .orderByAsc(Classroom::getRoomName));
    }
}
