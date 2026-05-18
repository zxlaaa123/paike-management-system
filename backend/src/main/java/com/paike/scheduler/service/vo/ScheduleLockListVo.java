package com.paike.scheduler.service.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ScheduleLockListVo {

    private Long planId;

    private String planName;

    private Integer lockedCount;

    private List<ScheduleLockItemVo> items = new ArrayList<>();
}
