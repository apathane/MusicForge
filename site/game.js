// Buffer: catch the 4 MiB chunks, dodge the 403s, watch the update reach 99% and start over.
// It is the download engine's real life, minus the part where it eventually works.
(() => {
  const game = document.getElementById("buffer-game");
  const canvas = document.getElementById("buffer-canvas");
  if (!game || !canvas) return;
  const ctx = canvas.getContext("2d");
  const overlay = document.getElementById("buffer-overlay");
  const message = document.getElementById("buffer-message");
  const startButton = document.getElementById("buffer-start");
  const mbLabel = document.getElementById("buffer-mb");
  const bestLabel = document.getElementById("buffer-best");
  const livesLabel = document.getElementById("buffer-lives");
  const bar = game.querySelector(".buffer-bar");
  const fill = document.getElementById("buffer-fill");

  const CHUNK_MB = 4;
  const LIVES = 3;
  let best = 0;
  try { best = Number(localStorage.getItem("musicforge-buffer-best")) || 0; } catch (_) {}
  bestLabel.textContent = `${best} MB`;

  let colors = {};
  function readColors() {
    const css = getComputedStyle(document.documentElement);
    const v = (name) => css.getPropertyValue(name).trim();
    colors = { ink: v("--ink"), muted: v("--muted"), soft: v("--soft"), accent: v("--accent"), accentInk: v("--accent-ink"), line: v("--line-strong") };
  }
  readColors();
  // The site's theme toggle flips a class on <html>; repaint with the new palette.
  new MutationObserver(() => { readColors(); if (!running) draw(); }).observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });

  let w = 0, h = 0, dpr = 1;
  function resize() {
    const rect = canvas.getBoundingClientRect();
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = rect.width; h = rect.height;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    basket.y = h - 26;
    basket.x = Math.min(Math.max(basket.x || w / 2, basket.w / 2), w - basket.w / 2);
    if (!running) draw();
  }

  const basket = { x: 0, y: 0, w: 78, h: 10, target: null };
  let items = [], running = false, paused = false, last = 0, spawnIn = 0;
  let mb = 0, lives = LIVES, level = 1, sizeMb = 0, progressMb = 0, flash = null;

  function newUpdate() { sizeMb = 64 + Math.round(Math.random() * 14) * 4; progressMb = 0; }
  function percent() { return Math.min(99, Math.floor((progressMb / sizeMb) * 100)); }
  function hud() {
    mbLabel.textContent = `${mb} MB`;
    livesLabel.textContent = String(lives);
    const p = percent();
    fill.style.width = `${p}%`;
    bar.setAttribute("aria-valuenow", String(p));
  }

  function start() {
    items = []; mb = 0; lives = LIVES; level = 1; spawnIn = 0; flash = null; paused = false;
    newUpdate(); hud();
    overlay.hidden = true;
    running = true; last = performance.now();
    canvas.focus({ preventScroll: true });
    requestAnimationFrame(frame);
  }

  function end() {
    running = false;
    flash = null; draw();
    if (mb > best) {
      best = mb;
      try { localStorage.setItem("musicforge-buffer-best", String(best)); } catch (_) {}
      bestLabel.textContent = `${best} MB`;
    }
    message.textContent = mb > 0
      ? `Download failed after ${mb} MB. The update is still out there. Somewhere.`
      : "Download failed before it started. Very on brand.";
    startButton.textContent = "Retry";
    overlay.hidden = false;
    startButton.focus({ preventScroll: true });
  }

  function setPaused(value) {
    if (!running || paused === value) return;
    paused = value;
    message.textContent = "Paused. The update is not going anywhere either.";
    startButton.textContent = "Resume";
    overlay.hidden = !paused;
    if (!paused) { last = performance.now(); requestAnimationFrame(frame); }
  }

  function spawn() {
    const bad = Math.random() < Math.min(0.18 + level * 0.04, 0.42);
    const size = 30;
    items.push({ x: size / 2 + Math.random() * (w - size), y: -size, size, bad, speed: (120 + level * 28) * (0.85 + Math.random() * 0.3) });
  }

  function frame(now) {
    if (!running || paused) return;
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;

    const keySpeed = 420;
    if (keys.left) basket.x -= keySpeed * dt;
    if (keys.right) basket.x += keySpeed * dt;
    if (basket.target !== null) basket.x += (basket.target - basket.x) * Math.min(1, dt * 18);
    basket.x = Math.min(Math.max(basket.x, basket.w / 2), w - basket.w / 2);

    spawnIn -= dt;
    if (spawnIn <= 0) { spawn(); spawnIn = Math.max(0.32, 0.9 - level * 0.07); }

    for (const it of items) it.y += it.speed * dt;
    const caught = items.filter((it) => it.y + it.size / 2 >= basket.y && it.y - it.size / 2 <= basket.y + basket.h && Math.abs(it.x - basket.x) <= basket.w / 2 + it.size / 2);
    for (const it of caught) {
      if (it.bad) {
        lives -= 1;
        flash = { text: "403 · client retired", until: now + 900 };
      } else {
        mb += CHUNK_MB; progressMb += CHUNK_MB;
        // The rule this site's app actually enforces: a reopened file must keep its length.
        if (progressMb / sizeMb >= 0.99) {
          level += 1; newUpdate();
          flash = { text: "99% · rendition changed, starting over", until: now + 1400 };
        }
      }
    }
    items = items.filter((it) => !caught.includes(it) && it.y - it.size / 2 < h);
    hud();
    draw(now);
    if (lives <= 0) { end(); return; }
    requestAnimationFrame(frame);
  }

  function roundRect(x, y, ww, hh, r) {
    ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + ww, y, x + ww, y + hh, r); ctx.arcTo(x + ww, y + hh, x, y + hh, r);
    ctx.arcTo(x, y + hh, x, y, r); ctx.arcTo(x, y, x + ww, y, r); ctx.closePath();
  }

  function draw(now = performance.now()) {
    ctx.clearRect(0, 0, w, h);
    ctx.font = '500 10px "DM Mono", ui-monospace, monospace';
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    for (const it of items) {
      const x = it.x - it.size / 2, y = it.y - it.size / 2;
      roundRect(x, y, it.size, it.size, 6);
      if (it.bad) {
        ctx.strokeStyle = colors.muted; ctx.lineWidth = 1.5; ctx.stroke();
        ctx.fillStyle = colors.muted; ctx.fillText("403", it.x, it.y + 0.5);
      } else {
        ctx.fillStyle = colors.accent; ctx.fill();
        ctx.fillStyle = colors.accentInk; ctx.fillText("4M", it.x, it.y + 0.5);
      }
    }
    roundRect(basket.x - basket.w / 2, basket.y, basket.w, basket.h, 5);
    ctx.fillStyle = colors.ink; ctx.fill();
    ctx.strokeStyle = colors.line; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(0, h - 8.5); ctx.lineTo(w, h - 8.5); ctx.stroke();
    if (flash && now < flash.until) {
      ctx.font = '700 15px Manrope, system-ui, sans-serif';
      ctx.fillStyle = colors.ink; ctx.fillText(flash.text, w / 2, h * 0.38);
    }
  }

  const keys = { left: false, right: false };
  function onKey(event, down) {
    const k = event.key.toLowerCase();
    if (k === "arrowleft" || k === "a") { keys.left = down; basket.target = null; }
    else if (k === "arrowright" || k === "d") { keys.right = down; basket.target = null; }
    else if (down && (k === " " || k === "p") && running) setPaused(!paused);
    else return;
    event.preventDefault();
  }
  canvas.tabIndex = 0;
  canvas.addEventListener("keydown", (e) => onKey(e, true));
  canvas.addEventListener("keyup", (e) => onKey(e, false));
  canvas.addEventListener("pointerdown", (e) => { canvas.setPointerCapture(e.pointerId); basket.target = e.offsetX; });
  canvas.addEventListener("pointermove", (e) => { if (e.buttons || e.pointerType === "mouse") basket.target = e.offsetX; });

  startButton.addEventListener("click", () => (running && paused ? setPaused(false) : start()));
  document.addEventListener("visibilitychange", () => { if (document.hidden) setPaused(true); });
  if ("IntersectionObserver" in window) {
    new IntersectionObserver(([entry]) => { if (!entry.isIntersecting) setPaused(true); }, { threshold: 0.2 }).observe(game);
  }
  window.addEventListener("resize", resize);
  resize();
})();
