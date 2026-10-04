#!/usr/bin/env node
// Render a storyboard JSON to MP4: headless Chromium captures every frame of engine/player.html,
// frames are piped into ffmpeg, and the optional voiceover / music is muxed in.
//
//   node render.mjs <storyboard.json> <out.mp4> [--fps 30] [--width 1080] [--from 0] [--to 12]
//   node render.mjs <storyboard.json> <out_dir> --stills 1,6.5,20     # PNG previews only
import { createRequire } from 'node:module';
import { execSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
function loadPlaywright() {
  try { return require('playwright'); } catch {}
  const root = execSync('npm root -g').toString().trim();
  return require(path.join(root, 'playwright'));
}

const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf('--' + k); return i >= 0 ? args[i + 1] : d; };
const [sbPath, out] = args.filter((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')));
if (!sbPath || !out) { console.error('usage: render.mjs <storyboard.json> <out.mp4|dir> [--fps N] [--width PX] [--from S] [--to S] [--stills t1,t2]'); process.exit(1); }

const sb = JSON.parse(fs.readFileSync(sbPath, 'utf8'));
const sbDir = path.dirname(path.resolve(sbPath));
const fps = +opt('fps', sb.meta?.fps || 30);
const width = +opt('width', sb.meta?.width || 1080);
const height = Math.round(width * 16 / 9 / 2) * 2;
sb.meta = { ...(sb.meta || {}), width, height };

const here = path.dirname(fileURLToPath(import.meta.url));
const player = pathToFileURL(path.join(here, '..', 'engine', 'player.html')).href;

const { chromium } = loadPlaywright();
const launch = {};
if (fs.existsSync('/opt/pw-browsers/chromium')) {
  // cloud containers ship a pinned Chromium; fall back to Playwright's default elsewhere
  try { launch.executablePath = execSync('ls -d /opt/pw-browsers/chromium-*/chrome-linux/chrome 2>/dev/null | head -1').toString().trim() || undefined; } catch {}
}
const browser = await chromium.launch(launch);
const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
page.on('pageerror', e => console.error('page error:', e.message));
await page.goto(player);
await page.evaluate(s => window.__load(s), sb);
const duration = await page.evaluate(() => window.__duration());

const stills = opt('stills');
if (stills) {
  fs.mkdirSync(out, { recursive: true });
  for (const t of stills.split(',').map(Number)) {
    await page.evaluate(t => window.__seek(t), t);
    const f = path.join(out, `still_${t.toFixed(2)}.png`);
    await page.screenshot({ path: f });
    console.log(f);
  }
  await browser.close();
  process.exit(0);
}

const from = +opt('from', 0), to = Math.min(+opt('to', duration), duration);
const frames = Math.round((to - from) * fps);
const ff = ['-y', '-v', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-i', '-'];
const audio = sb.audio ? path.resolve(sbDir, sb.audio) : null;
const music = sb.music?.file ? path.resolve(sbDir, sb.music.file) : null;
if (audio) ff.push('-ss', String(from), '-i', audio);
if (music) ff.push('-stream_loop', '-1', '-ss', String(from), '-i', music);
if (audio && music) {
  const vol = sb.music.volume ?? 0.12;
  ff.push('-filter_complex', `[2:a]volume=${vol}[m];[1:a][m]amix=inputs=2:duration=first:normalize=0[a]`, '-map', '0:v', '-map', '[a]');
} else if (music) {
  ff.push('-filter_complex', `[1:a]volume=${sb.music.volume ?? 0.3}[a]`, '-map', '0:v', '-map', '[a]');
}
ff.push('-t', String(to - from), '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p');
if (audio || music) ff.push('-c:a', 'aac', '-b:a', '192k');
ff.push('-movflags', '+faststart', out);

const enc = spawn('ffmpeg', ff, { stdio: ['pipe', 'inherit', 'inherit'] });
const done = new Promise((res, rej) => enc.on('close', c => c === 0 ? res() : rej(new Error('ffmpeg exited ' + c))));
const t0 = Date.now();
for (let f = 0; f < frames; f++) {
  await page.evaluate(t => window.__seek(t), from + f / fps);
  const buf = await page.screenshot({ type: 'jpeg', quality: 95 });
  if (!enc.stdin.write(buf)) await new Promise(r => enc.stdin.once('drain', r));
  if (f % (fps * 5) === 0) process.stdout.write(`\rframe ${f}/${frames}  ${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
enc.stdin.end();
await done;
await browser.close();
console.log(`\nwrote ${out} (${(to - from).toFixed(1)}s @ ${fps}fps, ${width}x${height})`);
