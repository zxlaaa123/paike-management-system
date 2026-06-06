# M-16 第8批收口报告 —— TeachingTask

日期：2026-06-03　分支：`refactor/m16-batch8-teaching-task-vo`

---

## 1. 特殊性（混合填充模式）

8 个 view 字段分两条路径填充：

- **Java fillTaskRelations**（4 字段）：courseName/teacherName/className 来自关联表 + scheduledSlots 来自 Schedule 计数。
- **XML selectConflictCheckById**（6 字段）：teacherName/teacherStatus/className/classStatus/studentCount/courseType — SQL 别名填充（与 fillTaskRelations 的 teacherName/className 重叠）。

`list()` 走 Entity mapper → `fromEntity` → VO → `fillTaskRelations(VO)`；`checkConflict()` 走 XML → VO 直返（XML resultType=VO + 别名直接映射）。

## 2. 改面

| 层 | 文件 | 改动 |
|---|---|---|
| ① VO | `TeachingTaskVo`（新建） | 20 字段（12 持久化 + 8 view）+ `fromEntity` |
| ② Entity | `TeachingTask` | 删 8 view 字段（`@TableField` import 保留） |
| ③ XML | `TeachingTaskMapper.xml` | `selectConflictCheckById` resultType→VO |
| ③ Mapper | `TeachingTaskMapper` | `selectConflictCheckById`→VO |
| ③ Service | `TeachingTaskService` | list/getById/create/update/listAll→VO，fillTaskRelations→List\<Vo\> |
| ④ Controller | `TeachingTaskController` | 5 端点→VO |
| ④ 消费方 | `ScheduleConflictService` | checkConflict 变量→VO |

测试：新增序列化测试（20 字段）+ 投资测试 18→**10**、2→**1**（仅剩 Schedule 10）+ `ScheduleConflictServiceTest` 同步 VO。

## 3. 收口

9 tests, 0 failures，全量 compile 零编译错误。

view 字段命中 **18→10**，承载 Entity **2→1**（仅剩 Schedule），M-16 进度 **7/9→8/9**。
