import { test, expect } from '@playwright/test'
import { apiHeaders, loginAsAdmin, type AuthState } from './helpers/auth'
import { deleteResourceIds, deleteSchedulesForClass } from './helpers/e2e-cleanup'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

/**
 * V9 阶段1 新增 E2E：单双周（weekType）全链路（V9_05 T11 阶段1）。
 *
 * 链路：创建 ODD+EVEN 任务（同教师同班级，共享时段）→ 读回验证 weekType 输入源 →
 *      旧策略生成方案（验证 ODD+EVEN 共槽不冲突）→ 方案明细验证 weekType →
 *      方案详情页单/双/全部筛选 → apply → 正式课表验证 weekType 透传。
 *
 * 边界声明（与 V9_04 对齐）：
 * - 课表查询页（TimetableGrid）切换未实现，步骤6 用方案详情页切换替代（阶段0原型已实现）
 * - 不验证评分/导出/V4V5/V8引擎（留阶段2/3）
 * - 用 COMPREHENSIVE 旧策略，不用 SOLVER_V8（1B stub 会拒绝非 ALL 任务）
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

test.describe.serial('V9 单双周全链路端到端', () => {

  test('1. 登录', async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
    expect(authState.cookieHeader).toContain('paike_token=')
  })

  test.afterAll(async ({ request }) => {
    if (!authState) return
    const h = authHeaders()
    // 顺序：先删依赖 schedule 表的 plan，再清 schedule，再删 task/course/room/class/teacher
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

    // 确保当前学期
    const cur = await (await request.get(`${API_URL}/api/v3/semesters/current`, { headers: h })).json()
    if (cur.code === 200 && cur.data) {
      ids.semesterId = cur.data.id
    } else {
      const created = await (await request.post(`${API_URL}/api/v3/semesters`, {
        headers: h,
        data: { name: `V9E2E-${ts}`, schoolYear: '2026-2027', term: '1', startDate: '2026-09-01', endDate: '2027-01-15', status: 'ACTIVE', remark: 'V9E2E' },
      })).json()
      ids.semesterId = created.data.id
      await request.put(`${API_URL}/api/v3/semesters/${ids.semesterId}/current`, { headers: h })
    }

    const t = await (await request.post(`${API_URL}/api/teachers`, {
      headers: h, data: { teacherNo: `V9E${ts}`, name: `V9E2E教师${ts}`, department: '计算机学院' },
    })).json()
    expect(t.code).toBe(200); ids.teacherId = t.data.id

    const c = await (await request.post(`${API_URL}/api/classes`, {
      headers: h, data: { className: `V9E2E班级${ts}`, major: '计算机', grade: '2026', studentCount: 40 },
    })).json()
    expect(c.code).toBe(200); ids.classId = c.data.id

    const r = await (await request.post(`${API_URL}/api/classrooms`, {
      headers: h, data: { roomName: `V9E${ts}`, building: '教学楼A', capacity: 60, roomType: 'NORMAL' },
    })).json()
    expect(r.code).toBe(200); ids.roomId = r.data.id

    // 两门 NORMAL 课（单周一门、双周一门），各 2 学时=1 个大节
    const coA = await (await request.post(`${API_URL}/api/courses`, {
      headers: h, data: { courseNo: `V9A${ts}`, courseName: `V9E2E单周课${ts}`, courseType: 'NORMAL', weeklyHours: 2 },
    })).json()
    expect(coA.code).toBe(200); ids.courseIdA = coA.data.id

    const coB = await (await request.post(`${API_URL}/api/courses`, {
      headers: h, data: { courseNo: `V9B${ts}`, courseName: `V9E2E双周课${ts}`, courseType: 'NORMAL', weeklyHours: 2 },
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

  test('3. 读回任务验证 weekType 输入源', async ({ request }) => {
    const h = authHeaders()
    const odd = await (await request.get(`${API_URL}/api/teaching-tasks/${ids.taskOddId}`, { headers: h })).json()
    expect(odd.code).toBe(200)
    expect(odd.data.weekType).toBe('ODD')
    const even = await (await request.get(`${API_URL}/api/teaching-tasks/${ids.taskEvenId}`, { headers: h })).json()
    expect(even.code).toBe(200)
    expect(even.data.weekType).toBe('EVEN')
  })

  test('4. 旧策略生成方案（ODD+EVEN 共槽不冲突）', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-generate`, {
      headers: authHeaders(),
      data: {
        semesterId: ids.semesterId,
        strategyType: 'COMPREHENSIVE',
        planName: `V9E2E单双周-${ts}`,
        overwriteDraft: true,
      },
    }, { timeout: 120000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    // ODD+EVEN 共享时段，两条都应排下，共 2 个大节
    expect(body.data.scheduledCount).toBeGreaterThanOrEqual(2)
    expect(body.data.unscheduledCount).toBe(0)
    ids.planId = body.data.planId
  })

  test('5. 方案明细验证 weekType 写真实值', async ({ request }) => {
    const h = authHeaders()
    const items = await (await request.get(`${API_URL}/api/v3/schedule-plans/${ids.planId}/items`, { headers: h })).json()
    expect(items.code).toBe(200)
    const list: Array<{ weekType?: string }> = items.data || []
    const weekTypes = list.map((it) => it.weekType).filter(Boolean)
    // 应同时存在 ODD 和 EVEN 两条（1B toPlanItem 写真实值，非硬编码 ALL）
    expect(weekTypes).toContain('ODD')
    expect(weekTypes).toContain('EVEN')
  })

  test('6. 方案详情页单/双/全部筛选', async ({ page }) => {
    await page.context().addCookies(parseCookies(authState!.cookieHeader))
    await page.goto(`${BASE_URL}/v3/schedule-plans/${ids.planId}`)
    await page.waitForTimeout(1500)

    // 全部：含 ODD + EVEN，共 ≥2 条
    const allCount = await readItemCount(page)
    expect(allCount).toBeGreaterThanOrEqual(2)

    // Element Plus el-radio-button 的可点击层是 .el-radio-button__inner（span），
    // 隐藏的 input 会被 span 拦截点击事件，故按可见文字定位 span 而非 getByRole('radio')。
    await page.locator('.el-radio-button__inner', { hasText: '单周' }).click()
    await page.waitForTimeout(400)
    const oddCount = await readItemCount(page)
    expect(oddCount).toBeGreaterThanOrEqual(1)
    expect(oddCount).toBeLessThan(allCount)

    await page.locator('.el-radio-button__inner', { hasText: '双周' }).click()
    await page.waitForTimeout(400)
    const evenCount = await readItemCount(page)
    expect(evenCount).toBeGreaterThanOrEqual(1)

    // 切回全部应恢复总数（验证不是把数据筛没了）
    await page.locator('.el-radio-button__inner', { hasText: '全部' }).click()
    await page.waitForTimeout(400)
    const allCountAgain = await readItemCount(page)
    expect(allCountAgain).toBe(allCount)
  })

  test('7. apply 方案到正式课表', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-plans/${ids.planId}/apply`, {
      headers: authHeaders(),
    }, { timeout: 60000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.appliedCount).toBeGreaterThanOrEqual(2)
  })

  test('8. 正式课表验证 weekType 透传', async ({ request }) => {
    const h = authHeaders()
    // 按班级查正式课表，断言返回行含 ODD 和 EVEN（1C applyPlanInternal 透传，不再丢）
    const res = await request.get(`${API_URL}/api/schedules/class/${ids.classId}`, { headers: h })
    const body = await res.json()
    expect(body.code).toBe(200)
    const schedules: Array<{ weekType?: string; teachingTaskId?: number }> = body.data || []
    expect(schedules.length).toBeGreaterThanOrEqual(2)
    const weekTypes = schedules.map((s) => s.weekType).filter(Boolean)
    expect(weekTypes).toContain('ODD')
    expect(weekTypes).toContain('EVEN')
  })
})

/** 读取方案详情页"共 N 条"toolbar-count 的数值。 */
async function readItemCount(page: import('@playwright/test').Page): Promise<number> {
  const text = await page.locator('.toolbar-count').first().textContent() ?? ''
  const m = text.match(/(\d+)/)
  return m ? parseInt(m[1], 10) : -1
}

/** cookieHeader → playwright addCookies 格式。 */
function parseCookies(cookieHeader: string) {
  return cookieHeader.split('; ')
    .filter(Boolean)
    .map((pair) => {
      const [name, ...valueParts] = pair.split('=')
      return { name, value: valueParts.join('='), domain: '127.0.0.1', path: '/' }
    })
}
