const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const vscode = require('vscode');

const harness = __dirname;
const config = JSON.parse(fs.readFileSync(path.join(harness, 'config.json'), 'utf8').replace(/^\uFEFF/, ''));
const logFile = path.join(harness, 'events.jsonl');
const resultFile = path.join(harness, 'result.json');
const result = { startedAt: new Date().toISOString(), commands: [], status: 'running', scope: 'Actual installed Payara Tools commands inside a real VS Code extension host; registration seeded in an isolated test profile.' };

function log(event, details = {}) {
  fs.appendFileSync(logFile, JSON.stringify({ time: new Date().toISOString(), event, ...details }) + '\n');
  console.log('[COMP713 Payara smoke] ' + event + ' ' + JSON.stringify(details));
  fs.writeFileSync(resultFile, JSON.stringify(result, null, 2));
}
function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
function get(url) {
  return new Promise(resolve => {
    const request = http.get(url, { timeout: 4000, headers: { Accept: 'application/json' } }, response => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', chunk => { body += chunk; });
      response.on('end', () => resolve({ status: response.statusCode, body: body.slice(0, 16000) }));
    });
    request.on('error', error => resolve({ error: error.message }));
    request.on('timeout', () => request.destroy(new Error('HTTP timeout')));
  });
}
async function until(description, check, timeoutMs = 180000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await check();
    if (value) { log(description, { success: true }); return value; }
    await delay(1000);
  }
  throw new Error('Timed out: ' + description);
}
async function command(id, ...args) {
  result.commands.push({ id, invokedAt: new Date().toISOString() });
  log('registered-command-invoked', { id });
  return vscode.commands.executeCommand(id, ...args);
}

exports.run = async function () {
  try {
    log('test-host-started', { vscodeVersion: vscode.version, workspace: vscode.workspace.workspaceFolders?.map(folder => folder.uri.fsPath) });
    const ext = vscode.extensions.getExtension('payara.payara-vscode');
    if (!ext) throw new Error('The installed Payara Tools extension was not visible in this real VS Code extension host.');
    result.vscodeVersion = vscode.version;
    result.payaraToolsVersion = ext.packageJSON.version;
    result.payaraExtensionPath = ext.extensionPath;
    result.javaExtensions = vscode.extensions.all.filter(entry => /^(redhat\.java|vscjava\.)/.test(entry.id)).map(entry => ({ id: entry.id, version: entry.packageJSON.version }));
    if (config.mavenRepository) {
      // Maven.cmd passes MAVEN_OPTS to java, preserving this quoted property value.
      // This changes only the isolated host process and its build children.
      process.env.MAVEN_OPTS = (process.env.MAVEN_OPTS || '') + ' -Dmaven.repo.local="' + config.mavenRepository + '"';
      log('process-local-Maven-repository', { path: config.mavenRepository });
    }

    await ext.activate();
    // Context-menu commands receive a local-server model. Instantiate the installed
    // model class with exactly the same domain as the single registration seeded in
    // this isolated profile. No extension source or command behavior is replaced.
    const Server = require(path.join(ext.extensionPath, 'out/main/fish/payara/server/PayaraLocalServerInstance.js')).PayaraLocalServerInstance;
    const server = new Server(config.serverName, 'domain1', config.payaraPath);
    server.setJDKHome(config.jdkHome);
    server.setUsername('admin');
    server.setPassword('');
    result.server = { name: server.getName(), domain: server.getDomainName(), path: server.getPath(), jdkHome: server.getJDKHome(), httpPort: server.getHttpPort(), adminPort: server.getAdminPort() };
    log('installed-extension-activated', { toolsVersion: result.payaraToolsVersion, server: result.server, javaExtensions: result.javaExtensions });

    const before = await get(config.healthUrl);
    log('health-before-commands', before);
    // The parent task coordinates the stopped domain. A running domain is reported
    // explicitly; it is never credited as a newly verified Start action.
    const adminBefore = await get('http://127.0.0.1:' + server.getAdminPort() + '/management/domain');
    if (adminBefore.status === 200) {
      result.startStatus = 'Already running before harness; Start was not invoked.';
      server.setStarted(true);
      log('domain-already-running', { adminHttpStatus: adminBefore.status });
    } else {
      await command('payara.server.start', server);
      await until('domain-started-through-Payara-Tools', async () => server.isStarted() && (await get('http://127.0.0.1:' + server.getAdminPort() + '/management/domain')).status === 200);
      result.startStatus = 'Started by payara.server.start inside VS Code.';
    }
    await command('payara.server.refresh.all');
    await delay(5000);

    // This is the real installed command, which selects the sole registered server,
    // builds the Maven project, and deploys it via the installed deployment logic.
    const deploymentStartedAt = new Date().toISOString();
    const serverLog = server.getServerLog();
    const logOffset = fs.existsSync(serverLog) ? fs.readFileSync(serverLog, 'utf8').length : 0;
    const buildLogFile = path.join(harness, 'payara-tools-build.log');
    await command('payara.server.app.deploy', vscode.Uri.file(config.projectPath).toString());
    await until('fresh-domain-log-confirms-Payara-Tools-deployment', async () => {
      const fresh = fs.readFileSync(serverLog, 'utf8').slice(logOffset);
      return /comp713-a3 was successfully deployed/i.test(fresh);
    }, 300000);
    const health = await until('application-responds-after-Payara-Tools-deploy', async () => {
      const response = await get(config.healthUrl);
      if (response.status !== 200) return false;
      try { JSON.parse(response.body); return response; } catch { return false; }
    }, 300000);
    // HTTP alone could be an older deployment, so independently require a fresh
    // successful deployment entry in this domain's real server log.
    const deploymentLog = await until('fresh-domain-log-confirms-deployment', async () => {
      const content = fs.readFileSync(serverLog, 'utf8').slice(logOffset);
      const lines = content.split(/\r?\n/);
      const hits = lines.filter(line => /deploy|loading application/i.test(line) && /comp713-a3/i.test(line));
      return hits.length ? hits.slice(-12) : false;
    }, 120000);
    result.deployment = { command: 'payara.server.app.deploy', invokedAt: deploymentStartedAt, healthUrl: config.healthUrl, health, serverLog, deploymentLog, buildLogFile };
    result.status = 'passed';
    result.completedAt = new Date().toISOString();
    log('ready-for-API-tests', { health, resultFile });
    fs.writeFileSync(path.join(harness, 'READY'), result.completedAt);

    // Keep this genuine VS Code host available for screenshots and the parent task's
    // real HTTP tests. Creating FINISH ends only the test host, not an asadmin action.
    const finishBy = Date.now() + 30 * 60000;
    while (!fs.existsSync(path.join(harness, 'FINISH')) && Date.now() < finishBy) await delay(1000);
    log('test-host-finished');
  } catch (error) {
    result.status = 'failed';
    result.error = error.stack || String(error);
    log('test-failed', { error: result.error });
    throw error;
  }
};
