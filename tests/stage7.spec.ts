import { test, expect } from '@playwright/test'
import { apiHeaders, loginAndGoTo as openAuthenticatedPage, loginAsAdmin, type AuthState } from './helpers/auth'
import { deleteResourceIds, deleteSchedulesForClass } from './helpers/e2e-cleanup'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

let authState: AuthState | null = null
let ids: {
  t1?: number; t2?: number; c1?: number; c2?: number;
  rNormal?: number; rLab?: number; rComp?: number; rSmall?: number;
  coNormal?: number; coExp?: number; coComp?: number;
  task1?: number; task2?: number; task3?: number;
} = {}

// 每个冲突测试分配独立的时间段索引，避免互相干扰
const SLOT: Record<string, number> = {
  crud: 0,      // 周一1-2节 — CRUD测试
  teacher: 1,   // 周一3-4节 — 教师冲突
  class: 2,     // 周一5-6节 — 班级冲突
  room: 3,      // 周一7-8节 — 教室冲突
  capacity: 4,  // 周二1-2节 — 容量冲突
  expType: 5,   // 周二3-4节 — 实验课教室类型
  compType: 6,  // 周二5-6节 — 机房课教室类型
  disableT: 7,  // 周二7-8节 — 停用教师
  disableC: 8,  // 周三1-2节 — 停用班级
  disableR: 9,  // 周三3-4节 — 停用教室
  overHours: 10,// 周三5-6节 — 超课时
  check: 11,    // 周三7-8节 — 预检接口
  slots: 12,    // 周四1-2节 — scheduledSlots统计
}

function authHeaders() {
  if (!authState) {
    throw new Error('authState 未初始化，请先执行登录用例')
  }
  return apiHeaders(authState)
}

async function loginAndGoTo(page: any, path: string) {
  if (!authState) {
    throw new Error('authState 未初始化，请先执行登录用例')
  }
  await openAuthenticatedPage(page, path, authState, BASE_URL)
}

function scheduleRow(page: any, ...texts: string[]) {
  let row = page.locator('.el-table__body tr')
  for (const text of texts) {
    row = row.filter({ hasText: text })
  }
  return row.first()
}

test.describe.serial('阶段 7 & 8：手动排课与冲突检测', () => {
  const createdTaskIds: number[] = []
  const ts = Date.now().toString().slice(-6)
  const T1_NO = `T${ts}`
  const T2_NO = `T2${ts}`
  const C1 = `计科${ts}班`
  const C2 = `计科${ts}B班`
  const R_N = `N${ts}`
  const R_LAB = `LAB${ts}`
  const R_COMP = `COMP${ts}`
  const R_SMALL = `S${ts}`
  const CO_N = `CN${ts}`
  const CO_EXP = `CE${ts}`
  const CO_COMP = `CC${ts}`

  // ====== 基础数据准备 ======

  test('1. 登录', async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
  })

  test.afterAll(async ({ request }) => {
    if (!authState) {
      return
    }
    const h = authHeaders()
    await deleteSchedulesForClass(request, API_URL, h, ids.c1)
    await deleteSchedulesForClass(request, API_URL, h, ids.c2)
    await deleteResourceIds(request, API_URL, h, '/api/teaching-tasks', [ids.task1, ids.task2, ids.task3, ...createdTaskIds])
    await deleteResourceIds(request, API_URL, h, '/api/courses', [ids.coNormal, ids.coExp, ids.coComp])
    await deleteResourceIds(request, API_URL, h, '/api/classrooms', [ids.rNormal, ids.rLab, ids.rComp, ids.rSmall])
    await deleteResourceIds(request, API_URL, h, '/api/classes', [ids.c1, ids.c2])
    await deleteResourceIds(request, API_URL, h, '/api/teachers', [ids.t1, ids.t2])
    ids = {}
  })

  test('2. 时间段返回20条', async ({ request }) => {
    const res = await request.get(`${API_URL}/api/time-slots`, {
      headers: authHeaders(),
    })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data).toHaveLength(20)
  })

  test('3. 准备基础数据', async ({ request }) => {
    const h = authHeaders()

    const t1 = await (await request.post(`${API_URL}/api/teachers`, { headers: h, data: { teacherNo: T1_NO, name: '张老师', department: '计算机学院', phone: '13800138001' } })).json()
    expect(t1.code).toBe(200); ids.t1 = t1.data.id

    const t2 = await (await request.post(`${API_URL}/api/teachers`, { headers: h, data: { teacherNo: T2_NO, name: '李老师', department: '计算机学院', phone: '13800138002' } })).json()
    expect(t2.code).toBe(200); ids.t2 = t2.data.id

    const c1 = await (await request.post(`${API_URL}/api/classes`, { headers: h, data: { className: C1, major: '计算机科学与技术', grade: '2024', studentCount: 40, headTeacher: '张老师' } })).json()
    expect(c1.code).toBe(200); ids.c1 = c1.data.id

    const c2 = await (await request.post(`${API_URL}/api/classes`, { headers: h, data: { className: C2, major: '软件工程', grade: '2024', studentCount: 60, headTeacher: '李老师' } })).json()
    expect(c2.code).toBe(200); ids.c2 = c2.data.id

    const rn = await (await request.post(`${API_URL}/api/classrooms`, { headers: h, data: { roomName: R_N, building: '教学楼A', capacity: 60, roomType: 'NORMAL' } })).json()
    expect(rn.code).toBe(200); ids.rNormal = rn.data.id

    const rlb = await (await request.post(`${API_URL}/api/classrooms`, { headers: h, data: { roomName: R_LAB, building: '实验楼B', capacity: 60, roomType: 'LAB' } })).json()
    expect(rlb.code).toBe(200); ids.rLab = rlb.data.id

    const rcp = await (await request.post(`${API_URL}/api/classrooms`, { headers: h, data: { roomName: R_COMP, building: '机房楼C', capacity: 60, roomType: 'COMPUTER' } })).json()
    expect(rcp.code).toBe(200); ids.rComp = rcp.data.id

    const rs = await (await request.post(`${API_URL}/api/classrooms`, { headers: h, data: { roomName: R_SMALL, building: '教学楼D', capacity: 30, roomType: 'NORMAL' } })).json()
    expect(rs.code).toBe(200); ids.rSmall = rs.data.id

    const con = await (await request.post(`${API_URL}/api/courses`, { headers: h, data: { courseNo: CO_N, courseName: '数据结构', courseType: 'NORMAL', totalHours: 64, weeklyHours: 4 } })).json()
    expect(con.code).toBe(200); ids.coNormal = con.data.id

    const coe = await (await request.post(`${API_URL}/api/courses`, { headers: h, data: { courseNo: CO_EXP, courseName: '化学实验', courseType: 'EXPERIMENT', totalHours: 32, weeklyHours: 2 } })).json()
    expect(coe.code).toBe(200); ids.coExp = coe.data.id

    const coc = await (await request.post(`${API_URL}/api/courses`, { headers: h, data: { courseNo: CO_COMP, courseName: '程序设计实践', courseType: 'COMPUTER', totalHours: 32, weeklyHours: 2 } })).json()
    expect(coc.code).toBe(200); ids.coComp = coc.data.id
  })

  test('4. 创建教学任务', async ({ request }) => {
    const h = authHeaders()

    // task1: 张老师 + 计科班(40人) + 普通课 每周4节(需2大节)
    const tk1 = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coNormal, teacherId: ids.t1, classId: ids.c1, weeklyHours: 4, needContinuous: 0, status: 1 } })).json()
    expect(tk1.code).toBe(200); ids.task1 = tk1.data.id

    // task2: 李老师 + 计科B班(60人) + 实验课 每周2节(需1大节)
    const tk2 = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coExp, teacherId: ids.t2, classId: ids.c2, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(tk2.code).toBe(200); ids.task2 = tk2.data.id

    // task3: 张老师 + 计科B班(60人) + 机房课 每周2节(需1大节)
    const tk3 = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coComp, teacherId: ids.t1, classId: ids.c2, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(tk3.code).toBe(200); ids.task3 = tk3.data.id
  })

  // ====== 排课 CRUD ======

  test('5. 排课 CRUD', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.crud].id

    // 创建
    const cr = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(cr.code).toBe(200)
    expect(cr.data.courseName).toBe('数据结构')
    expect(cr.data.teacherName).toBe('张老师')
    expect(cr.data.className).toBe(C1)
    expect(cr.data.roomName).toBe(R_N)
    const sid = cr.data.id

    // 列表 / 按班级 / 按教师 / 按教室
    const list = await (await request.get(`${API_URL}/api/schedules?page=1&size=10`, { headers: h })).json()
    expect(list.code).toBe(200)
    expect(list.data.records.length).toBeGreaterThan(0)

    const byClass = await (await request.get(`${API_URL}/api/schedules/class/${ids.c1}`, { headers: h })).json()
    expect(byClass.code).toBe(200)
    expect(byClass.data.length).toBeGreaterThan(0)

    const byTeacher = await (await request.get(`${API_URL}/api/schedules/teacher/${ids.t1}`, { headers: h })).json()
    expect(byTeacher.code).toBe(200)
    expect(byTeacher.data.length).toBeGreaterThan(0)

    const byRoom = await (await request.get(`${API_URL}/api/schedules/classroom/${ids.rNormal}`, { headers: h })).json()
    expect(byRoom.code).toBe(200)
    expect(byRoom.data.length).toBeGreaterThan(0)

    // 删除
    const del = await (await request.delete(`${API_URL}/api/schedules/${sid}`, { headers: h })).json()
    expect(del.code).toBe(200)
  })

  // ====== 冲突检测（每个测试用独立时间段）======

  test('6. 冲突-教师：同一教师同一时间不能有两门课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.teacher].id

    // 先排 task1(张老师) 在 rLab(容量50,够40人)
    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rLab } })).json()
    expect(c1.code).toBe(200)
    const sid1 = c1.data.id

    // 创建另一个张老师的任务（张老师+计科班+普通课 每周2节）
    const nt = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coNormal, teacherId: ids.t1, classId: ids.c1, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(nt.code).toBe(200)
    createdTaskIds.push(nt.data.id)

    // 同教师同时间不同教室 → 冲突
    const c2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: nt.data.id, timeSlotId: slot, classroomId: ids.rComp } })).json()
    expect(c2.code).toBe(400)
    expect(c2.message).toContain('张老师')
    expect(c2.message).toContain('已有课程')

    // 清理
    await request.delete(`${API_URL}/api/schedules/${sid1}`, { headers: h })
  })

  test('7. 冲突-班级：同一班级同一时间不能有两门课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.class].id

    // 先排 task1(计科班40人) 在 rLab(容量50)
    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rLab } })).json()
    expect(c1.code).toBe(200)
    const sid1 = c1.data.id

    // 创建新任务：李老师 + 计科班(同班级40人) + 普通课
    const nt = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coNormal, teacherId: ids.t2, classId: ids.c1, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(nt.code).toBe(200)
    createdTaskIds.push(nt.data.id)

    // 同班级同时间不同教室 → 冲突
    const c2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: nt.data.id, timeSlotId: slot, classroomId: ids.rComp } })).json()
    expect(c2.code).toBe(400)
    expect(c2.message).toContain(C1)
    expect(c2.message).toContain('已有课程')

    await request.delete(`${API_URL}/api/schedules/${sid1}`, { headers: h })
  })

  test('8. 冲突-教室：同一教室同一时间不能安排两门课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.room].id

    // 先排 task1(计科班40人) 在 rLab(容量50)
    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rLab } })).json()
    expect(c1.code).toBe(200)
    const sid1 = c1.data.id

    // 创建新任务：李老师 + 计科B班(60人) + 普通课 — 不同班级，避免班级冲突
    const nt = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coNormal, teacherId: ids.t2, classId: ids.c2, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(nt.code).toBe(200)
    createdTaskIds.push(nt.data.id)

    // 同教室(rLab容量50,够计科B班60人? 不够! 换rNormal容量60)
    // 先改为 c1 用 rComp(机房50,够40人), nt 用 rComp → 教室冲突
    // 不对，c1 已经用了 rLab。让 nt 也在 rLab 创建，但 nt 是计科B班60人 > rLab容量50，会触发容量冲突
    // 最佳方案：c1 用 rNormal(60人够40人), nt 也在 rNormal → 教室冲突
    // 但 c1 已经创建了... 重新安排
    // 直接删除 c1 重新来
    await request.delete(`${API_URL}/api/schedules/${sid1}`, { headers: h })

    // 重新：task1(计科班40人) 在 rNormal(容量60)
    const c1b = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(c1b.code).toBe(200)
    const sid2 = c1b.data.id

    // nt(计科B班60人) 同教室 rNormal(容量60,够60人) 同时间 → 教室冲突
    const c2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: nt.data.id, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(c2.code).toBe(400)
    expect(c2.message).toContain(R_N)
    expect(c2.message).toContain('已被占用')

    await request.delete(`${API_URL}/api/schedules/${sid2}`, { headers: h })
  })

  test('9. 冲突-容量：班级人数>教室容量', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.capacity].id

    // task2 计科B班60人, rSmall容量30
    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: slot, classroomId: ids.rSmall } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain('人数')
    expect(r.message).toContain('容量')
  })

  test('10. 冲突-实验课必须安排实验室', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.expType].id

    // task2是实验课, 安排在普通教室rNormal
    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain('实验课必须安排在实验室')
  })

  test('11. 冲突-机房课必须安排机房', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.compType].id

    // task3是机房课, 安排在普通教室rNormal
    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task3, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain('机房课必须安排在机房')
  })

  test('12. 冲突-停用教师不能参与排课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.disableT].id

    // 停用李老师
    await request.put(`${API_URL}/api/teachers/${ids.t2}/status`, { headers: h, data: { status: 0 } })

    // task2 是李老师的
    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain('李老师')
    expect(r.message).toContain('停用')

    // 恢复
    await request.put(`${API_URL}/api/teachers/${ids.t2}/status`, { headers: h, data: { status: 1 } })
  })

  test('13. 冲突-停用班级不能参与排课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.disableC].id

    // 停用计科B班
    await request.put(`${API_URL}/api/classes/${ids.c2}/status`, { headers: h, data: { status: 0 } })

    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain(C2)
    expect(r.message).toContain('停用')

    // 恢复
    await request.put(`${API_URL}/api/classes/${ids.c2}/status`, { headers: h, data: { status: 1 } })
  })

  test('14. 冲突-停用教室不能参与排课', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.disableR].id

    // 停用rSmall
    await request.put(`${API_URL}/api/classrooms/${ids.rSmall}/status`, { headers: h, data: { status: 0 } })

    const r = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rSmall } })).json()
    expect(r.code).toBe(400)
    expect(r.message).toContain(R_SMALL)
    expect(r.message).toContain('停用')

    // 恢复
    await request.put(`${API_URL}/api/classrooms/${ids.rSmall}/status`, { headers: h, data: { status: 1 } })
  })

  test('15. 冲突-教学任务不能超过每周课时', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const s1 = slots[SLOT.overHours].id
    const s2 = slots[SLOT.overHours + 1].id

    // task2(计科B班60人+实验课) 每周2节 → 需1大节, 实验课必须用实验室rLab(容量60,够60人)
    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: s1, classroomId: ids.rLab } })).json()
    expect(c1.code).toBe(200)

    // 再排第2大节 → 超限
    const c2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task2, timeSlotId: s2, classroomId: ids.rLab } })).json()
    expect(c2.code).toBe(400)
    expect(c2.message).toContain('每周课时')

    // 清理
    await request.delete(`${API_URL}/api/schedules/${c1.data.id}`, { headers: h })
  })

  test('16. 冲突检测预检接口 /check-conflict', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data
    const slot = slots[SLOT.check].id

    // 先排一节课：task1(张老师) 在 rNormal
    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slot, classroomId: ids.rNormal } })).json()
    expect(c1.code).toBe(200)

    // 预检：同教师不同任务同时间 → 教师冲突
    // 创建新任务：张老师+计科班(40人)+普通课 每周2节, 用rComp(机房50,够40人)
    const nt2 = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.coNormal, teacherId: ids.t1, classId: ids.c1, weeklyHours: 2, needContinuous: 0, status: 1 } })).json()
    expect(nt2.code).toBe(200)
    createdTaskIds.push(nt2.data.id)
    const ck1 = await (await request.post(`${API_URL}/api/schedules/check-conflict`, { headers: h, data: { teachingTaskId: nt2.data.id, timeSlotId: slot, classroomId: ids.rComp } })).json()
    expect(ck1.code).toBe(200)
    if (!ck1.data.hasConflict) { console.log('ck1 no conflict, expected teacher conflict'); console.log('ck1:', ck1.data) }
    expect(ck1.data.hasConflict).toBe(true)
    expect(ck1.data.message).toContain('张老师')

    // 预检：不同时间+不同教室 → 无冲突 (用更远时段避免与前面测试遗留数据冲突)
    const ck2 = await (await request.post(`${API_URL}/api/schedules/check-conflict`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: slots[19].id, classroomId: ids.rComp } })).json()
    expect(ck2.code).toBe(200)
    expect(ck2.data.hasConflict).toBe(false)

    // 清理
    await request.delete(`${API_URL}/api/schedules/${c1.data.id}`, { headers: h })
  })

  test('17. 教学任务列表返回正确的 scheduledSlots', async ({ request }) => {
    const h = authHeaders()
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data

    // task1 每周4节 → 需2大节，用不同天避免 ALLOW_SAME_COURSE_SAME_DAY=false 冲突
    const s1 = slots[SLOT.slots].id // 周四1-2节
    const s2 = slots[SLOT.slots + 4].id // 周五1-2节，不同天

    const c1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: s1, classroomId: ids.rNormal } })).json()
    expect(c1.code).toBe(200)

    const c2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task1, timeSlotId: s2, classroomId: ids.rNormal } })).json()
    expect(c2.code).toBe(200)

    // 查询教学任务列表
    const tr = await (await request.get(`${API_URL}/api/teaching-tasks?page=1&size=20`, { headers: h })).json()
    expect(tr.code).toBe(200)
    const found = tr.data.records.find((t: any) => t.id === ids.task1)
    expect(found).toBeTruthy()
    expect(found.scheduledSlots).toBe(2)

    // 清理测试数据
    await request.delete(`${API_URL}/api/schedules/${c1.data.id}`, { headers: h })
    await request.delete(`${API_URL}/api/schedules/${c2.data.id}`, { headers: h })
  })

  // ====== 前端测试 ======

  test('18. 前端登录页', async ({ page }) => {
    // waitUntil: 'domcontentloaded' 避免 Vite 首次按需 chunk 加载拖慢 goto;
    // 30s 余量覆盖冷启动时序抖动(单跑稳定, 串行 52 用例末尾偶发首次 chunk 延迟)。
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('input[placeholder="请输入用户名"]')).toBeVisible()
    await expect(page.locator('input[placeholder="请输入密码"]')).toBeVisible()
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', '123456')
    await page.click('button:has-text("登录")')
    await page.waitForURL('**/dashboard', { timeout: 30000 })
    await expect(page.getByText('首页统计').first()).toBeVisible()
  })

  test('19. 前端排课页-列表', async ({ page }) => {
    await loginAndGoTo(page, '/schedule')
    await expect(page.locator('text=排课列表')).toBeVisible()
    await expect(page.locator('button:has-text("新增排课")')).toBeVisible()
  })

  test('20. 前端排课页-列表显示数据', async ({ page, request }) => {
    const h = authHeaders()
    const slotsRes = await request.get(`${API_URL}/api/time-slots`, { headers: h })
    const slotsBody = await slotsRes.json()
    expect(slotsBody.code).toBe(200)
    const slots = slotsBody.data
    expect(slots.length).toBeGreaterThanOrEqual(16)

    // API创建一条排课 (用task3 机房课+rComp, 避免task1课时超限)
    const cr = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task3, timeSlotId: slots[15].id, classroomId: ids.rComp } })).json()
    if (cr.code !== 200) { console.log('Schedule create failed:', cr.message || JSON.stringify(cr)) }
    expect(cr.code).toBe(200)

    await loginAndGoTo(page, '/dashboard')
    await page.locator('.el-sub-menu__title').filter({ hasText: '教学管理' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '手动排课' }).click()
    await page.waitForURL('**/schedule', { timeout: 15000 })

    const row = scheduleRow(page, '程序设计实践', R_COMP)
    await expect(row).toBeVisible({ timeout: 10000 })
  })

  test('21. 前端排课页-删除排课', async ({ page }) => {
    await loginAndGoTo(page, '/dashboard')
    await page.locator('.el-sub-menu__title').filter({ hasText: '教学管理' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '手动排课' }).click()
    await page.waitForURL('**/schedule', { timeout: 15000 })
    await page.waitForTimeout(1000)

    const row = scheduleRow(page, '程序设计实践', R_COMP)
    await expect(row).toBeVisible({ timeout: 10000 })

    await row.locator('button:has-text("删除")').click()
    await page.waitForTimeout(500)
    // Element Plus 确认弹窗按钮可能是 "OK"/"Cancel" (英文) 或 "确定"/"取消" (中文)
    const okBtn = page.locator('.el-message-box__btns button:has-text("OK"), .el-message-box__btns button:has-text("确定")')
    await okBtn.first().click()
    await expect(page.locator('.el-message-box')).not.toBeVisible({ timeout: 10000 })
  })

  test('22. 前端排课页-新增排课弹窗', async ({ page }) => {
    await loginAndGoTo(page, '/dashboard')
    await page.locator('.el-sub-menu__title').filter({ hasText: '教学管理' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '手动排课' }).click()
    await page.waitForURL('**/schedule', { timeout: 15000 })

    await page.click('button:has-text("新增排课")')
    await expect(page.locator('div[aria-label="新增排课"]')).toBeVisible()
    const dialog = page.locator('div[aria-label="新增排课"]')
    await expect(dialog.locator('label:has-text("教学任务")')).toBeVisible()
    await expect(dialog.locator('label:has-text("时间段")')).toBeVisible()
    await expect(dialog.locator('label:has-text("教室")')).toBeVisible()

    // 选择教学任务
    await page.click('.el-form-item:has(label:has-text("教学任务")) .el-select')
    await page.waitForTimeout(500)
    // 选项可能包含课程名
    const option = page.locator('.el-select-dropdown__item:has-text("数据结构"), .el-select-dropdown__item:has-text("程序设计实践")')
    await option.first().click()

    const cancelBtn = page.locator('button:has-text("取消"), button:has-text("Cancel")')
    await cancelBtn.first().click()
    await expect(page.locator('div[aria-label="新增排课"]')).not.toBeVisible({ timeout: 5000 })
  })
})
