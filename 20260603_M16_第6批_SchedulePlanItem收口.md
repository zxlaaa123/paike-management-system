# M-16 第6批收口报告 —— SchedulePlanItem

日期：2026-06-03　分支：`refactor/m16-batch6-plan-item-vo`

---

## 1. 背景

第4批原定 SchedulePlanItem，侦察发现 V5SimulationService compare 集群 + V5ConsistencyCheckService + V4ScheduleRiskService 深度耦合，改选 ScheduleAdjustLog。本批（第6批）在 alias 模式（第5批 UnscheduledTask）跑通后回锅。

## 2. 两世界分界（关键难点）

view 填充走 `SchedulePlanService.fillItemRelations` → `getPlanItems` 链。但 `SchedulePlanItem` 在同一项目中被两条路径使用：

- **VO 链（`getPlanItems` 填充 view）**：Controller getItems、V4ScheduleLockService.toVo、V5SimulationPlanDetailVo.items、V5SimulationService.loadCompareItems → indexByTeachingTaskId → buildItemChanges / buildLoadChanges / buildRoomUtilizationChanges / buildClassroomUtilizationChanges / resolveLockedCourseNames / buildItemChange 等 ~10 方法。
- **Entity 链（`planItemMapper.selectList`/`selectById`，view 恒 null）**：refreshPlanConflictState / buildConflictReasons / buildPeerConflictReasons、V4ScheduleRiskService.getPlanRisks → buildContext → 风险检测全流程、V5ConsistencyCheckService 一致性检查全流程、V5SimulationService.loadSourceItems / copyItems / copyDetachedItem / findBestLocalPlacement / appendAdjustLog 等。

**VO 链**改全类型 VO（`SchedulePlanItemVo`）+ `fillItemRelations` 填充；**Entity 链**删 view 字段后移除 view 字段回退逻辑（原逻辑 `entity.getCourseName()` 等为 view 字段回退值，但 Entity 链无 fillRelations → 恒 null，删字段后查 context map 等价）。

## 3. 改面

| 层 | 文件 | 改动 |
|---|---|---|
| ① VO | `service/vo/SchedulePlanItemVo.java`（新建） | 24 字段 + `fromEntity(Entity)` 静态工厂 |
| ② Entity | `entity/SchedulePlanItem.java` | 删 5 view 字段（`@TableField` import 保留——createdAt/updatedAt 用） |
| ③ Service | `SchedulePlanService.java` | `getPlanItems`→`List<Vo>`、`fillItemRelations`→`List<Vo>`、`planItemToVo`→委托 `fromEntity`；`refreshPlanConflictState` 等 Entity 链不动 |
| ④ Controller | `SchedulePlanController.java` | `getItems`→`Result<List<Vo>>` |
| ④ VO 嵌套 | `V5SimulationPlanDetailVo.java` | `items`→`List<Vo>`、删 entity import |
| ④ 锁服务 | `V4ScheduleLockService.java` | `planItemMap`→`Map<Long,Vo>`、`toVo` 参数→Vo |
| ④ 模拟服务 | `V5SimulationService.java` | VO 链 ~10 方法签名全改 VO + `hasPlacementChanged(Vo)` 重载 + 混合方法（buildChangedItems/buildItemChanges）Entity 形参保持 Entity 内部转 Vo |
| ④ 风险服务 | `V4ScheduleRiskService.java` | 4 处 view 字段回退删除（改查 context map 等价） |
| ④ 一致性服务 | `V5ConsistencyCheckService.java` | `fillItemContext` 删 view 字段回退逻辑 |

测试：新增 `M16PlanItemVoSerializationTest`（24 字段集 + 填充/null + deleted=0）+ 投资测试 25→**20**、4→**3**。

## 4. 收口

测试：21 tests, 0 failures（含 SchedulePlanService 4 + ScoreService 5 + Delta 3 + V4Risk 1 + V5Consistency 3 + Pagination 1 + 投资 2 + 序列化 2）。

view 字段命中 **25→20**，承载 Entity **4→3**，M-16 进度 **5/9→6/9**。
