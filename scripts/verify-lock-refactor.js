#!/usr/bin/env node
'use strict';

const http = require('node:http');
const https = require('node:https');
const { URL } = require('node:url');

function parseArgs(argv) {
  const args = { planId: null, planItemId: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--plan-id') {
      args.planId = argv[++i] ?? null;
    } else if (arg === '--plan-item-id') {
      args.planItemId = argv[++i] ?? null;
    }
  }
  return args;
}

function usage() {
  console.error('Usage: node scripts/verify-lock-refactor.js --plan-id <ID> --plan-item-id <ID>');
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.planId || !args.planItemId) {
    usage();
    process.exitCode = 1;
    return;
  }

  const baseUrl = normalizeBaseUrl(process.env.PAIKE_BASE_URL);
  const username = process.env.PAIKE_USERNAME || 'admin';
  const password = process.env.PAIKE_PASSWORD || '123456';
  const requester = createRequester(baseUrl);
  let failed = false;
  let cleanupFailed = false;

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
      const code = payload.code;
      const message = summarizeMessage(payload);
      const ok = expect(response, payload, message);
      if (!ok) {
        failed = true;
        console.log(`[${name}] ${label} => FAIL (http ${response.statusCode}, code=${String(code)}, msg="${message}")`);
        return response;
      }
      console.log(`[${name}] ${label} => OK (http ${response.statusCode}, code=${String(code)}, msg="${message || 'n/a'}")`);
      return response;
    } catch (error) {
      failed = true;
      console.log(`[${name}] ${label} => FAIL (error="${error.message}")`);
      return null;
    }
  }

  async function cleanupUnlock() {
    try {
      const response = await requester.request('POST', api('/v4/schedule-locks/unlock'), {
        targetType: 'PLAN',
        planId: Number(args.planId),
        planItemId: Number(args.planItemId),
      });
      const message = summarizeMessage(response.payload);
      console.log(`[cleanup] unlock => ${response.statusCode === 200 ? 'OK' : 'DONE'} (http ${response.statusCode}, code=${String(response.payload?.code)}, msg="${message}")`);
    } catch (error) {
      cleanupFailed = true;
      console.log(`[cleanup] unlock => FAIL (error="${error.message}")`);
    }
  }

  try {
    await login();

    const planId = Number(args.planId);
    const planItemId = Number(args.planItemId);
    const lockBody1 = { targetType: 'PLAN', planId, planItemId, lockReason: 'verify-lock-refactor' };
    const lockBody2 = { targetType: 'PLAN', planId, planItemId, lockReason: 'verify-lock-refactor-2' };
    const unlockBody = { targetType: 'PLAN', planId, planItemId };

    await runStep('step1', 'lock target', 'POST', api('/v4/schedule-locks/lock'), lockBody1, (response, payload) =>
      response.statusCode === 200 && payload.code === 200 && payload.data?.locked === true
    );
    await runStep('step2', 'duplicate lock', 'POST', api('/v4/schedule-locks/lock'), lockBody1, (response, payload, message) =>
      response.statusCode === 400 && message.includes('已处于锁定状态')
    );
    await runStep('step3', 'unlock target', 'POST', api('/v4/schedule-locks/unlock'), unlockBody, (response, payload) =>
      response.statusCode === 200 && payload.code === 200 && payload.data?.unlocked === true
    );
    await runStep('step4', 'duplicate unlock', 'POST', api('/v4/schedule-locks/unlock'), unlockBody, (response, payload, message) =>
      response.statusCode === 400 && message.includes('当前未锁定')
    );
    await runStep('step5', 'lock again', 'POST', api('/v4/schedule-locks/lock'), lockBody2, (response, payload) =>
      response.statusCode === 200 && payload.code === 200 && payload.data?.locked === true
    );
  } finally {
    await cleanupUnlock();
  }

  process.exit(failed || cleanupFailed ? 1 : 0);
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
