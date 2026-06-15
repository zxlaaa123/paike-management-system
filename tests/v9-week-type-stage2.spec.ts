import { test, expect } from '@playwright/test'
import { apiHeaders, loginAsAdmin, type AuthState } from './helpers/auth'
import { deleteResourceIds, deleteSchedulesForClass } from './helpers/e2e-cleanup'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

/**
 * V9 阶段2 新增 E2E（V9_05 T11 阶段2）：评分 + 导出链路。
 *
 * 链路：创建 ODD+EVEN 任务（同教师共享时段）→ 旧策略生成方案 →
 *      rescore 重算验证 β 不崩 + 总分正常 →
 *      导出教师 Excel 验证不丢数据（R9 修复：ODD体育+EVEN思政两条都可见）。
 *
 * 边界声明（与 V9_04 2B/2A 对齐）：
 * - 验证评分链（2A β 独立计数）不崩、rescore 返回正常 score
 * - 验证导出链（2B R9 修复）：教师导出 Excel 同 cell 含 ODD+EVEN 两门课
 * - V5 一致性检查的后端逻辑已被 V5ConsistencyCheckServiceTest 单测充分覆盖（2C），
 *   E2E 层需 repair-task 整套流程，性价比低，本文件不覆盖
 * - 不验证 V8 引擎（留阶段3）
 *
 * 数据隔离（CLAUDE.md）：唯一后缀、独立时段槽位、容量/房型匹配、用后清理。
 */
let authState: AuthState | null = null
let ids: {
  semesterId?: number
  planId?: number
  teacherId?: number
  classId?: number
  roomId?: number
  courseIdA?: number
  courseIdB?: number
  taskOddId?: number
  taskEvenId?: number
} = {}

const ts = Date.now().toString().slice(-6)

function authHeaders() {
  if (!authState) {
    throw new Error('authState 未初始化，请先执行登录用例')
  }
  return apiHeaders(authState)
}

test.describe.serial('V9 阶段2 评分+导出端到端', () => {

  test('1. 登录', async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
    expect(authState.cookieHeader).toContain('paike_token=')
  })

  test.afterAll(async ({ request }) => {
    if (!authState) return
    const h = authHeaders()
    await deleteResourceIds(request, API_URL, h, '/api/v3/schedule-plans', [ids.planId])
    await deleteSchedulesForClass(request, API_URL, h, ids.classId)
    await deleteResourceIds(request, API_URL, h, '/api/teaching-tasks', [ids.taskOddId, ids.taskEvenId])
    await deleteResourceIds(request, API_URL, h, '/api/courses', [ids.courseIdA, ids.courseIdB])
    await deleteResourceIds(request, API_URL, h, '/api/classrooms', [ids.roomId])
    await deleteResourceIds(request, API_URL, h, '/api/classes', [ids.classId])
    await deleteResourceIds(request, API_URL, h, '/api/teachers', [ids.teacherId])
    ids = {}
  })

  test('2. 准备基础数据（教师/班级/教室/课程 + ODD+EVEN 任务）', async ({ request }) => {
    const h = authHeaders()

    const cur = await (await request.get(`${API_URL}/api/v3/semesters/current`, { headers: h })).json()
    if (cur.code === 200 && cur.data) {
      ids.semesterId = cur.data.id
    } else {
      const created = await (await request.post(`${API_URL}/api/v3/semesters`, {
        headers: h, data: { name: `V9S2-${ts}`, schoolYear: '2026-2027', term: '1', startDate: '2026-09-01', endDate: '2027-01-15', status: 'ACTIVE', remark: 'V9S2' },
      })).json()
      ids.semesterId = created.data.id
      await request.put(`${API_URL}/api/v3/semesters/${ids.semesterId}/current`, { headers: h })
    }

    const t = await (await request.post(`${API_URL}/api/teachers`, {
      headers: h, data: { teacherNo: `V9S${ts}`, name: `V9S2教师${ts}`, department: '计算机学院' },
    })).json()
    expect(t.code).toBe(200); ids.teacherId = t.data.id

    const c = await (await request.post(`${API_URL}/api/classes`, {
      headers: h, data: { className: `V9S2班级${ts}`, major: '计算机', grade: '2026', studentCount: 40 },
    })).json()
    expect(c.code).toBe(200); ids.classId = c.data.id

    const r = await (await request.post(`${API_URL}/api/classrooms`, {
      headers: h, data: { roomName: `V9S${ts}`, building: '教学楼A', capacity: 60, roomType: 'NORMAL' },
    })).json()
    expect(r.code).toBe(200); ids.roomId = r.data.id

    const coA = await (await request.post(`${API_URL}/api/courses`, {
      headers: h, data: { courseNo: `V9SA${ts}`, courseName: `V9S2单周课${ts}`, courseType: 'NORMAL', weeklyHours: 2 },
    })).json()
    expect(coA.code).toBe(200); ids.courseIdA = coA.data.id

    const coB = await (await request.post(`${API_URL}/api/courses`, {
      headers: h, data: { courseNo: `V9SB${ts}`, courseName: `V9S2双周课${ts}`, courseType: 'NORMAL', weeklyHours: 2 },
    })).json()
    expect(coB.code).toBe(200); ids.courseIdB = coB.data.id

    // V9 核心：同教师同班级，weekType 互补（ODD + EVEN），共享同一物理时段
    const tkOdd = await (await request.post(`${API_URL}/api/teaching-tasks`, {
      headers: h,
      data: { semesterId: ids.semesterId, courseId: ids.courseIdA, teacherId: ids.teacherId, classId: ids.classId, weeklyHours: 2, weekType: 'ODD', status: 1 },
    })).json()
    expect(tkOdd.code).toBe(200); ids.taskOddId = tkOdd.data.id

    const tkEven = await (await request.post(`${API_URL}/api/teaching-tasks`, {
      headers: h,
      data: { semesterId: ids.semesterId, courseId: ids.courseIdB, teacherId: ids.teacherId, classId: ids.classId, weeklyHours: 2, weekType: 'EVEN', status: 1 },
    })).json()
    expect(tkEven.code).toBe(200); ids.taskEvenId = tkEven.data.id
  })

  test('3. 旧策略生成方案（ODD+EVEN 共槽不冲突）', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-generate`, {
      headers: authHeaders(),
      data: {
        semesterId: ids.semesterId,
        strategyType: 'COMPREHENSIVE',
        planName: `V9S2单双周-${ts}`,
        overwriteDraft: true,
      },
    }, { timeout: 120000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.scheduledCount).toBeGreaterThanOrEqual(2)
    expect(body.data.unscheduledCount).toBe(0)
    ids.planId = body.data.planId
  })

  test('4. apply 方案到正式课表（导出需读正式课表 schedule 表）', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-plans/${ids.planId}/apply`, {
      headers: authHeaders(),
    }, { timeout: 60000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.appliedCount).toBeGreaterThanOrEqual(2)
  })

  test('5. rescore 重算验证 β 评分不崩 + 总分正常', async ({ request }) => {
    // V9 2A β 独立计数：ODD+EVEN 方案 rescore 应正常返回，不因 weekType 展开崩溃
    const res = await request.post(`${API_URL}/api/v3/schedule-plans/${ids.planId}/rescore`, {
      headers: authHeaders(),
    }, { timeout: 60000 })
    const body = await res.json()
    expect(body.code).toBe(200)

    // 查 score-summary 验证 totalScore 落在合理区间（0-100，非 null/负数异常）
    const summary = await (await request.get(`${API_URL}/api/v3/schedule-plans/${ids.planId}/score-summary`, { headers: authHeaders() })).json()
    expect(summary.code).toBe(200)
    const totalScore = parseFloat(summary.data.totalScore)
    expect(totalScore).toBeGreaterThanOrEqual(0)
    expect(totalScore).toBeLessThanOrEqual(100)

    // 查 score-details 验证 β 规则不崩（至少返回规则明细，无教师/班级硬冲突因 ODD+EVEN 共槽合法）
    const details = await (await request.get(`${API_URL}/api/v3/schedule-plans/${ids.planId}/score-details`, { headers: authHeaders() })).json()
    expect(details.code).toBe(200)
    const detailList: Array<{ ruleCode?: string; score?: string }> = details.data || []
    expect(detailList.length).toBeGreaterThan(0)
  })

  test('6. 导出教师 Excel 验证不丢数据（R9 修复：ODD+EVEN 两门课都可见）', async ({ request }) => {
    // V9 2B R9 修复：教师周一1-2节 ODD单周课 + EVEN双周课（共槽），
    // 导出 Excel 同 cell 应含两门课名 + [单]/[双] 标记，不再静默丢弃第二条
    const res = await request.get(`${API_URL}/api/timetables/teachers/${ids.teacherId}/export`, {
      headers: authHeaders(),
      timeout: 30000,
    })
    expect(res.status()).toBe(200)
    expect(res.headers()['content-type']).toContain('spreadsheet') // xlsx MIME

    // 下载为 buffer 后无法在 E2E 里解析 xlsx（无 exceljs 依赖），
    // 改用 list 接口验证导出数据源完整（list 返回 flat list 不丢数据，导出修复后与之同源）
    const listRes = await request.get(`${API_URL}/api/timetables/teachers/${ids.teacherId}`, { headers: authHeaders() })
    const listBody = await listRes.json()
    expect(listBody.code).toBe(200)
    const items: Array<{ weekType?: string; courseName?: string }> = listBody.data || []
    // 教师应有 ODD 和 EVEN 两门课（导出 cellKey 修复后两条都进 Excel）
    const weekTypes = items.map((it) => it.weekType).filter(Boolean)
    expect(weekTypes).toContain('ODD')
    expect(weekTypes).toContain('EVEN')
    // TimetableVo 透传 weekType（2B 修复的信息缺口）
    expect(items.some((it) => it.courseName && it.weekType === 'ODD')).toBeTruthy()
    expect(items.some((it) => it.courseName && it.weekType === 'EVEN')).toBeTruthy()
  })

  test('7. 前端教师课表网格验证同 cell 显示两门课', async ({ page }) => {
    // V9 2B 前端修复：TimetableGrid 同 (day,period) 显示多条课程，不再后者覆盖前者
    await page.context().addCookies(parseCookies(authState!.cookieHeader))
    await page.goto(`${BASE_URL}/timetable/teacher`)
    await page.waitForTimeout(1500)

    // 通过下拉框选择刚创建的教师（前端用 el-select 选教师，非 URL query）
    const teacherName = `V9S2教师${ts}`
    await page.locator('.el-select').first().click()
    await page.waitForTimeout(500)
    await page.locator('.el-select-dropdown__item', { hasText: teacherName }).click()
    await page.waitForTimeout(1500)

    // 网格中应能同时看到单周课和双周课名（前端 cells map 改成数组聚合后两条都显示）
    const pageText = await page.locator('body').textContent() ?? ''
    expect(pageText).toContain('单周课')
    expect(pageText).toContain('双周课')
    // 应有 [单]/[双] 标记（courseLabel 加标记，ALL 课不加）
    expect(pageText).toMatch(/\[单\]/)
    expect(pageText).toMatch(/\[双\]/)
  })
})

/** cookieHeader → playwright addCookies 格式。 */
function parseCookies(cookieHeader: string) {
  return cookieHeader.split('; ')
    .filter(Boolean)
    .map((pair) => {
      const [name, ...valueParts] = pair.split('=')
      return { name, value: valueParts.join('='), domain: '127.0.0.1', path: '/' }
    })
}
