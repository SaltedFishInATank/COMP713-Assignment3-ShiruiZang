/* Genuine browser verification. Run with Node and Playwright on NODE_PATH.
 * Optional: COMP713_BASE_URL, CHROME_PATH, CODEX_NODE_MODULES, EVIDENCE_WEB_DIR.
 * Normal browser workflow mutations use visible web forms and buttons.
 * --prepare-android-fixture creates a dedicated native-test fixture through Payara.
 */
'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');

function loadPlaywright() {
  try { return require('playwright'); } catch (original) {
    const modules = process.env.CODEX_NODE_MODULES || path.join(process.env.USERPROFILE || '',
      '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules');
    try { return require(path.join(modules, 'playwright')); } catch { throw original; }
  }
}

const { chromium } = loadPlaywright();
const base = (process.env.COMP713_BASE_URL || 'http://localhost:8080/comp713-a3/').replace(/\/?$/, '/');
const output = process.env.EVIDENCE_WEB_DIR || path.resolve(__dirname, '../evidence/web');
const checks = [];
const report = { startedAt: new Date().toISOString(), base, checks, browser: '', roles: ['coordinator', 'worker'] };
const telemetry = {};
let browser, coordinatorContext, workerContext, coordinator, worker, project, task;
const timeout = 20000;
const run = Date.now();
const projectName = `Browser fieldwork ${run}`;
const taskTitle = `Inspect campus access ${run}`;

async function prepareAndroidFixture() {
  await fs.mkdir(output, { recursive: true });
  async function request(suffix, method = 'GET', body, token) {
    const response = await fetch(base + 'api/' + suffix, { method,
      headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
    const json = await response.json();
    if (!response.ok) throw new Error('Android fixture ' + method + ' ' + suffix + ' returned ' + response.status + ': ' + JSON.stringify(json));
    return json.data;
  }
  const login = await request('auth/login', 'POST', { username: 'coordinator', password: 'Coordinator713!' });
  const users = await request('users', 'GET', undefined, login.token);
  const assignee = users.find(item => item.username === 'worker');
  assert.ok(assignee, 'Seeded worker account is missing');
  const project = await request('projects', 'POST', { name: 'Android verification ' + run,
    description: 'Real emulator worker to open coordinator web browser verification.' }, login.token);
  const task = await request('projects/' + project.id + '/tasks', 'POST', { title: 'Android field evidence ' + run,
    description: 'Native Android acceptance and submission must update the open web client without refresh.',
    assignedTo: assignee.id }, login.token);
  const fixture = { createdAt: new Date().toISOString(), projectId: project.id, taskId: task.id,
    title: task.title, status: task.status, assignedTo: assignee.id, project, task };
  await fs.writeFile(path.join(output, 'android-fixture.json'), JSON.stringify(fixture, null, 2));
  console.log(JSON.stringify(fixture, null, 2));
}

// Optional Android-to-web observer mode. It performs no workflow mutations.
// Set ANDROID_TASK_ID and run this same script while real Android instrumentation
// accepts/submits that task. Readiness and result files coordinate the two clients.
async function observeAndroid() {
  const taskId = Number(process.env.ANDROID_TASK_ID);
  assert.ok(Number.isSafeInteger(taskId) && taskId > 0, 'ANDROID_TASK_ID must be a positive task id');
  const observerReport = { startedAt: new Date().toISOString(), base, taskId, status: 'RUNNING', observedStates: [], websocketEvents: [] };
  const readyPath = path.join(output, 'android-observer-ready.json');
  const resultPath = path.join(output, 'android-observer-result.json');
  let observerBrowser, page, context;
  await fs.mkdir(output, { recursive: true });
  await fs.rm(readyPath, { force: true });
  try {
    observerBrowser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
      headless: true, args: ['--no-first-run', '--disable-default-apps'] });
    observerReport.browser = observerBrowser.version();
    context = await observerBrowser.newContext({ viewport: { width: 1440, height: 1000 } });
    page = await context.newPage();
    const record = watch(page, 'androidObserver');
    page.setDefaultTimeout(timeout);
    await page.goto(base, { waitUntil: 'domcontentloaded' });
    await signIn(page, 'coordinator', 'Coordinator713!');
    const details = await page.evaluate(async id => {
      const response = await fetch('api/tasks/' + id, { headers: { Authorization: 'Bearer ' + sessionStorage.getItem('fieldwork-token') } });
      if (!response.ok) throw new Error('Observer could not read task: HTTP ' + response.status);
      return (await response.json()).data;
    }, taskId);
    observerReport.title = details.title;
    observerReport.projectId = details.projectId;
    await page.locator('#project-filter').selectOption(String(details.projectId));
    await page.locator(`[data-task="${taskId}"]`).waitFor({ state: 'visible' });
    async function captureState() {
      const state = await page.locator(`[data-task="${taskId}"]`).evaluate(card => card.closest('section').querySelector('.column-head').textContent.trim());
      const label = state.replace(/\s+\d+$/, '').trim();
      if (!observerReport.observedStates.some(item => item.state === label)) {
        const screenshotName = 'android-live-' + label.toLowerCase().replace(/[^a-z]+/g, '-') + '.png';
        observerReport.observedStates.push({ at: new Date().toISOString(), state: label, screenshot: screenshotName });
        await screenshot(page, screenshotName);
        await fs.writeFile(resultPath, JSON.stringify(observerReport, null, 2));
      }
      return label;
    }
    const initial = await captureState();
    assert.equal(initial, 'TO DO', 'Android fixture must start as todo so acceptance and submission are observable');
    observerReport.initialMainNavigations = record.mainNavigations.length;
    await fs.writeFile(readyPath, JSON.stringify({ readyAt: new Date().toISOString(), taskId, projectId: details.projectId,
      title: details.title, initialState: initial, websocketAuthenticated: received('androidObserver', 'ready') > 0 }, null, 2));
    console.log('ANDROID_OBSERVER_READY taskId=' + taskId + ' title=' + details.title);
    const deadline = Date.now() + Number(process.env.ANDROID_OBSERVER_TIMEOUT_MS || 240000);
    let state = initial;
    while (Date.now() < deadline) {
      if (await page.locator(`[data-task="${taskId}"]`).count()) state = await captureState();
      if (state === 'AWAITING REVIEW') break;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.equal(state, 'AWAITING REVIEW', 'Android submission did not reach the open web task board');
    assert.ok(observerReport.observedStates.some(item => item.state === 'IN PROGRESS'), 'Android acceptance was not observed on the web board');
    observerReport.websocketEvents = record.websockets.flatMap(socket => socket.received);
    assert.ok(received('androidObserver', 'task.changed') >= 2, 'Expected real task.changed WebSocket frames for Android acceptance and submission');
    assert.equal(record.mainNavigations.length, observerReport.initialMainNavigations, 'Web page reloaded during Android changes');
    assert.deepEqual(record.pageErrors, []);
    observerReport.pageReloaded = false;
    observerReport.mainNavigations = record.mainNavigations.length;
    observerReport.taskChangedFrames = received('androidObserver', 'task.changed');
    await page.locator(`[data-task="${taskId}"]`).click();
    await page.locator('.review-note').waitFor({ state: 'visible' });
    observerReport.displayedEvidence = await page.locator('.review-note').textContent();
    await screenshot(page, 'android-live-submitted-evidence.png');
    observerReport.status = 'PASS';
    console.log('PASS Android-to-open-web task acceptance and submission without refresh');
  } catch (error) {
    observerReport.status = 'FAIL'; observerReport.error = error.stack; process.exitCode = 1;
    if (page) await screenshot(page, 'android-live-failure.png').catch(() => {});
  } finally {
    observerReport.completedAt = new Date().toISOString();
    observerReport.telemetry = telemetry.androidObserver;
    await fs.writeFile(resultPath, JSON.stringify(observerReport, null, 2));
    await observerBrowser?.close();
    console.log('Android observer evidence: ' + resultPath);
  }
}

async function saveReport() {
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(report, null, 2));
  await fs.writeFile(path.join(output, 'browser-events.json'), JSON.stringify(telemetry, null, 2));
}

async function check(name, action) {
  const started = Date.now();
  try {
    const evidence = await action();
    checks.push({ name, status: 'PASS', durationMs: Date.now() - started, ...evidence });
    console.log('PASS ' + name);
  } catch (error) {
    checks.push({ name, status: 'FAIL', durationMs: Date.now() - started, error: error.stack });
    console.error('FAIL ' + name + ': ' + error.message);
    throw error;
  } finally { await saveReport(); }
}

async function until(condition, description, milliseconds = timeout) {
  const deadline = Date.now() + milliseconds;
  while (Date.now() < deadline) {
    if (await condition()) return;
    await new Promise(resolve => setTimeout(resolve, 80));
  }
  throw new Error('Timed out: ' + description);
}

function watch(page, role) {
  const record = telemetry[role] = { mainNavigations: [], api: [], websockets: [], pageErrors: [], failedRequests: [] };
  page.on('framenavigated', frame => {
    if (frame === page.mainFrame()) record.mainNavigations.push({ at: new Date().toISOString(), url: frame.url() });
  });
  page.on('pageerror', error => record.pageErrors.push(error.message));
  page.on('requestfailed', request => record.failedRequests.push({ at: new Date().toISOString(), method: request.method(),
    url: request.url(), reason: request.failure()?.errorText }));
  page.on('response', response => {
    if (response.url().startsWith(base + 'api/')) record.api.push({ at: new Date().toISOString(),
      method: response.request().method(), path: response.url().slice(base.length), status: response.status() });
  });
  page.on('websocket', socket => {
    const events = { url: socket.url(), openedAt: new Date().toISOString(), received: [], sentTypes: [] };
    record.websockets.push(events);
    socket.on('framereceived', frame => {
      try { events.received.push({ at: new Date().toISOString(), ...JSON.parse(String(frame.payload)) }); }
      catch { events.received.push({ at: new Date().toISOString(), type: 'unparseable' }); }
    });
    socket.on('framesent', frame => {
      // Do not persist authentication tokens or credentials in the evidence.
      try { events.sentTypes.push(JSON.parse(String(frame.payload)).type); } catch { events.sentTypes.push('unparseable'); }
    });
    socket.on('close', () => { events.closedAt = new Date().toISOString(); });
  });
  return record;
}

function apiResponse(page, method, suffix) {
  return page.waitForResponse(response => response.request().method() === method && response.url() === base + 'api/' + suffix,
    { timeout });
}

function received(role, type) {
  return telemetry[role].websockets.flatMap(socket => socket.received).filter(event => event.type === type).length;
}

async function signIn(page, username, password) {
  await page.locator('#login-form input[name="username"]').fill(username);
  await page.locator('#login-form input[name="password"]').fill(password);
  await page.locator('#login-form button').click();
  await page.locator('#workspace').waitFor({ state: 'visible', timeout });
  await page.locator('#socket-status').filter({ hasText: 'Live updates' }).waitFor({ timeout });
}

async function screenshot(page, filename) {
  await page.screenshot({ path: path.join(output, filename), fullPage: true, animations: 'disabled' });
}

async function expectTaskState(page, id, text) {
  const card = page.locator(`.board > section`).filter({ has: page.locator('.column-head').filter({ hasText: text }) })
    .locator(`[data-task="${id}"]`);
  await card.waitFor({ state: 'visible', timeout });
}

async function openTask(page) {
  await page.locator(`[data-task="${task.id}"]`).click();
  await page.locator('#task-dialog').waitFor({ state: 'visible', timeout });
  await page.locator('#task-title').filter({ hasText: taskTitle }).waitFor({ timeout });
}

async function submit(page, buttonName, expectedStatus = 200) {
  const response = apiResponse(page, 'PATCH', 'tasks/' + task.id);
  await page.locator('#task-actions').getByRole('button', { name: buttonName, exact: true }).click();
  const result = await response;
  assert.equal(result.status(), expectedStatus);
  const body = await result.json();
  if (expectedStatus === 200) {
    task = body.data;
    await page.locator('#task-dialog').waitFor({ state: 'hidden', timeout });
  }
  return body;
}

(async () => {
  if (process.argv.includes('--prepare-android-fixture')) { await prepareAndroidFixture(); return; }
  if (process.argv.includes('--observe-android')) { await observeAndroid(); return; }
  await fs.mkdir(output, { recursive: true });
  try {
    const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
    browser = await chromium.launch({ executablePath: chrome, headless: true, args: ['--no-first-run', '--disable-default-apps'] });
    report.browser = browser.version();
    coordinatorContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    workerContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    coordinator = await coordinatorContext.newPage();
    worker = await workerContext.newPage();
    watch(coordinator, 'coordinator');
    watch(worker, 'worker');
    coordinator.setDefaultTimeout(timeout);
    worker.setDefaultTimeout(timeout);

    await check('01 Real web application and handled invalid login', async () => {
      const response = await coordinator.goto(base, { waitUntil: 'domcontentloaded' });
      assert.equal(response.status(), 200);
      await screenshot(coordinator, '01-login.png');
      await coordinator.locator('input[name="password"]').fill('Incorrect713!');
      const rejected = apiResponse(coordinator, 'POST', 'auth/login');
      await coordinator.locator('#login-form button').click();
      assert.equal((await rejected).status(), 401);
      await coordinator.locator('#login-error').filter({ hasText: 'Sign in with a valid account' }).waitFor();
      assert.equal(await coordinator.locator('#login-form button').isEnabled(), true);
      return { invalidLoginHttpStatus: 401, errorDisplayed: await coordinator.locator('#login-error').textContent() };
    });

    await check('02 Two isolated browser users authenticated over Payara', async () => {
      await signIn(coordinator, 'coordinator', 'Coordinator713!');
      await worker.goto(base, { waitUntil: 'domcontentloaded' });
      await signIn(worker, 'worker', 'Worker713!');
      assert.equal(await worker.locator('[data-view="projects"]').isVisible(), false);
      assert.equal(await worker.locator('[data-view="review"]').isVisible(), false);
      return { coordinatorWebSocketReady: received('coordinator', 'ready'), workerWebSocketReady: received('worker', 'ready') };
    });

    await check('03 Coordinator creates project through visible web form', async () => {
      await coordinator.locator('[data-view="projects"]').click();
      await coordinator.locator('#project-form input[name="name"]').fill(projectName);
      await coordinator.locator('#project-form input[name="description"]').fill('Real browser verification: shared field inspection workflow.');
      const created = apiResponse(coordinator, 'POST', 'projects');
      await coordinator.locator('#project-form button').click();
      const response = await created;
      assert.equal(response.status(), 201);
      project = (await response.json()).data;
      await coordinator.locator('.project-row').filter({ hasText: projectName }).getByRole('button', { name: 'Open board' }).click();
      await until(async () => await worker.locator(`#project-filter option[value="${project.id}"]`).count() === 1,
        'new project pushed to worker');
      await worker.locator('#project-filter').selectOption(String(project.id));
      return { projectId: project.id, projectName };
    });

    await check('04 Assignment appears in open worker browser through WebSocket', async () => {
      const prior = received('worker', 'task.changed');
      const workerNavigations = telemetry.worker.mainNavigations.length;
      await coordinator.locator('#new-task').click();
      await coordinator.locator('#task-detail select[name="projectId"]').selectOption(String(project.id));
      await coordinator.locator('#task-detail input[name="title"]').fill(taskTitle);
      await coordinator.locator('#task-detail textarea[name="description"]').fill('Check the entrance and provide field evidence.');
      // The seeded first worker is the account signed into the separate worker context.
      await coordinator.locator('#task-detail select[name="assignedTo"]').selectOption({ index: 0 });
      const created = apiResponse(coordinator, 'POST', `projects/${project.id}/tasks`);
      await coordinator.locator('#task-actions button').click();
      const response = await created;
      assert.equal(response.status(), 201);
      task = (await response.json()).data;
      await coordinator.locator('#task-dialog').waitFor({ state: 'hidden' });
      await expectTaskState(worker, task.id, 'TO DO');
      assert.ok(received('worker', 'task.changed') > prior);
      assert.equal(telemetry.worker.mainNavigations.length, workerNavigations);
      await screenshot(coordinator, '02-coordinator-assigned.png');
      return { taskId: task.id, taskTitle, workerReceivedPush: true, workerPageReloaded: false };
    });

    await check('05 Worker accepts and coordinator board updates without refresh', async () => {
      const prior = received('coordinator', 'task.changed');
      const navigationCount = telemetry.coordinator.mainNavigations.length;
      await openTask(worker);
      await submit(worker, 'Accept assignment');
      await expectTaskState(coordinator, task.id, 'IN PROGRESS');
      assert.ok(received('coordinator', 'task.changed') > prior);
      assert.equal(telemetry.coordinator.mainNavigations.length, navigationCount);
      return { taskStatus: task.status, receivedPush: true, coordinatorPageReloaded: false };
    });

    await check('06 Submission field error is displayed and recoverable', async () => {
      await openTask(worker);
      await worker.locator('textarea[name="completionNote"]').fill('   ');
      await worker.locator('input[name="latitude"]').fill('-36.8485');
      await worker.locator('input[name="longitude"]').fill('174.7633');
      await submit(worker, 'Submit for review', 400);
      await worker.locator('#task-error').filter({ hasText: 'completionNote must contain' }).waitFor();
      assert.equal(await worker.locator('#task-actions button').isEnabled(), true);
      return { httpStatus: 400, errorDisplayed: await worker.locator('#task-error').textContent() };
    });

    await check('07 Worker submits location evidence; coordinator receives it live', async () => {
      const prior = received('coordinator', 'task.changed');
      const navigationCount = telemetry.coordinator.mainNavigations.length;
      await worker.locator('textarea[name="completionNote"]').fill('Entrance inspected. Access path is clear; captured through the web manual location fallback.');
      await screenshot(worker, '03-worker-field-evidence.png');
      await submit(worker, 'Submit for review');
      await expectTaskState(coordinator, task.id, 'AWAITING REVIEW');
      assert.ok(received('coordinator', 'task.changed') > prior);
      assert.equal(telemetry.coordinator.mainNavigations.length, navigationCount);
      return { taskStatus: task.status, latitude: task.latitude, longitude: task.longitude,
        coordinatorReceivedPush: true, coordinatorPageReloaded: false };
    });

    await check('08 Coordinator requests rework and worker receives shared state', async () => {
      const prior = received('worker', 'task.changed');
      await openTask(coordinator);
      await coordinator.locator('.review-note').filter({ hasText: 'Entrance inspected' }).waitFor();
      await screenshot(coordinator, '04-coordinator-review.png');
      await submit(coordinator, 'Request rework');
      await expectTaskState(worker, task.id, 'IN PROGRESS');
      assert.ok(received('worker', 'task.changed') > prior);
      return { taskStatus: task.status, workerReceivedPush: true };
    });

    await check('09 Disconnection and reconnect button restore missed state', async () => {
      await coordinatorContext.setOffline(true);
      // Chromium can retain a previously established WebSocket while offline
      // emulation blocks new connections. Close the actual transport explicitly
      // so this test exercises the application's real close/reconnect handlers.
      await coordinator.evaluate(() => socket.close(4000, 'Connection interruption for recovery verification'));
      await until(async () => (await coordinator.locator('#socket-status').textContent()).includes('Reconnecting'),
        'disconnection status', 12000);
      await openTask(worker);
      await worker.locator('textarea[name="completionNote"]').fill('Rework complete: access route and door operation checked again.');
      await worker.locator('input[name="latitude"]').fill('-36.8485');
      await worker.locator('input[name="longitude"]').fill('174.7633');
      await submit(worker, 'Submit for review');
      await expectTaskState(coordinator, task.id, 'IN PROGRESS');
      const readyBefore = received('coordinator', 'ready');
      const navigationsBefore = telemetry.coordinator.mainNavigations.length;
      await coordinatorContext.setOffline(false);
      await coordinator.locator('#reconnect').click();
      await until(() => received('coordinator', 'ready') > readyBefore, 'new WebSocket authenticated');
      await expectTaskState(coordinator, task.id, 'AWAITING REVIEW');
      assert.equal(telemetry.coordinator.mainNavigations.length, navigationsBefore);
      return { interruption: 'Browser offline emulation plus explicit close of existing real WebSocket',
        missedStateRestored: true, authenticatedReconnect: true, coordinatorPageReloaded: false };
    });

    await check('10 Coordinator approves and worker sees approval live', async () => {
      const prior = received('worker', 'task.changed');
      await openTask(coordinator);
      await coordinator.locator('.review-note').filter({ hasText: 'Rework complete' }).waitFor();
      await submit(coordinator, 'Approve task');
      await expectTaskState(coordinator, task.id, 'APPROVED');
      await expectTaskState(worker, task.id, 'APPROVED');
      assert.equal(task.status, 'done');
      assert.ok(received('worker', 'task.changed') > prior);
      await screenshot(coordinator, '05-coordinator-approved.png');
      await screenshot(worker, '06-worker-approved.png');
      return { taskId: task.id, taskStatus: task.status, version: task.version, workerReceivedPush: true };
    });

    await check('11 No browser script errors or unexpected API server failures', async () => {
      for (const [role, record] of Object.entries(telemetry)) {
        assert.deepEqual(record.pageErrors, [], role + ' script errors');
        assert.deepEqual(record.api.filter(response => response.status >= 500), [], role + ' API server failures');
        assert.equal(record.mainNavigations.length, 1, role + ' navigated or reloaded during shared workflow');
      }
      return { coordinatorMainNavigations: telemetry.coordinator.mainNavigations.length,
        workerMainNavigations: telemetry.worker.mainNavigations.length,
        coordinatorTaskPushes: received('coordinator', 'task.changed'), workerTaskPushes: received('worker', 'task.changed') };
    });
    report.status = 'PASS';
    report.projectId = project.id;
    report.taskId = task.id;
  } catch (error) {
    report.status = 'FAIL';
    report.error = error.stack;
    process.exitCode = 1;
    for (const [role, page] of [['coordinator', coordinator], ['worker', worker]]) {
      if (page) await screenshot(page, `failure-${role}.png`).catch(() => {});
    }
  } finally {
    report.completedAt = new Date().toISOString();
    await saveReport();
    await browser?.close();
    console.log('Evidence: ' + output);
  }
})();
