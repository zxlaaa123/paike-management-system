#!/usr/bin/env node
'use strict';

// P1-13 性能测量：登录 -> 调一次 V5 localReplan -> 打印耗时 / scope / 生成方案 id
//
// 默认目标：task 5 (SUGGESTED, source_plan_id=63)，scope = 班级 [68,77,80] (~15 items)。
// 用 --task-id / --class-ids / --candidate-limit / --keep 覆盖。
//
// 用法示例：
//   node scripts/perf-local-replan.js
//   node scripts/perf-local-replan.js --task-id 5 --class-ids 68,77,80,73,66 --candidate-limit 600
//   node scripts/perf-local-replan.js --keep   # 不 discard 试算方案

const http = require('node:http');
const https = require('node:https');
const { URL } = require('node:url');

function parseArgs(argv) {
  const args = {
    taskId: 5,
    classIds: [68, 77, 80],
    teacherIds: [],
    classroomIds: [],
    weekdays: [],
    periodNos: [],
    candidateLimit: null,
    keep: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--task-id') args.taskId = Number(argv[++i]);
    else if (arg === '--class-ids') args.classIds = parseIntList(argv[++i]);
    else if (arg === '--teacher-ids') args.teacherIds = parseIntList(argv[++i]);
    else if (arg === '--classroom-ids') args.classroomIds = parseIntList(argv[++i]);
    else if (arg === '--weekdays') args.weekdays = parseIntList(argv[++i]);
    else if (arg === '--period-nos') args.periodNos = parseIntList(argv[++i]);
    else if (arg === '--candidate-limit') args.candidateLimit = Number(argv[++i]);
    else if (arg === '--keep') args.keep = true;
  }
  return args;
}

function parseIntList(value) {
  if (!value) return [];
  return String(value).split(',').map((v) => Number(v.trim())).filter((v) => Number.isFinite(v));
}

function normalizeBaseUrl(value) {
  const base = value || 'http://127.0.0.1:8090';
  return base.endsWith('/') ? base.slice(0, -1) : base;
}

function createRequester(baseUrl) {
  const cookieJar = new Map();
  const base = new URL(baseUrl);
  const transport = base.protocol === 'https:' ? https : http;

  function storeCookies(setCookieHeaders) {
    if (!setCookieHeaders) return;
    const values = Array.isArray(setCookieHeaders) ? setCookieHeaders : [setCookieHeaders];
    for (const cookie of values) {
      const pair = String(cookie).split(';')[0];
      const idx = pair.indexOf('=');
      if (idx < 0) continue;
      const name = pair.slice(0, idx).trim();
      const value = pair.slice(idx + 1).trim();
      if (name) cookieJar.set(name, value);
    }
  }

  function buildCookieHeader() {
    if (cookieJar.size === 0) return '';
    return Array.from(cookieJar.entries()).map(([n, v]) => `${n}=${v}`).join('; ');
  }

  function request(method, pathname, data, opts = {}) {
    return new Promise((resolve, reject) => {
      const body = data == null ? '' : JSON.stringify(data);
      const headers = {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      };
      const cookieHeader = buildCookieHeader();
      if (cookieHeader) headers.Cookie = cookieHeader;
      const upperMethod = method.toUpperCase();
      if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(upperMethod)) {
        const csrf = cookieJar.get('XSRF-TOKEN');
        if (csrf) headers['X-CSRF-Token'] = csrf;
      }
      if (body) headers['Content-Length'] = Buffer.byteLength(body);

      const req = transport.request(
        {
          protocol: base.protocol,
          hostname: base.hostname,
          port: base.port || (base.protocol === 'https:' ? 443 : 80),
          method: upperMethod,
          path: pathname,
          headers,
          timeout: opts.timeoutMs || 600000,
        },
        (res) => {
          const chunks = [];
          res.on('data', (c) => chunks.push(c));
          res.on('end', () => {
            storeCookies(res.headers['set-cookie']);
            const text = Buffer.concat(chunks).toString('utf8');
            let payload = null;
            try { payload = text ? JSON.parse(text) : null; } catch { payload = text; }
            resolve({ statusCode: res.statusCode || 0, payload });
          });
        }
      );
      req.on('error', reject);
      req.on('timeout', () => req.destroy(new Error('request timeout')));
      if (body) req.write(body);
      req.end();
    });
  }

  return { request, cookies: cookieJar };
}

function api(p) { return `/api${p}`; }
function msg(p) { return p?.message ?? p?.msg ?? ''; }
function fmtMs(ms) { return ms.toFixed(1).padStart(8) + ' ms'; }

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const baseUrl = normalizeBaseUrl(process.env.PAIKE_BASE_URL);
  const username = process.env.PAIKE_USERNAME || 'admin';
  const password = process.env.PAIKE_PASSWORD || '123456';
  const r = createRequester(baseUrl);

  console.log(`[config] baseUrl=${baseUrl} taskId=${args.taskId} classIds=[${args.classIds.join(',')}] candidateLimit=${args.candidateLimit ?? '(default 600)'}`);

  // ---- login ----
  let t0 = process.hrtime.bigint();
  const login = await r.request('POST', api('/auth/login'), { username, password });
  let loginMs = Number(process.hrtime.bigint() - t0) / 1e6;
  if (login.payload?.code !== 200 || !r.cookies.get('paike_token')) {
    console.error(`[login] FAIL http=${login.statusCode} code=${login.payload?.code} msg="${msg(login.payload)}"`);
    process.exit(1);
  }
  console.log(`[login] OK ${fmtMs(loginMs)}`);

  // ---- localReplan ----
  const body = {
    classIds: args.classIds.length ? args.classIds : undefined,
    teacherIds: args.teacherIds.length ? args.teacherIds : undefined,
    classroomIds: args.classroomIds.length ? args.classroomIds : undefined,
    weekdays: args.weekdays.length ? args.weekdays : undefined,
    periodNos: args.periodNos.length ? args.periodNos : undefined,
    candidateLimit: args.candidateLimit ?? undefined,
    newPlanName: `perf-local-replan-${Date.now()}`,
  };
  console.log(`[localReplan] request body=${JSON.stringify(body)}`);

  t0 = process.hrtime.bigint();
  let resp;
  try {
    resp = await r.request('POST', api(`/v5/repair-tasks/${args.taskId}/simulations/local-replan`), body);
  } catch (e) {
    console.error(`[localReplan] FAIL error="${e.message}"`);
    process.exit(1);
  }
  const replanMs = Number(process.hrtime.bigint() - t0) / 1e6;

  console.log(`[localReplan] http=${resp.statusCode} code=${resp.payload?.code} msg="${msg(resp.payload)}"`);
  console.log(`[localReplan] ELAPSED ${fmtMs(replanMs)}`);

  if (resp.statusCode !== 200 || resp.payload?.code !== 200) {
    console.error('[localReplan] non-200, dumping payload below and exiting');
    console.error(JSON.stringify(resp.payload, null, 2));
    process.exit(2);
  }

  const data = resp.payload.data || {};
  const plan = data.plan || data.simulation || {};
  const itemCount = (data.items && data.items.length) || plan.scheduledCount || null;
  const planId = plan.id ?? data.planId ?? null;
  const adjustLogs = data.adjustLogs || data.logs || [];
  console.log(`[localReplan] simulation planId=${planId}  items=${itemCount}  conflictCount=${plan.conflictCount ?? 'n/a'}  totalScore=${plan.totalScore ?? 'n/a'}`);
  console.log(`[localReplan] adjustLogs.count=${adjustLogs.length}`);
  if (adjustLogs.length) {
    console.log(`[localReplan] first 3 logs:`);
    for (const line of adjustLogs.slice(0, 3)) {
      console.log(`  - ${typeof line === 'string' ? line : JSON.stringify(line)}`);
    }
  }

  // ---- cleanup: discard simulation ----
  if (!args.keep && planId) {
    t0 = process.hrtime.bigint();
    const dr = await r.request('POST', api(`/v5/repair-tasks/${args.taskId}/simulations/${planId}/discard`), null);
    const discardMs = Number(process.hrtime.bigint() - t0) / 1e6;
    console.log(`[cleanup] discard plan ${planId} http=${dr.statusCode} code=${dr.payload?.code} msg="${msg(dr.payload)}" ${fmtMs(discardMs)}`);
  } else if (args.keep) {
    console.log(`[cleanup] --keep specified, simulation plan ${planId} retained`);
  }

  // ---- summary ----
  console.log('---- SUMMARY ----');
  console.log(`localReplan elapsed: ${replanMs.toFixed(1)} ms (${(replanMs / 1000).toFixed(2)} s)`);
  console.log(`Verdict: ${replanMs < 5000 ? 'FAST (<5s) — 不必优化' : replanMs < 30000 ? 'SLOW (5-30s) — 边际优化' : 'CRITICAL (>30s) — 必须优化'}`);
}

main().catch((e) => {
  console.error(e.stack || e.message || String(e));
  process.exit(1);
});
