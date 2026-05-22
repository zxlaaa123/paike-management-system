#!/usr/bin/env node
'use strict';

const http = require('node:http');
const https = require('node:https');
const { URL } = require('node:url');

function parseArgs(argv) {
  const args = { semesterId: null, planId: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--semester-id') {
      args.semesterId = argv[++i] ?? null;
    } else if (arg === '--plan-id') {
      args.planId = argv[++i] ?? null;
    }
  }
  return args;
}

function usage() {
  console.error('Usage: node scripts/verify-soft-delete-plan.js --semester-id <ID> --plan-id <ID>');
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
    return Array.from(cookieJar.entries()).map(([name, value]) => `${name}=${value}`).join('; ');
  }

  function request(method, pathname, data) {
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
        const csrfToken = cookieJar.get('XSRF-TOKEN');
        if (csrfToken) headers['X-CSRF-Token'] = csrfToken;
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
        },
        (res) => {
          const chunks = [];
          res.on('data', (chunk) => chunks.push(chunk));
          res.on('end', () => {
            storeCookies(res.headers['set-cookie']);
            const text = Buffer.concat(chunks).toString('utf8');
            let payload = null;
            try {
              payload = text ? JSON.parse(text) : null;
            } catch {
              payload = text;
            }
            resolve({
              statusCode: res.statusCode || 0,
              payload,
            });
          });
        }
      );

      req.on('error', reject);
      if (body) req.write(body);
      req.end();
    });
  }

  return {
    request,
    cookies: cookieJar,
  };
}

function api(pathname) {
  return `/api${pathname}`;
}

function summarizeMessage(payload) {
  return payload?.message ?? payload?.msg ?? '';
}

function extractRecords(payload) {
  return Array.isArray(payload?.data?.records) ? payload.data.records : [];
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.semesterId || !args.planId) {
    usage();
    process.exitCode = 1;
    return;
  }

  const baseUrl = normalizeBaseUrl(process.env.PAIKE_BASE_URL);
  const username = process.env.PAIKE_USERNAME || 'admin';
  const password = process.env.PAIKE_PASSWORD || '123456';
  const requester = createRequester(baseUrl);
  const semesterId = Number(args.semesterId);
  const planId = Number(args.planId);
  let failed = false;
  let scheduledCount = null;

  async function login() {
    const response = await requester.request('POST', api('/auth/login'), { username, password });
    const code = response.payload?.code;
    if (code !== 200 || !requester.cookies.get('paike_token')) {
      throw new Error(`login failed: http ${response.statusCode}, code=${code}, msg="${summarizeMessage(response.payload)}"`);
    }
  }

  async function runStep(name, label, method, pathname, body, expect) {
    try {
      const response = await requester.request(method, pathname, body);
      const payload = response.payload || {};
      const message = summarizeMessage(payload);
      const outcome = expect(response, payload, message);
      if (!outcome.ok) {
        failed = true;
        console.log(`[${name}] ${label} => FAIL (${outcome.detail})`);
        return response;
      }
      console.log(`[${name}] ${label} => OK (${outcome.detail})`);
      return response;
    } catch (error) {
      failed = true;
      console.log(`[${name}] ${label} => FAIL (error="${error.message}")`);
      return null;
    }
  }

  await login();

  await runStep('step1', 'get plan detail', 'GET', api(`/v3/schedule-plans/${planId}`), null, (response, payload, message) => {
    const plan = payload.data;
    scheduledCount = plan?.scheduledCount ?? null;
    const detail = `http ${response.statusCode}, code=${String(payload.code)}, status="${plan?.status ?? 'n/a'}", scheduledCount=${String(scheduledCount)}`;
    if (response.statusCode !== 200 || payload.code !== 200 || !plan) {
      return { ok: false, detail: `${detail}, expected existing draft plan, msg="${message}"` };
    }
    if (plan.status !== 'DRAFT') {
      return { ok: false, detail: `${detail}, expected status="DRAFT"` };
    }
    return { ok: true, detail };
  });

  await runStep('step2', 'list plans before delete', 'GET', api(`/v3/schedule-plans?semesterId=${semesterId}&page=1&size=100`), null, (response, payload, message) => {
    const records = extractRecords(payload);
    const contains = records.some((item) => Number(item.id) === planId);
    const detail = `http ${response.statusCode}, code=${String(payload.code)}, containsPlan=${String(contains)}`;
    if (response.statusCode !== 200 || payload.code !== 200) {
      return { ok: false, detail: `${detail}, expected plan in list, msg="${message}"` };
    }
    return contains
      ? { ok: true, detail }
      : { ok: false, detail: `${detail}, expected containsPlan=true` };
  });

  await runStep('step3', 'delete draft plan', 'DELETE', api(`/v3/schedule-plans/${planId}`), null, (response, payload, message) => {
    const detail = `http ${response.statusCode}, code=${String(payload.code)}, msg="${message}"`;
    return response.statusCode === 200 && payload.code === 200
      ? { ok: true, detail }
      : { ok: false, detail: `${detail}, expected http 200 and code=200` };
  });

  await runStep('step4', 'get deleted plan detail', 'GET', api(`/v3/schedule-plans/${planId}`), null, (response, payload, message) => {
    const missing = response.statusCode >= 400 || payload.code !== 200;
    const notFound = message.includes('不存在');
    const detail = `http ${response.statusCode}, code=${String(payload.code)}, msg="${message}"`;
    return missing && notFound
      ? { ok: true, detail }
      : { ok: false, detail: `${detail}, expected 4xx/code!=200 and msg contains "不存在"` };
  });

  await runStep('step5', 'list plans after delete', 'GET', api(`/v3/schedule-plans?semesterId=${semesterId}&page=1&size=100`), null, (response, payload, message) => {
    const records = extractRecords(payload);
    const contains = records.some((item) => Number(item.id) === planId);
    const detail = `http ${response.statusCode}, code=${String(payload.code)}, containsPlan=${String(contains)}`;
    if (response.statusCode !== 200 || payload.code !== 200) {
      return { ok: false, detail: `${detail}, expected plan removed from list, msg="${message}"` };
    }
    return !contains
      ? { ok: true, detail }
      : { ok: false, detail: `${detail}, expected containsPlan=false` };
  });

  console.log(`SQL hint: SELECT id, deleted FROM schedule_plan WHERE id = ${planId}; expected deleted=1`);
  process.exit(failed ? 1 : 0);
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
