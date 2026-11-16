// Captures the README media from a running demo stack:
//   docs/media/dashboard.png  the fleet dashboard while a rollout is running
//   docs/media/rollout.gif    a rollout of v1.2.0 moving through its stages, live
//
// Needs: `docker compose up --build` (demo data on), `npx playwright install chromium` once, and
// ffmpeg on the PATH. Run from pulse-dashboard: `npm run media`. Each run starts a new rollout,
// so use a fresh stack (`docker compose down -v && docker compose up --build`) for clean captures.
import { chromium } from 'playwright';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.PULSE_URL ?? 'http://localhost:3001';
const ADMIN = { email: 'admin@pulse.demo', password: 'demo-admin' };
const ROLLOUT = { version: 'v1.2.0', model: 'Pulse Aurora', stages: [25, 100], threshold: 40 };
const VIEWPORT = { width: 1440, height: 900 };
const COMMANDS = ['LOCK', 'CLIMATE_ON', 'FLASH_LIGHTS'];
const GIF_MAX_SECONDS = 15;
const GIF_MAX_BYTES = 3 * 1024 * 1024;
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/media');

function fail(message) {
  console.error(`capture-readme-media: ${message}`);
  process.exit(1);
}

async function api(path, { token, method = 'GET', body } = {}) {
  const response = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  if (!response.ok) fail(`${method} ${path} returned ${response.status}: ${await response.text()}`);
  return response.json();
}

async function signIn(page) {
  await page.goto(`${BASE}/login`);
  await page.fill('input[type=email]', ADMIN.email);
  await page.fill('input[type=password]', ADMIN.password);
  await page.click('button[type=submit]');
  await page.waitForURL(url => !url.pathname.startsWith('/login'));
  await page.getByText('Live updates connected').waitFor();
}

// --- preconditions ---
if (spawnSync('ffmpeg', ['-version']).status !== 0) fail('ffmpeg is not on the PATH');
try {
  await fetch(BASE);
} catch {
  fail(`nothing is listening at ${BASE}; start the stack with docker compose up --build`);
}

const { accessToken: token } = await api('/api/v1/auth/login', { method: 'POST', body: ADMIN });
const version = (await api('/api/v1/software-versions', { token })).find(v => v.versionLabel === ROLLOUT.version);
if (!version) fail(`software version ${ROLLOUT.version} not found; is DEMO_ENABLED on?`);
const campaign = await api('/api/v1/rollouts', {
  token, method: 'POST',
  body: {
    softwareVersionId: version.id, targetModel: ROLLOUT.model,
    stagePercentages: ROLLOUT.stages, failureThresholdPercent: ROLLOUT.threshold,
  },
});

mkdirSync(OUT, { recursive: true });
const videoDir = mkdtempSync(join(tmpdir(), 'pulse-media-'));
const browser = await chromium.launch();

const recording = await browser.newContext({ viewport: VIEWPORT, recordVideo: { dir: videoDir, size: VIEWPORT } });
const rollout = await recording.newPage();
const recordingStarted = Date.now();
await signIn(rollout);
await rollout.goto(`${BASE}/rollouts/${campaign.id}`);
await rollout.getByRole('button', { name: 'Start' }).waitFor();
const clipStart = (Date.now() - recordingStarted) / 1000;
await rollout.waitForTimeout(800);
await rollout.getByRole('button', { name: 'Start' }).click();

// The dashboard opens after the start, so it counts the running rollout, and is subscribed before
// a few remote commands run, so their results show up in its live activity feed.
const dashboard = await (await browser.newContext({ viewport: VIEWPORT })).newPage();
await signIn(dashboard);
const fleet = await api('/api/v1/vehicles', { token });
for (const [i, type] of COMMANDS.entries()) {
  await api(`/api/v1/vehicles/${fleet[i].id}/commands`, { token, method: 'POST', body: { type } });
}

// Command results listed in the dashboard's live activity panel.
const commandResults = () => dashboard
  .locator('.glass-panel', { has: dashboard.getByRole('heading', { name: 'Live Activity' }) })
  .getByText(/completed|failed|timed out/i)
  .count();
// Closes the pop-up toasts (the same results are in the activity panel), then captures the top of
// the page, where the content is.
async function captureDashboard() {
  const close = dashboard.getByRole('button', { name: '×' });
  while (await close.count()) await close.first().click();
  await dashboard.screenshot({ path: join(OUT, 'dashboard.png'), clip: { x: 0, y: 0, width: VIEWPORT.width, height: 580 } });
}

let screenshotTaken = false;
let status = 'IN_PROGRESS';
const deadline = Date.now() + 120_000;
while (Date.now() < deadline && status === 'IN_PROGRESS') {
  await rollout.waitForTimeout(1000);
  status = (await api(`/api/v1/rollouts/${campaign.id}`, { token })).status;
  if (!screenshotTaken && (await commandResults()) >= COMMANDS.length) {
    await captureDashboard();
    screenshotTaken = true;
  }
}
if (!screenshotTaken) {
  for (let i = 0; i < 15 && (await commandResults()) < COMMANDS.length; i++) await dashboard.waitForTimeout(1000);
  await captureDashboard();
}
await rollout.waitForTimeout(2000);
const clipEnd = (Date.now() - recordingStarted) / 1000;
const video = await rollout.video().path();
await recording.close();
await browser.close();

// Speed the clip up to fit the GIF length, then use a shared palette so the GIF stays small.
const speed = Math.max(1, (clipEnd - clipStart) / GIF_MAX_SECONDS);
const gif = join(OUT, 'rollout.gif');
for (const width of [960, 800, 640]) {
  const filters = `setpts=PTS/${speed.toFixed(2)},fps=8,scale=${width}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=bayer:bayer_scale=4`;
  const run = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-ss', clipStart.toFixed(2), '-i', video,
    '-filter_complex', filters, gif], { stdio: 'inherit' });
  if (run.status !== 0) fail('ffmpeg failed');
  if (statSync(gif).size <= GIF_MAX_BYTES) break;
}
rmSync(videoDir, { recursive: true, force: true });

console.log(`rollout ended ${status} after ${(clipEnd - clipStart).toFixed(0)}s (GIF at ${speed.toFixed(1)}x)`);
console.log(`wrote ${join(OUT, 'dashboard.png')} and ${gif} (${(statSync(gif).size / 1024).toFixed(0)} KB)`);
