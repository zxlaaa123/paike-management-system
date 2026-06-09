import { test, expect } from '@playwright/test'
import { apiHeaders, loginAndGoTo as openAuthenticatedPage, loginAsAdmin, type AuthState } from './helpers/auth'
import { deleteResourceIds, deleteSchedulesForClass } from './helpers/e2e-cleanup'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

let authState: AuthState | null = null
let ids: {
  teacher?: number; class?: number; room?: number; course?: number;
  task?: number; schedule?: number
} = {}

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

async function cleanupStageData(request: any) {
  if (!authState) {
    return
  }
  const h = authHeaders()
  await deleteSchedulesForClass(request, API_URL, h, ids.class)
  await deleteResourceIds(request, API_URL, h, '/api/teaching-tasks', [ids.task])
  await deleteResourceIds(request, API_URL, h, '/api/courses', [ids.course])
  await deleteResourceIds(request, API_URL, h, '/api/classrooms', [ids.room])
  await deleteResourceIds(request, API_URL, h, '/api/classes', [ids.class])
  await deleteResourceIds(request, API_URL, h, '/api/teachers', [ids.teacher])
  ids = {}
}

test.describe.serial('阶段 9：课表查询', () => {
  const ts = Date.now().toString().slice(-6)
  const T_NO = `T${ts}`
  const C_NAME = `计科${ts}班`
  const R_NAME = `A${ts}`
  const CO_NO = `C${ts}`

  test('1. 登录', async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
  })

  test.afterAll(async ({ request }) => {
    await cleanupStageData(request)
  })

  test('2. 准备基础数据并创建排课', async ({ request }) => {
    const h = authHeaders()

    const t = await (await request.post(`${API_URL}/api/teachers`, { headers: h, data: { teacherNo: T_NO, name: `张${ts}老师`, department: '计算机学院' } })).json()
    expect(t.code).toBe(200); ids.teacher = t.data.id

    const c = await (await request.post(`${API_URL}/api/classes`, { headers: h, data: { className: C_NAME, major: '计算机', grade: '2024', studentCount: 40 } })).json()
    expect(c.code).toBe(200); ids.class = c.data.id

    const r = await (await request.post(`${API_URL}/api/classrooms`, { headers: h, data: { roomName: R_NAME, building: '教学楼A', capacity: 60, roomType: 'NORMAL' } })).json()
    expect(r.code).toBe(200); ids.room = r.data.id

    const co = await (await request.post(`${API_URL}/api/courses`, { headers: h, data: { courseNo: CO_NO, courseName: '数据结构', courseType: 'NORMAL', totalHours: 64, weeklyHours: 4 } })).json()
    expect(co.code).toBe(200); ids.course = co.data.id

    const tk = await (await request.post(`${API_URL}/api/teaching-tasks`, { headers: h, data: { courseId: ids.course, teacherId: ids.teacher, classId: ids.class, weeklyHours: 4, needContinuous: 0, status: 1 } })).json()
    expect(tk.code).toBe(200); ids.task = tk.data.id

    // 创建2个时间段的数据（用不同天避免 ALLOW_SAME_COURSE_SAME_DAY=false 冲突）
    const slots = (await (await request.get(`${API_URL}/api/time-slots`, { headers: h })).json()).data

    const s1 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task, timeSlotId: slots[0].id, classroomId: ids.room } })).json()
    expect(s1.code).toBe(200)

    const s2 = await (await request.post(`${API_URL}/api/schedules`, { headers: h, data: { teachingTaskId: ids.task, timeSlotId: slots[4].id, classroomId: ids.room } })).json()
    expect(s2.code).toBe(200)
  })

  test('3. 班级课表 API', async ({ request }) => {
    const h = authHeaders()
    const res = await request.get(`${API_URL}/api/timetables/classes/${ids.class}`, { headers: h })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.length).toBe(2)
    expect(body.data[0].courseName).toBe('数据结构')
    expect(body.data[0].teacherName).toBe(`张${ts}老师`)
    expect(body.data[0].className).toBe(C_NAME)
    expect(body.data[0].classroomName).toBe(R_NAME)
    expect(body.data[0].dayOfWeek).toBe(1)
    expect(body.data[0].period).toBe(1)
    expect(body.data[0].timeSlotName).toBe('周一 第1-2节')
  })

  test('4. 教师课表 API', async ({ request }) => {
    const h = authHeaders()
    const res = await request.get(`${API_URL}/api/timetables/teachers/${ids.teacher}`, { headers: h })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.length).toBe(2)
    expect(body.data[0].courseName).toBe('数据结构')
    expect(body.data[0].className).toBe(C_NAME)
  })

  test('5. 教室课表 API', async ({ request }) => {
    const h = authHeaders()
    const res = await request.get(`${API_URL}/api/timetables/classrooms/${ids.room}`, { headers: h })
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.length).toBe(2)
    expect(body.data[0].courseName).toBe('数据结构')
    expect(body.data[0].classroomName).toBe(R_NAME)
  })

  test('6. 班级课表页面', async ({ page }) => {
    await loginAndGoTo(page, '/dashboard')

    // 展开课表查询子菜单
    await page.locator('.el-sub-menu__title').filter({ hasText: '课表查询' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '班级课表' }).click()
    await page.waitForURL('**/timetable/class', { timeout: 15000 })

    // 验证页面加载
    await expect(page.locator('label:has-text("选择班级")')).toBeVisible()

    // 选择班级
    await page.click('.el-form-item:has(label:has-text("选择班级")) .el-select')
    await page.waitForTimeout(500)
    await page.fill('.el-form-item:has(label:has-text("选择班级")) input', C_NAME)
    await page.waitForTimeout(500)
    await page.click(`.el-select-dropdown__item:has-text("${C_NAME}")`)
    await page.waitForTimeout(1000)

    // 验证课表显示
    await expect(page.locator(`text=${C_NAME} 课表`)).toBeVisible()
    await expect(page.locator('td:has-text("数据结构")').first()).toBeVisible({ timeout: 10000 })
  })

  test('7. 教师课表页面', async ({ page }) => {
    await loginAndGoTo(page, '/dashboard')

    await page.locator('.el-sub-menu__title').filter({ hasText: '课表查询' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '教师课表' }).click()
    await page.waitForURL('**/timetable/teacher', { timeout: 15000 })

    await expect(page.locator('label:has-text("选择教师")')).toBeVisible()

    await page.click('.el-form-item:has(label:has-text("选择教师")) .el-select')
    await page.waitForTimeout(500)
    await page.click('.el-form-item:has(label:has-text("选择教师")) .el-select')
    await page.waitForTimeout(500)
    // 用 filterable 搜索
    await page.fill('.el-form-item:has(label:has-text("选择教师")) input', `张${ts}`)
    await page.waitForTimeout(500)
    await page.click(`.el-select-dropdown__item:has-text("张${ts}老师")`)
    await page.waitForTimeout(1000)

    await expect(page.locator(`span:has-text("张${ts}老师 课表")`)).toBeVisible()
    await expect(page.locator('td:has-text("数据结构")').first()).toBeVisible({ timeout: 10000 })
  })

  test('8. 教室课表页面', async ({ page }) => {
    await loginAndGoTo(page, '/dashboard')

    await page.locator('.el-sub-menu__title').filter({ hasText: '课表查询' }).click()
    await page.waitForTimeout(500)
    await page.locator('.el-menu-item').filter({ hasText: '教室课表' }).click()
    await page.waitForURL('**/timetable/classroom', { timeout: 15000 })

    await expect(page.locator('label:has-text("选择教室")')).toBeVisible()

    await page.click('.el-form-item:has(label:has-text("选择教室")) .el-select')
    await page.waitForTimeout(500)
    await page.fill('.el-form-item:has(label:has-text("选择教室")) input', R_NAME)
    await page.waitForTimeout(500)
    await page.click(`.el-select-dropdown__item:has-text("${R_NAME}")`)
    await page.waitForTimeout(1000)

    await expect(page.locator(`span:has-text("${R_NAME} 课表")`)).toBeVisible()
    await expect(page.locator('td:has-text("数据结构")').first()).toBeVisible({ timeout: 10000 })
  })
})
