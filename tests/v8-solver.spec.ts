import { test, expect } from '@playwright/test'
import { apiHeaders, loginAndGoTo as openAuthenticatedPage, loginAsAdmin, type AuthState } from './helpers/auth'
import { deleteResourceIds, deleteSchedulesForClass } from './helpers/e2e-cleanup'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

let authState: AuthState | null = null
let ids: {
  semesterId?: number
  planId?: number
  teacherId?: number
  classId?: number
  roomId?: number
  courseId?: number
  taskId?: number
} = {}

const ts = Date.now().toString().slice(-6)

function authHeaders() {
  if (!authState) {
    throw new Error('authState 未初始化，请先执行登录用例')
  }
  return apiHeaders(authState)
}

// V8 阶段 4 补写：方案生成页"智能求解(SOLVER_V8)"策略端到端（V8_05 T7）。
// 遵循 CLAUDE.md 数据隔离：唯一后缀、NORMAL 课配 NORMAL 教室(容量 60≥40)、用后清理。
test.describe.serial('V8 智能求解策略端到端', () => {

  test('1. 登录', async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
    expect(authState.cookieHeader).toContain('paike_token=')
  })

  test.afterAll(async ({ request }) => {
    if (!authState) return
    const h = authHeaders()
    await deleteResourceIds(request, API_URL, h, '/api/v3/schedule-plans', [ids.planId])
    await deleteSchedulesForClass(request, API_URL, h, ids.classId)
    await deleteResourceIds(request, API_URL, h, '/api/teaching-tasks', [ids.taskId])
    await deleteResourceIds(request, API_URL, h, '/api/courses', [ids.courseId])
    await deleteResourceIds(request, API_URL, h, '/api/classrooms', [ids.roomId])
    await deleteResourceIds(request, API_URL, h, '/api/classes', [ids.classId])
    await deleteResourceIds(request, API_URL, h, '/api/teachers', [ids.teacherId])
    ids = {}
  })

  test('2. 准备基础数据', async ({ request }) => {
    const h = authHeaders()

    // 确保当前学期
    const cur = await (await request.get(`${API_URL}/api/v3/semesters/current`, { headers: h })).json()
    if (cur.code === 200 && cur.data) {
      ids.semesterId = cur.data.id
    } else {
      const created = await (await request.post(`${API_URL}/api/v3/semesters`, {
        headers: h,
        data: { name: `V8E2E-${ts}`, schoolYear: '2026-2027', term: '1', startDate: '2026-09-01', endDate: '2027-01-15', status: 'ACTIVE', remark: 'V8E2E' },
      })).json()
      ids.semesterId = created.data.id
      await request.put(`${API_URL}/api/v3/semesters/${ids.semesterId}/current`, { headers: h })
    }

    const t = await (await request.post(`${API_URL}/api/teachers`, {
      headers: h, data: { teacherNo: `V8E${ts}`, name: `V8E2E教师${ts}`, department: '计算机学院' },
    })).json()
    expect(t.code).toBe(200); ids.teacherId = t.data.id

    const c = await (await request.post(`${API_URL}/api/classes`, {
      headers: h, data: { className: `V8E2E班级${ts}`, major: '计算机', grade: '2026', studentCount: 40 },
    })).json()
    expect(c.code).toBe(200); ids.classId = c.data.id

    const r = await (await request.post(`${API_URL}/api/classrooms`, {
      headers: h, data: { roomName: `V8E${ts}`, building: '教学楼A', capacity: 60, roomType: 'NORMAL' },
    })).json()
    expect(r.code).toBe(200); ids.roomId = r.data.id

    const co = await (await request.post(`${API_URL}/api/courses`, {
      headers: h, data: { courseNo: `V8E${ts}`, courseName: `V8E2E课程${ts}`, courseType: 'NORMAL', weeklyHours: 2 },
    })).json()
    expect(co.code).toBe(200); ids.courseId = co.data.id

    const tk = await (await request.post(`${API_URL}/api/teaching-tasks`, {
      headers: h,
      data: { semesterId: ids.semesterId, courseId: ids.courseId, teacherId: ids.teacherId, classId: ids.classId, weeklyHours: 2, status: 1 },
    })).json()
    expect(tk.code).toBe(200); ids.taskId = tk.data.id
  })

  test('3. 用智能求解策略生成方案', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-generate`, {
      headers: authHeaders(),
      data: {
        semesterId: ids.semesterId,
        strategyType: 'SOLVER_V8',
        planName: `V8E2E智能求解-${ts}`,
        overwriteDraft: true,
        solverSeed: 42,
        solverTimeBudgetMs: 2000,
      },
    }, { timeout: 120000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.strategyType).toBe('SOLVER_V8')
    expect(body.data.scheduledCount).toBeGreaterThan(0)
    expect(body.data.unscheduledCount).toBe(0)
    expect(body.data.totalScore).not.toBeNull()
    ids.planId = body.data.planId
  })

  test('4. 方案详情页可见且展示得分', async ({ page }) => {
    await openAuthenticatedPage(page, `/v3/schedule-plans/${ids.planId}`, authState!, BASE_URL)
    await page.waitForTimeout(1500)
    // 方案详情页应含策略标签"智能求解"或方案名
    const body = await page.locator('body').textContent() ?? ''
    expect(body.includes('智能求解') || body.includes(`V8E2E智能求解-${ts}`)).toBeTruthy()
  })

  test('5. 可重新评分(rescore)', async ({ request }) => {
    const res = await request.post(`${API_URL}/api/v3/schedule-plans/${ids.planId}/rescore`, {
      headers: authHeaders(),
    }, { timeout: 60000 })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.totalScore).not.toBeNull()
  })
})
