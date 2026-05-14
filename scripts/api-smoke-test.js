#!/usr/bin/env node

const http = require('http')
const https = require('https')
const fs = require('fs')
const path = require('path')

function parseArgs(argv) {
  const parsed = {
    json: false,
    keepData: false,
    output: '',
    baseUrl: process.env.PAIKE_BASE_URL || 'http://127.0.0.1:8090',
    username: process.env.PAIKE_USERNAME || 'admin',
    password: process.env.PAIKE_PASSWORD || '123456',
    prefix: process.env.PAIKE_SMOKE_PREFIX || `SMOKE-${Date.now().toString().slice(-6)}`,
  }

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--json') parsed.json = true
    else if (arg === '--keep-data') parsed.keepData = true
    else if (arg === '--output') parsed.output = argv[i + 1] || ''
    else if (arg === '--base-url') parsed.baseUrl = argv[i + 1] || parsed.baseUrl
    else if (arg === '--username') parsed.username = argv[i + 1] || parsed.username
    else if (arg === '--password') parsed.password = argv[i + 1] || parsed.password
    else if (arg === '--prefix') parsed.prefix = argv[i + 1] || parsed.prefix
  }

  return parsed
}

function createRequester(baseUrl) {
  const base = new URL(baseUrl)
  const transport = base.protocol === 'https:' ? https : http

  return function request(method, pathname, data, token) {
    return new Promise((resolve, reject) => {
      const body = data == null ? null : JSON.stringify(data)
      const headers = {}
      if (body) {
        headers['Content-Type'] = 'application/json'
        headers['Content-Length'] = Buffer.byteLength(body)
      }
      if (token) headers.Authorization = `Bearer ${token}`

      const req = transport.request(
        {
          protocol: base.protocol,
          hostname: base.hostname,
          port: base.port,
          path: pathname,
          method,
          headers,
        },
        (res) => {
          let raw = ''
          res.on('data', (chunk) => {
            raw += chunk
          })
          res.on('end', () => {
            let payload = raw
            try {
              payload = raw ? JSON.parse(raw) : null
            } catch (_error) {
              // keep raw string
            }
            resolve({
              httpStatus: res.statusCode || 0,
              payload,
            })
          })
        },
      )

      req.on('error', reject)
      if (body) req.write(body)
      req.end()
    })
  }
}

function summarizePayload(payload) {
  if (payload == null) return ''
  if (typeof payload === 'string') return payload.slice(0, 180)
  if (typeof payload === 'object') {
    if (typeof payload.message === 'string') return payload.message
    return JSON.stringify(payload).slice(0, 180)
  }
  return String(payload)
}

async function run() {
  const options = parseArgs(process.argv.slice(2))
  const request = createRequester(options.baseUrl)
  const api = (pathname) => `/api${pathname}`
  const created = {
    teacherId: null,
    classId: null,
    classroomId: null,
    courseId: null,
    taskId: null,
    conflictTaskId: null,
    scheduleId: null,
  }
  const results = []

  function record(name, passed, detail, extra = {}) {
    const item = {
      name,
      passed,
      detail,
      ...extra,
    }
    results.push(item)
    const prefix = passed ? 'PASS' : 'FAIL'
    console.log(`${prefix} ${name}${detail ? ` | ${detail}` : ''}`)
    return item
  }

  async function apiCall(name, method, pathname, data, token, predicate) {
    try {
      const response = await request(method, api(pathname), data, token)
      const passed = predicate(response)
      record(name, passed, summarizePayload(response.payload), { response })
      return response
    } catch (error) {
      record(name, false, error.message, { error: error.message })
      return null
    }
  }

  async function cleanup(token) {
    const deletions = [
      ['schedule', created.scheduleId, (id) => request('DELETE', api(`/schedules/${id}`), null, token)],
      ['conflictTask', created.conflictTaskId, (id) => request('DELETE', api(`/teaching-tasks/${id}`), null, token)],
      ['task', created.taskId, (id) => request('DELETE', api(`/teaching-tasks/${id}`), null, token)],
      ['course', created.courseId, (id) => request('DELETE', api(`/courses/${id}`), null, token)],
      ['classroom', created.classroomId, (id) => request('DELETE', api(`/classrooms/${id}`), null, token)],
      ['class', created.classId, (id) => request('DELETE', api(`/classes/${id}`), null, token)],
      ['teacher', created.teacherId, (id) => request('DELETE', api(`/teachers/${id}`), null, token)],
    ]

    for (const [label, id, deleter] of deletions) {
      if (!id) continue
      try {
        await deleter(id)
        console.log(`CLEANUP ${label} ${id}`)
      } catch (error) {
        console.log(`CLEANUP-FAIL ${label} ${id} | ${error.message}`)
      }
    }
  }

  let token = ''
  try {
    const health = await request('GET', api('/health'), null, null)
    record('health', health.httpStatus === 200, `http=${health.httpStatus}`)

    const login = await request('POST', api('/auth/login'), {
      username: options.username,
      password: options.password,
    })
    token = login.payload?.data?.token || ''
    record('login', login.payload?.code === 200 && Boolean(token), summarizePayload(login.payload))
    if (!token) throw new Error('登录失败，无法继续执行冒烟测试')

    const timeSlots = await apiCall('time-slots', 'GET', '/time-slots', null, token, (res) => {
      return res.payload?.code === 200 && Array.isArray(res.payload?.data) && res.payload.data.length > 0
    })
    const firstSlotId = timeSlots?.payload?.data?.[0]?.id

    await apiCall('schedules-page', 'GET', '/schedules?page=1&size=10', null, token, (res) => res.payload?.code === 200)
    await apiCall('teaching-tasks-page', 'GET', '/teaching-tasks?page=1&size=10', null, token, (res) => res.payload?.code === 200)
    await apiCall('unscheduled-tasks-page', 'GET', '/unscheduled-tasks?page=1&size=10', null, token, (res) => res.payload?.code === 200)

    const teacherNo = `T-${options.prefix}`
    const className = `冒烟班级-${options.prefix}`
    const roomName = `冒烟教室-${options.prefix}`
    const courseNo = `C-${options.prefix}`
    const courseName = `冒烟课程-${options.prefix}`

    const teacher = await request('POST', api('/teachers'), {
      teacherNo,
      name: `冒烟教师${options.prefix}`,
      department: '冒烟测试部',
      phone: '13800138000',
      status: 1,
      remark: 'API 冒烟测试自动创建',
    }, token)
    created.teacherId = teacher.payload?.data?.id || null
    record('create-teacher', teacher.payload?.code === 200 && Boolean(created.teacherId), summarizePayload(teacher.payload))
    if (!created.teacherId) throw new Error('教师创建失败，停止后续测试')

    const clazz = await request('POST', api('/classes'), {
      className,
      major: '冒烟专业',
      grade: '2026',
      studentCount: 40,
      headTeacher: '冒烟班主任',
      status: 1,
      remark: 'API 冒烟测试自动创建',
    }, token)
    created.classId = clazz.payload?.data?.id || null
    record('create-class', clazz.payload?.code === 200 && Boolean(created.classId), summarizePayload(clazz.payload))
    if (!created.classId) throw new Error('班级创建失败，停止后续测试')

    const classroom = await request('POST', api('/classrooms'), {
      roomName,
      building: '冒烟楼',
      capacity: 60,
      roomType: 'NORMAL',
      status: 1,
      remark: 'API 冒烟测试自动创建',
    }, token)
    created.classroomId = classroom.payload?.data?.id || null
    record('create-classroom', classroom.payload?.code === 200 && Boolean(created.classroomId), summarizePayload(classroom.payload))
    if (!created.classroomId) throw new Error('教室创建失败，停止后续测试')

    const course = await request('POST', api('/courses'), {
      courseNo,
      courseName,
      courseType: 'NORMAL',
      courseNature: '必修',
      totalHours: 32,
      weeklyHours: 4,
      remark: 'API 冒烟测试自动创建',
    }, token)
    created.courseId = course.payload?.data?.id || null
    record('create-course', course.payload?.code === 200 && Boolean(created.courseId), summarizePayload(course.payload))
    if (!created.courseId) throw new Error('课程创建失败，停止后续测试')

    const task = await request('POST', api('/teaching-tasks'), {
      courseId: created.courseId,
      teacherId: created.teacherId,
      classId: created.classId,
      weeklyHours: 4,
      needContinuous: 0,
      status: 1,
      remark: 'API 冒烟测试自动创建',
    }, token)
    created.taskId = task.payload?.data?.id || null
    record('create-teaching-task', task.payload?.code === 200 && Boolean(created.taskId), summarizePayload(task.payload))
    if (!created.taskId || !firstSlotId) throw new Error('教学任务或时间段缺失，停止后续测试')

    const schedule = await request('POST', api('/schedules'), {
      teachingTaskId: created.taskId,
      timeSlotId: firstSlotId,
      classroomId: created.classroomId,
    }, token)
    created.scheduleId = schedule.payload?.data?.id || null
    record('create-schedule', schedule.payload?.code === 200 && Boolean(created.scheduleId), summarizePayload(schedule.payload))

    const timetable = await request('GET', api(`/timetables/classes/${created.classId}`), null, token)
    const hasTimetableData = timetable.payload?.code === 200 && Array.isArray(timetable.payload?.data) && timetable.payload.data.length > 0
    record('class-timetable', hasTimetableData, summarizePayload(timetable.payload))

    const conflictTask = await request('POST', api('/teaching-tasks'), {
      courseId: created.courseId,
      teacherId: created.teacherId,
      classId: created.classId,
      weeklyHours: 2,
      needContinuous: 0,
      status: 1,
      remark: 'API 冒烟测试冲突验证',
    }, token)
    created.conflictTaskId = conflictTask.payload?.data?.id || null
    record(
      'create-conflict-task',
      conflictTask.payload?.code === 200 && Boolean(created.conflictTaskId),
      summarizePayload(conflictTask.payload),
    )

    if (created.conflictTaskId && firstSlotId) {
      const conflict = await request('POST', api('/schedules'), {
        teachingTaskId: created.conflictTaskId,
        timeSlotId: firstSlotId,
        classroomId: created.classroomId,
      }, token)
      const conflictCode = conflict.payload?.code
      record(
        'teacher-conflict',
        conflictCode === 400,
        `code=${conflictCode} ${summarizePayload(conflict.payload)}`,
        { response: conflict },
      )
    }
  } catch (error) {
    record('fatal', false, error.message, { error: error.message })
  } finally {
    if (token && !options.keepData) {
      await cleanup(token)
    }
  }

  const summary = {
    baseUrl: options.baseUrl,
    prefix: options.prefix,
    keepData: options.keepData,
    passed: results.filter((item) => item.passed).length,
    failed: results.filter((item) => !item.passed).length,
    results,
  }

  if (options.output) {
    const outputPath = path.resolve(process.cwd(), options.output)
    fs.mkdirSync(path.dirname(outputPath), { recursive: true })
    fs.writeFileSync(outputPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
    console.log(`WROTE ${outputPath}`)
  }

  if (options.json) {
    console.log(JSON.stringify(summary, null, 2))
  } else {
    console.log(`SUMMARY passed=${summary.passed} failed=${summary.failed}`)
  }

  if (summary.failed > 0) {
    process.exitCode = 1
  }
}

run().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
