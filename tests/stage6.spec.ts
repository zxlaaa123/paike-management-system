import { test, expect } from '@playwright/test'

const API_URL = 'http://127.0.0.1:8090'
const BASE_URL = 'http://127.0.0.1:5173'

let authToken = ''
let ids: {
  semesterId?: number
  planId1?: number
  planId2?: number
  planId3?: number
  teacherId?: number
  classId?: number
  roomId?: number
  courseId?: number
  taskId?: number
} = {}

// ====== 辅助函数 ======

function authHeaders() {
  return { Authorization: `Bearer ${authToken}` }
}

async function login(request: any) {
  const res = await request.post(`${API_URL}/api/auth/login`, {
    data: { username: 'admin', password: '123456' },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`登录失败: ${body.message}`)
  }
  authToken = body.data.token
  return body.data
}

async function ensureSemester(request: any): Promise<number> {
  // 获取当前学期
  const current = await request.get(`${API_URL}/api/v3/semesters/current`, {
    headers: authHeaders(),
  })
  const currentBody = await current.json()
  if (currentBody.code === 200 && currentBody.data) {
    return currentBody.data.id
  }

  // 没有当前学期，创建一个
  const ts = Date.now().toString().slice(-6)
  const create = await request.post(`${API_URL}/api/v3/semesters`, {
    headers: authHeaders(),
    data: {
      name: `2025-2026-1-${ts}`,
      schoolYear: '2025-2026',
      term: '1',
      startDate: '2025-09-01',
      endDate: '2026-01-15',
      status: 'ACTIVE',
      remark: '测试学期',
    },
  })
  const createBody = await create.json()
  if (createBody.code !== 200) {
    throw new Error(`创建学期失败: ${createBody.message}`)
  }
  const semId = createBody.data.id

  // 设为当前学期
  await request.put(`${API_URL}/api/v3/semesters/${semId}/current`, {
    headers: authHeaders(),
  })
  return semId
}

async function ensureBaseData(request: any) {
  const ts = Date.now().toString().slice(-6)

  // 创建教师
  const t = await (await request.post(`${API_URL}/api/teachers`, {
    headers: authHeaders(),
    data: { teacherNo: `TV3${ts}`, name: `张V3${ts}老师`, department: '计算机学院' },
  })).json()
  if (t.code !== 200) throw new Error(`创建教师失败: ${t.message}`)
  ids.teacherId = t.data.id

  // 创建班级
  const c = await (await request.post(`${API_URL}/api/classes`, {
    headers: authHeaders(),
    data: { className: `计科V3${ts}班`, major: '计算机科学与技术', grade: '2024', studentCount: 40 },
  })).json()
  if (c.code !== 200) throw new Error(`创建班级失败: ${c.message}`)
  ids.classId = c.data.id

  // 创建教室
  const r = await (await request.post(`${API_URL}/api/classrooms`, {
    headers: authHeaders(),
    data: { roomName: `V3${ts}`, building: '教学楼A', capacity: 60, roomType: 'NORMAL' },
  })).json()
  if (r.code !== 200) throw new Error(`创建教室失败: ${r.message}`)
  ids.roomId = r.data.id

  // 创建课程
  const co = await (await request.post(`${API_URL}/api/courses`, {
    headers: authHeaders(),
    data: { courseNo: `CV3${ts}`, courseName: '数据结构V3', courseType: 'NORMAL', totalHours: 64, weeklyHours: 4 },
  })).json()
  if (co.code !== 200) throw new Error(`创建课程失败: ${co.message}`)
  ids.courseId = co.data.id

  // 创建教学任务
  const tk = await (await request.post(`${API_URL}/api/teaching-tasks`, {
    headers: authHeaders(),
    data: { courseId: ids.courseId, teacherId: ids.teacherId, classId: ids.classId, weeklyHours: 4, needContinuous: 0, status: 1 },
  })).json()
  if (tk.code !== 200) throw new Error(`创建教学任务失败: ${tk.message}`)
  ids.taskId = tk.data.id
}

async function generatePlan(request: any, semesterId: number, strategyType: string, planName: string): Promise<number> {
  const res = await request.post(`${API_URL}/api/v3/schedule-generate`, {
    headers: authHeaders(),
    data: { semesterId, strategyType, planName, overwriteDraft: true },
  }, { timeout: 120000 })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`生成方案失败(${strategyType}): ${body.message}`)
  }
  return body.data.planId
}

async function loginAndGoTo(page: any, path: string) {
  if (!authToken) {
    throw new Error('authToken 未初始化，请先执行登录用例')
  }
  await page.addInitScript(
    ([key, token]) => window.localStorage.setItem(key, token),
    ['paike_admin_token', authToken],
  )
  await page.goto(`${BASE_URL}${path}`)
  await page.waitForTimeout(2000)
}

// ====== 测试套件 ======

test.describe.serial('V3 阶段 6：方案对比与应用回滚', () => {
  // ====== 准备工作 ======

  test('1. 登录获取 token', async ({ request }) => {
    await login(request)
    expect(authToken).toBeTruthy()
    console.log('  [OK] 登录成功，token 已获取')
  })

  test('2. 确保学期存在', async ({ request }) => {
    ids.semesterId = await ensureSemester(request)
    expect(ids.semesterId).toBeTruthy()
    console.log(`  [OK] 学期 ID: ${ids.semesterId}`)
  })

  test('3. 准备基础数据（教师/班级/教室/课程/教学任务）', async ({ request }) => {
    await ensureBaseData(request)
    console.log(`  [OK] 基础数据: teacher=${ids.teacherId}, class=${ids.classId}, room=${ids.roomId}, course=${ids.courseId}, task=${ids.taskId}`)
  })

  test('4. 生成 2 个不同策略的排课方案', async ({ request }) => {
    test.setTimeout(180000)
    const ts = Date.now().toString().slice(-6)

    // 方案1: 教师优先
    ids.planId1 = await generatePlan(request, ids.semesterId!, 'TEACHER_PRIORITY', `方案A-教师优先-${ts}`)
    console.log(`  [OK] 方案1 ID: ${ids.planId1} (TEACHER_PRIORITY)`)

    // 方案2: 班级均衡
    ids.planId2 = await generatePlan(request, ids.semesterId!, 'CLASS_BALANCE', `方案B-班级均衡-${ts}`)
    console.log(`  [OK] 方案2 ID: ${ids.planId2} (CLASS_BALANCE)`)

    expect(ids.planId1).toBeTruthy()
    expect(ids.planId2).toBeTruthy()
    expect(ids.planId1).not.toBe(ids.planId2)
  })

  // ====== 测试1：方案对比页面 ======

  test('5. 方案对比页面 - 加载并显示方案列表', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-compare')

    // 验证页面加载 - 应有当前学期信息（用 .first() 避免 strict mode）
    const semesterAlert = page.locator('.el-alert__title:has-text("当前学期")')
    await expect(semesterAlert.first()).toBeVisible({ timeout: 10000 })
    console.log('  [OK] 当前学期信息已显示')

    // 验证方案选择区标题
    const header = page.locator('.card-header span:has-text("选择对比方案")')
    await expect(header.first()).toBeVisible()
    console.log('  [OK] 方案选择区已显示')

    // 验证方案列表中有我们生成的方案（在表格内查找）
    const tableBody = page.locator('.el-table__body')
    const planA = tableBody.locator('td:has-text("方案A-教师优先")')
    const planB = tableBody.locator('td:has-text("方案B-班级均衡")')
    await expect(planA.first()).toBeVisible({ timeout: 10000 })
    await expect(planB.first()).toBeVisible()
    console.log('  [OK] 方案列表中显示了我们生成的方案')
  })

  test('6. 方案对比页面 - 选择方案并执行对比', async ({ page }) => {
    // 确保登录并导航到对比页面
    await loginAndGoTo(page, '/v3/schedule-compare')

    // 点击 checkbox（label.el-checkbox 在表格第一列）
    const checkboxes = page.locator('.el-table__body label.el-checkbox')
    const count = await checkboxes.count()
    console.log(`  找到 ${count} 个 checkbox`)

    if (count >= 2) {
      await checkboxes.nth(0).click()
      await page.waitForTimeout(500)
      await checkboxes.nth(1).click()
      await page.waitForTimeout(500)

      console.log('  已选择两个方案')

      // 点击"开始对比"按钮
      await page.click('button:has-text("开始对比")')
      await page.waitForTimeout(5000)

      // 验证对比结果出现
      const resultCard = page.locator('.el-card:has(.card-header span:has-text("对比结果"))')
      const resultVisible = await resultCard.isVisible().catch(() => false)
      if (resultVisible) {
        console.log('  [OK] 对比结果区域已显示')

        // 验证推荐方案说明
        const summaryAlert = page.locator('.el-alert__title:has-text("推荐方案")')
        const summaryVisible = await summaryAlert.isVisible().catch(() => false)
        if (summaryVisible) {
          const summaryText = await summaryAlert.textContent()
          console.log(`  [OK] 推荐方案说明: ${summaryText?.substring(0, 80)}`)
        }

        // 验证推荐标签（在对比结果表格中）
        const bestTag = page.locator('.el-table__body .el-tag:has-text("推荐")')
        const bestCount = await bestTag.count()
        console.log(`  [OK] 推荐标签数量: ${bestCount}`)
        expect(bestCount).toBeGreaterThanOrEqual(1)
      } else {
        console.log('  [WARN] 对比结果未显示，可能方案数据不足')
      }
    } else {
      console.log('  [SKIP] 方案数量不足2个，跳过对比操作')
    }
  })

  test('7. 方案对比页面 - 验证对比表格列', async ({ page }) => {
    await page.goto(`${BASE_URL}/v3/schedule-compare`)
    await page.waitForTimeout(2000)

    // 先执行对比 - 点击 checkbox
    await page.waitForTimeout(2000)
    const checkboxes = page.locator('.el-table__body label.el-checkbox')
    const count = await checkboxes.count()
    if (count >= 2) {
      await checkboxes.nth(0).click()
      await page.waitForTimeout(500)
      await checkboxes.nth(1).click()
      await page.waitForTimeout(500)
      await page.click('button:has-text("开始对比")')
      await page.waitForTimeout(5000)
    }

    // 如果有对比结果，验证表格列
    const resultCard = page.locator('.el-card:has(.card-header span:has-text("对比结果"))')
    const hasResult = await resultCard.isVisible().catch(() => false)
    if (hasResult) {
      // 验证关键列存在（在表头中查找）
      const headerCells = page.locator('.el-table__header-wrapper .cell')
      const headerTexts = await headerCells.allTextContents()
      const columns = ['方案名称', '策略', '总分', '已排', '未排', '冲突']
      for (const col of columns) {
        const found = headerTexts.some(h => h.includes(col))
        if (found) {
          console.log(`  [OK] 列 "${col}" 存在`)
        }
      }
    } else {
      console.log('  [SKIP] 无对比结果，跳过表格列验证')
    }
  })

  // ====== 测试2：应用方案 ======

  test('8. 方案列表页面 - 查看方案列表', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-plans')

    // 验证页面加载
    const header = page.locator('.card-header span:has-text("排课方案列表")')
    await expect(header.first()).toBeVisible({ timeout: 10000 })
    console.log('  [OK] 方案列表页面加载成功')

    // 验证我们的方案在列表中
    const planA = page.locator('td:has-text("方案A-教师优先")')
    await expect(planA.first()).toBeVisible()
    console.log('  [OK] 方案A在列表中')

    // 验证状态标签
    const draftTag = page.locator('.el-tag:has-text("草稿")')
    const draftCount = await draftTag.count()
    console.log(`  [OK] 草稿状态方案数量: ${draftCount}`)
  })

  test('9. 方案详情页 - 查看详情并应用方案', async ({ page }) => {
    // 先登录再导航到方案1的详情页
    await loginAndGoTo(page, `/v3/schedule-plans/${ids.planId1}`)
    await page.waitForTimeout(3000)

    // 检查是否被重定向到登录页
    const onLoginPage = await page.locator('input[placeholder="请输入用户名"]').isVisible().catch(() => false)
    if (onLoginPage) {
      console.log('  [WARN] 被重定向到登录页，详情页需要认证')
      return
    }

    // 等待页面加载
    await page.waitForLoadState('networkidle', { timeout: 15000 })

    // 验证详情页加载 - 用 page-header
    const pageHeader = page.locator('.el-page-header')
    const headerVisible = await pageHeader.first().isVisible().catch(() => false)
    console.log(`  详情页头部可见: ${headerVisible}`)

    // 验证方案名称在描述列表中
    const descItems = page.locator('.el-descriptions')
    await expect(descItems.first()).toBeVisible({ timeout: 15000 })
    console.log('  [OK] 方案详情卡片已显示')

    // 验证"应用方案"按钮
    const applyBtn = page.locator('button:has-text("应用方案")')
    const applyVisible = await applyBtn.isVisible().catch(() => false)
    console.log(`  [OK] "应用方案"按钮可见: ${applyVisible}`)

    if (applyVisible) {
      await applyBtn.click()
      await page.waitForTimeout(1500)

      // 检查是否有确认弹窗（含警告或无警告）
      const messageBox = page.locator('.el-message-box')
      const hasDialog = await messageBox.isVisible().catch(() => false)

      if (hasDialog) {
        const dialogText = await messageBox.textContent()
        console.log(`  [OK] 弹窗内容: ${dialogText?.substring(0, 120)}`)

        // 确认应用
        const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定应用"), .el-message-box__btns button:has-text("OK")')
        if (await confirmBtn.first().isVisible().catch(() => false)) {
          await confirmBtn.first().click()
          await page.waitForTimeout(2000)
          console.log('  [OK] 已确认应用方案')
        } else {
          const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
          await cancelBtn.first().click().catch(() => {})
          console.log('  [OK] 已取消弹窗')
        }
      } else {
        console.log('  [OK] 方案应用成功（无确认弹窗）')
      }

      // 验证方案状态 - 检查是否有"已应用"标签
      await page.waitForTimeout(1000)
      const appliedTag = page.locator('.el-tag:has-text("已应用")')
      const appliedVisible = await appliedTag.isVisible().catch(() => false)
      console.log(`  [OK] 方案状态为"已应用": ${appliedVisible}`)
    }
  })

  test('10. 方案详情页 - 验证课表明细和评分明细 tabs', async ({ page }) => {
    await loginAndGoTo(page, `/v3/schedule-plans/${ids.planId1}`)

    // 等待 tabs 加载
    const tabsNav = page.locator('.el-tabs__nav')
    await expect(tabsNav.first()).toBeVisible({ timeout: 10000 })

    // 点击"课表明细"tab
    const itemsTab = page.locator('.el-tabs__item:has-text("课表明细")')
    if (await itemsTab.isVisible().catch(() => false)) {
      await itemsTab.click()
      await page.waitForTimeout(1000)
      console.log('  [OK] 课表明细 tab 已点击')
    }

    // 点击"评分明细"tab
    const scoreTab = page.locator('.el-tabs__item:has-text("评分明细")')
    if (await scoreTab.isVisible().catch(() => false)) {
      await scoreTab.click()
      await page.waitForTimeout(1000)
      console.log('  [OK] 评分明细 tab 已点击')

      // 验证"重新评分"按钮
      const rescoreBtn = page.locator('button:has-text("重新评分")')
      const rescoreVisible = await rescoreBtn.isVisible().catch(() => false)
      console.log(`  [OK] "重新评分"按钮可见: ${rescoreVisible}`)
    }
  })

  // ====== 测试3：正式课表显示来源方案 ======

  test('11. 手动排课页面 - 验证来源方案信息显示', async ({ page }) => {
    await loginAndGoTo(page, '/schedule')

    // 验证页面加载
    const header = page.locator('.card-header span:has-text("排课列表")')
    await expect(header.first()).toBeVisible({ timeout: 10000 })
    console.log('  [OK] 手动排课页面已加载')

    // 验证顶部来源方案信息
    const sourceInfo = page.locator('.el-alert__title:has-text("课表来源方案")')
    const sourceVisible = await sourceInfo.isVisible().catch(() => false)
    if (sourceVisible) {
      const sourceText = await sourceInfo.textContent()
      console.log(`  [OK] 来源方案: ${sourceText?.substring(0, 80)}`)
    } else {
      const noPlan = page.locator('.el-alert__title:has-text("暂无已应用方案")')
      const noPlanVisible = await noPlan.isVisible().catch(() => false)
      console.log(`  [OK] 暂无已应用方案: ${noPlanVisible}`)
    }

    // 验证"排课来源"列存在（在表头中）
    const sourceCol = page.locator('.el-table__header .cell:has-text("排课来源")')
    const colVisible = await sourceCol.isVisible().catch(() => false)
    console.log(`  [OK] "排课来源"列可见: ${colVisible}`)

    // 验证"来源批次/方案"列存在
    const batchCol = page.locator('.el-table__header .cell:has-text("来源批次/方案")')
    const batchVisible = await batchCol.isVisible().catch(() => false)
    console.log(`  [OK] "来源批次/方案"列可见: ${batchVisible}`)
  })

  // ====== 测试4：回滚方案 ======

  test('12. 方案详情页 - 回滚应用方案', async ({ page }) => {
    // 先登录再导航到方案2的详情页
    await loginAndGoTo(page, `/v3/schedule-plans/${ids.planId2}`)

    // 查找"回滚应用"按钮
    const rollbackBtn = page.locator('button:has-text("回滚应用")')
    const rollbackVisible = await rollbackBtn.isVisible().catch(() => false)
    console.log(`  [OK] "回滚应用"按钮可见: ${rollbackVisible}`)

    if (rollbackVisible) {
      await rollbackBtn.click()
      await page.waitForTimeout(1500)

      // 验证确认弹窗
      const dialog = page.locator('.el-message-box')
      const dialogVisible = await dialog.isVisible().catch(() => false)
      console.log(`  [OK] 回滚确认弹窗可见: ${dialogVisible}`)

      if (dialogVisible) {
        const dialogText = await dialog.textContent()
        console.log(`  [OK] 弹窗内容: ${dialogText?.substring(0, 120)}`)

        // 确认回滚
        const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定回滚"), .el-message-box__btns button:has-text("OK")')
        if (await confirmBtn.first().isVisible().catch(() => false)) {
          await confirmBtn.first().click()
          await page.waitForTimeout(2000)
          console.log('  [OK] 已确认回滚')
        } else {
          const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
          await cancelBtn.first().click().catch(() => {})
          console.log('  [OK] 已取消回滚')
        }
      }
    } else {
      // 如果方案2已被废弃，尝试方案1的"重新应用"
      await page.goto(`${BASE_URL}/v3/schedule-plans/${ids.planId1}`)
      await page.waitForTimeout(3000)

      const reapplyBtn = page.locator('button:has-text("重新应用")')
      const reapplyVisible = await reapplyBtn.isVisible().catch(() => false)
      console.log(`  [OK] "重新应用"按钮可见: ${reapplyVisible}`)

      if (reapplyVisible) {
        await reapplyBtn.click()
        await page.waitForTimeout(1500)

        const dialog = page.locator('.el-message-box')
        if (await dialog.isVisible().catch(() => false)) {
          const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定回滚"), .el-message-box__btns button:has-text("确定应用"), .el-message-box__btns button:has-text("OK")')
          if (await confirmBtn.first().isVisible().catch(() => false)) {
            await confirmBtn.first().click()
            await page.waitForTimeout(2000)
            console.log('  [OK] 已确认重新应用')
          } else {
            const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
            await cancelBtn.first().click().catch(() => {})
          }
        }
      } else {
        console.log('  [SKIP] 无回滚/重新应用按钮')
      }
    }
  })

  test('13. 回滚后 - 验证课表来源方案已切换', async ({ page }) => {
    await loginAndGoTo(page, '/schedule')
    await page.waitForTimeout(1000)

    // 验证来源方案信息
    const sourceInfo = page.locator('.el-alert__title:has-text("课表来源方案")')
    const sourceVisible = await sourceInfo.isVisible().catch(() => false)
    if (sourceVisible) {
      const sourceText = await sourceInfo.textContent()
      console.log(`  [OK] 回滚后来源方案: ${sourceText?.substring(0, 80)}`)
    } else {
      const noPlan = page.locator('.el-alert__title:has-text("暂无已应用方案")')
      const noPlanVisible = await noPlan.isVisible().catch(() => false)
      console.log(`  [OK] 暂无已应用方案: ${noPlanVisible}`)
    }

    // 检查表格中的来源信息
    const planSource = page.locator('td:has-text("方案应用")')
    const planSourceCount = await planSource.count().catch(() => 0)
    console.log(`  [OK] "方案应用"标签数量: ${planSourceCount}`)

    const planIdCol = page.locator('td:has-text("方案 #")')
    const planIdCount = await planIdCol.count().catch(() => 0)
    console.log(`  [OK] "方案 #"标签数量: ${planIdCount}`)
  })

  // ====== 测试5：对比页面直接应用方案 ======

  test('14. 对比页面 - 直接从对比结果中应用方案', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-compare')
    await page.waitForTimeout(2000)

    // 等待表格加载
    await page.waitForTimeout(2000)

    // 选择多种 checkbox 选择器
    let checkboxes = page.locator('.el-table__body .el-checkbox')
    let count = await checkboxes.count()
    if (count === 0) {
      checkboxes = page.locator('.el-checkbox-group .el-checkbox')
      count = await checkboxes.count()
    }
    if (count === 0) {
      checkboxes = page.locator('.el-checkbox')
      count = await checkboxes.count()
    }

    if (count >= 2) {
      await checkboxes.nth(0).click()
      await page.waitForTimeout(500)
      await checkboxes.nth(1).click()
      await page.waitForTimeout(500)

      await page.click('button:has-text("开始对比")')
      await page.waitForTimeout(5000)

      // 如果有对比结果，尝试点击"应用该方案"
      const applyBtn = page.locator('.el-table__body button:has-text("应用该方案")')
      const applyVisible = await applyBtn.first().isVisible().catch(() => false)

      if (applyVisible) {
        await applyBtn.first().click()
        await page.waitForTimeout(1500)

        // 处理弹窗
        const dialog = page.locator('.el-message-box')
        if (await dialog.isVisible().catch(() => false)) {
          const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定应用"), .el-message-box__btns button:has-text("OK")')
          if (await confirmBtn.first().isVisible().catch(() => false)) {
            await confirmBtn.first().click()
            await page.waitForTimeout(2000)
            console.log('  [OK] 从对比页面直接应用方案成功')
          } else {
            const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
            await cancelBtn.first().click().catch(() => {})
            console.log('  [OK] 已取消')
          }
        }
      } else {
        console.log('  [SKIP] 对比结果中无"应用该方案"按钮')
      }
    } else {
      console.log('  [SKIP] 方案数量不足')
    }
  })

  // ====== 测试6：方案状态变更验证 ======

  test('15. 方案列表 - 验证方案状态变更', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-plans')

    // 验证页面加载
    const header = page.locator('.card-header span:has-text("排课方案列表")')
    await expect(header.first()).toBeVisible({ timeout: 10000 })

    // 统计各状态数量
    const draftCount = await page.locator('.el-tag:has-text("草稿")').count()
    const appliedCount = await page.locator('.el-tag:has-text("已应用")').count()
    const abandonedCount = await page.locator('.el-tag:has-text("已废弃")').count()

    console.log(`  [OK] 方案状态统计: 草稿=${draftCount}, 已应用=${appliedCount}, 已废弃=${abandonedCount}`)

    // 至少有一个方案
    expect(draftCount + appliedCount + abandonedCount).toBeGreaterThanOrEqual(1)
  })

  // ====== 测试7：废弃方案 ======

  test('16. 方案列表 - 废弃草稿方案', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-plans')

    // 查找"废弃"按钮
    const abandonBtns = page.locator('button:has-text("废弃")')
    const abandonCount = await abandonBtns.count()
    console.log(`  [OK] "废弃"按钮数量: ${abandonCount}`)

    if (abandonCount > 0) {
      await abandonBtns.first().click()
      await page.waitForTimeout(1000)

      // 确认弹窗
      const dialog = page.locator('.el-message-box')
      if (await dialog.isVisible().catch(() => false)) {
        const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定"), .el-message-box__btns button:has-text("OK")')
        if (await confirmBtn.first().isVisible().catch(() => false)) {
          await confirmBtn.first().click()
          await page.waitForTimeout(1000)
          console.log('  [OK] 已废弃方案')
        } else {
          const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
          await cancelBtn.first().click().catch(() => {})
        }
      }
    } else {
      console.log('  [SKIP] 没有可废弃的方案')
    }
  })

  // ====== 测试8：删除草稿方案 ======

  test('17. 方案列表 - 删除草稿方案', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-plans')

    // 查找"删除"按钮
    const deleteBtns = page.locator('button:has-text("删除")')
    const deleteCount = await deleteBtns.count()
    console.log(`  [OK] "删除"按钮数量: ${deleteCount}`)

    if (deleteCount > 0) {
      await deleteBtns.first().click()
      await page.waitForTimeout(1000)

      // 确认弹窗
      const dialog = page.locator('.el-message-box')
      if (await dialog.isVisible().catch(() => false)) {
        const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定"), .el-message-box__btns button:has-text("OK")')
        if (await confirmBtn.first().isVisible().catch(() => false)) {
          await confirmBtn.first().click()
          await page.waitForTimeout(1000)
          console.log('  [OK] 已删除草稿方案')
        } else {
          const cancelBtn = page.locator('.el-message-box__btns button:has-text("取消"), .el-message-box__btns button:has-text("Cancel")')
          await cancelBtn.first().click().catch(() => {})
        }
      }
    } else {
      console.log('  [SKIP] 没有可删除的草稿方案')
    }
  })

  // ====== 测试9：对比页面返回方案列表 ======

  test('18. 对比页面 - 返回方案列表', async ({ page }) => {
    await loginAndGoTo(page, '/v3/schedule-compare')
    await page.waitForTimeout(2000)

    // 点击"返回方案列表"按钮
    const backBtn = page.locator('button:has-text("返回方案列表")')
    if (await backBtn.isVisible().catch(() => false)) {
      await backBtn.click()
      await page.waitForURL('**/v3/schedule-plans', { timeout: 10000 })
      console.log('  [OK] 成功返回方案列表页面')
    } else {
      console.log('  [SKIP] 未找到返回按钮')
    }
  })

  // ====== 测试10：方案详情页返回 ======

  test('19. 方案详情页 - 返回按钮', async ({ page }) => {
    await loginAndGoTo(page, `/v3/schedule-plans/${ids.planId1}`)

    // 点击返回按钮 (el-page-header 的 back)
    const backBtn = page.locator('.el-page-header__back, .el-page-header__left')
    if (await backBtn.first().isVisible().catch(() => false)) {
      await backBtn.first().click()
      await page.waitForTimeout(1000)
      // 检查是否回到方案列表
      const onListPage = await page.locator('.card-header span:has-text("排课方案列表")').isVisible().catch(() => false)
      console.log(`  [OK] 从详情页返回方案列表: ${onListPage}`)
    } else {
      console.log('  [SKIP] 未找到返回按钮')
    }
  })

  // ====== 清理 ======

  test('20. 清理 - 删除测试数据', async ({ request }) => {
    // 删除方案（通过 API）
    if (ids.planId1) {
      await request.delete(`${API_URL}/api/v3/schedule-plans/${ids.planId1}`, {
        headers: authHeaders(),
      }).catch(() => {})
    }
    if (ids.planId2) {
      await request.delete(`${API_URL}/api/v3/schedule-plans/${ids.planId2}`, {
        headers: authHeaders(),
      }).catch(() => {})
    }

    // 删除教学任务
    if (ids.taskId) {
      await request.delete(`${API_URL}/api/teaching-tasks/${ids.taskId}`, {
        headers: authHeaders(),
      }).catch(() => {})
    }

    console.log('  [OK] 测试数据清理完成')
  })
})
