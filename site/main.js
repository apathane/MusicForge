// MusicForge site v0.2 — Signal Design System
// Motion: reactive only (cursor, scroll, hover, input). Never on a timer.
// No framework, no AI slop, no endless pulses.

const html = document.documentElement;
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

// === Theme toggle — dark (default) / light, persisted to localStorage ===
const themeToggle = document.getElementById("theme-toggle");
const savedTheme = localStorage.getItem("musicforge-theme");

if (savedTheme === "light") {
  document.body.classList.add("theme-light");
  themeToggle.setAttribute("aria-checked", "true");
}

function toggleTheme() {
  const isLight = document.body.classList.toggle("theme-light");
  themeToggle.setAttribute("aria-checked", isLight);
  localStorage.setItem("musicforge-theme", isLight ? "light" : "dark");
}

themeToggle.addEventListener("click", toggleTheme);

// === Screenshot rail — CSS scroll-snap, buttons step one card ===
// Scrolling stays native so it works with JS off and keyboard.
const rail = document.querySelector("[data-rail]");

if (rail) {
  document.querySelectorAll("[data-step]").forEach((button) => {
    button.addEventListener("click", () => {
      const card = rail.querySelector(".slide");
      if (!card) return;

      const gap = parseFloat(getComputedStyle(rail).columnGap) || 16;
      const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

      rail.scrollBy({
        left: (card.offsetWidth + gap) * Number(button.dataset.step),
        behavior: reduced ? "auto" : "smooth",
      });
    });
  });
}

// === Caption heckling — dry humor under each screenshot ===
const captions = {
  player: "When it works. Eventually.",
  home: "Your plays. Mostly.",
  queue: "Up next. If you remember.",
  artist: "They exist somewhere.",
};

document.querySelectorAll(".slide img").forEach((img, i) => {
  const fig = img.parentElement;
  const cap = fig.querySelector("figcaption");
  const key = Object.keys(captions)[i % Object.keys(captions).length];
  if (cap) cap.textContent = captions[key];
});

// === Easter egg: Ctrl+Shift+Z toggles .za-mode ===
// Nod to the en-ZA locale bug that returned HTTP 400 on every YouTube request
// on the real phone while the en-US emulator looked fine.
let zaMode = false;

function toggleZaMode() {
  zaMode = !zaMode;
  html.classList.toggle("za-mode", zaMode);

  const pitch = document.getElementById("pitch");
  const bigNumbers = document.querySelectorAll(".big");

  if (zaMode) {
    pitch.textContent = "YouTube Music, forged in ZA.";
    bigNumbers.forEach((n) => {
      n.classList.add("za-flash");
    });
  } else {
    pitch.textContent = "YouTube Music, forged for offline.";
    bigNumbers.forEach((n) => {
      n.classList.remove("za-flash");
    });
  }
}

document.addEventListener("keydown", (event) => {
  if (event.ctrlKey && event.shiftKey && event.key === "Z") {
    event.preventDefault();
    toggleZaMode();
  }
});

// === Panic button ===
// Top-right. Clicking it flashes red and does nothing useful.
// It exists because something has to panic.
const panic = document.getElementById("panic");

if (panic) {
  panic.addEventListener("click", () => {
    panic.style.background = "#5A0000";
    panic.style.borderColor = "#FF4D4D";
    panic.style.color = "#FFB3B3";
    panic.textContent = "PANICKING";
    setTimeout(() => {
      panic.style.background = "";
      panic.style.borderColor = "";
      panic.style.color = "";
      panic.textContent = "PANIC";
    }, 700);
  });
}

// === Cursor trail dots — mousedown only, reduced-motion safe ===
if (!reducedMotion.matches) {
  document.addEventListener("mousedown", (event) => {
    const dot = document.createElement("div");
    dot.className = "trail";
    dot.style.left = `${event.clientX}px`;
    dot.style.top = `${event.clientY}px`;
    document.body.appendChild(dot);
    setTimeout(() => dot.remove(), 1200);
  });
}

// === The Room — cursor-following light pool with a mood state machine ===
// Ported from the Two Libertines "room breathes at frame rate" pattern:
// requestAnimationFrame writes CSS custom properties, not DOM state.
// Moods: arriving → wanted → restless → cold → leaving
// Motion is reactive: follows cursor, reacts to idle, reacts to visibility.
const room = document.getElementById("room");
const roomLight = document.getElementById("room-light");
const heroTitle = document.getElementById("hero-title");

const MOODS = {
  arriving:  { spotColor: "rgba(180, 83, 73, 0.10)", spotR: "420px" },
  wanted:    { spotColor: "rgba(216, 199, 160, 0.16)", spotR: "520px" },
  restless:  { spotColor: "rgba(255, 77, 77, 0.12)", spotR: "640px" },
  cold:      { spotColor: "rgba(138, 138, 138, 0.08)", spotR: "260px" },
  leaving:   { spotColor: "rgba(180, 83, 73, 0.06)", spotR: "420px" },
};

let mood = "arriving";
let targetX = 0;
let targetY = 0;
let currentX = 0;
let currentY = 0;
let idleTimer = null;
let leavingLatched = false;
let keystrokeBuffer = "";
const keystrokeWords = ["panic", "sonata", "forge", "offline", "gpl"];

// Smooth follow: lerp current toward target each frame
function lerp(a, b, t) {
  return a + (b - a) * t;
}

function frame() {
  currentX = lerp(currentX, targetX, 0.12);
  currentY = lerp(currentY, targetY, 0.12);

  if (room) {
    room.style.setProperty("--spot-x", `${currentX}px`);
    room.style.setProperty("--spot-y", `${currentY}px`);
  }

  if (!reducedMotion.matches) requestAnimationFrame(frame);
}

if (!reducedMotion.matches) requestAnimationFrame(frame);

document.addEventListener("mousemove", (event) => {
  if (reducedMotion.matches) return;
  targetX = event.clientX;
  targetY = event.clientY;

  if (mood === "leaving" && !leavingLatched) return;

  setMood("wanted");
  scheduleIdle();
});

function scheduleIdle() {
  if (idleTimer) clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    if (mood !== "leaving") setMood("restless");
  }, 6500);
}

scheduleIdle();

function setMood(next) {
  if (next === mood) return;
  mood = next;
  const m = MOODS[mood];

  if (room) {
    room.className = `room room--${mood}`;
  }
  if (roomLight) {
    roomLight.style.setProperty("--spot-color", m.spotColor);
    roomLight.style.setProperty("--spot-r", m.spotR);
  }

  // Title changes communicate mood to the visitor
  if (heroTitle) {
    if (mood === "leaving" && !leavingLatched) {
      leavingLatched = true;
      heroTitle.textContent = "MusicForge — don't leave before I do —";
    } else if (mood !== "leaving") {
      heroTitle.textContent = "MusicForge";
    }
  }
}

// Cursor entering top 5% of viewport → restless (heading for the chrome/leave)
document.addEventListener("mousemove", (event) => {
  if (reducedMotion.matches) return;
  if (event.clientY < window.innerHeight * 0.05 && mood !== "leaving") {
    setMood("restless");
  }
});

// Tab leaving / returning
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    setMood("leaving");
  } else if (leavingLatched) {
    room.classList.add("room--cold");
    setMood("cold");
  }
});

// Click anywhere to return from leaving state
document.addEventListener("click", () => {
  if (leavingLatched && mood === "cold") {
    leavingLatched = false;
    setMood("wanted");
  }
});

// Keystroke buffer — type certain words and the site reacts
document.addEventListener("keydown", (event) => {
  const key = event.key.toLowerCase();
  if (/^[a-z]$/.test(key)) {
    keystrokeBuffer = (keystrokeBuffer + key).slice(-20);
    keystrokeWords.forEach((word) => {
      if (keystrokeBuffer.includes(word)) {
        setMood("restless");
        if (word === "panic" && panic) panic.click();
        keystrokeBuffer = "";
      }
    });
  }
});

// === Reveal observer — sections fade in with stagger as they enter ===
const revealObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        revealObserver.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.12, rootMargin: "0px 0px -80px 0px" }
);

document.querySelectorAll(".reveal").forEach((el) => revealObserver.observe(el));

// === Scroll progress bar — reacts to scroll position ===
const progressBar = document.createElement("div");
progressBar.className = "scroll-progress";
document.documentElement.insertBefore(progressBar, document.documentElement.firstChild);

function updateProgress() {
  const winScroll = window.scrollY;
  const height = document.documentElement.scrollHeight - window.innerHeight;
  const scrolled = height ? (winScroll / height) * 100 : 0;
  progressBar.style.width = `${scrolled}%`;
}

window.addEventListener("scroll", updateProgress, { passive: true });
updateProgress();

// === Magnetic CTA — subtle parallax on the primary Download button ===
// Gated by fine pointer + no reduced motion.
const finePointer = window.matchMedia("(pointer: fine)");

if (finePointer.matches && !reducedMotion.matches) {
  document.querySelectorAll(".btn-accent").forEach((btn) => {
    const face = btn.querySelector(".btn-face");
    if (!face) return;

    btn.addEventListener("pointermove", (event) => {
      const rect = btn.getBoundingClientRect();
      const x = event.clientX - rect.left - rect.width / 2;
      const y = event.clientY - rect.top - rect.height / 2;

      btn.style.transform = `translate(${x * 0.12}px, ${y * 0.18}px)`;
      face.style.transform = `translate(${x * 0.08}px, ${y * 0.1}px)`;
    });

    btn.addEventListener("pointerleave", () => {
      btn.style.transform = "";
      face.style.transform = "";
    });
  });
}

// === Text scramble — hero title shuffles on hover, pure JS ===
// Gated by fine pointer + no reduced motion.
if (finePointer.matches && !reducedMotion.matches) {
  const scrambleChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%^&*";
  const heroTitleEl = document.getElementById("hero-title");
  if (heroTitleEl) {
    const originalTitle = heroTitleEl.textContent;
    let scrambleScheduled = false;

    function scrambleText(el, original, duration = 800) {
      let iteration = 0;
      const interval = setInterval(() => {
        el.textContent = original
          .split("")
          .map((char, index) => {
            if (index < iteration) return original[index];
            return scrambleChars[Math.floor(Math.random() * scrambleChars.length)];
          })
          .join("");

        if (iteration >= original.length) clearInterval(interval);
        iteration += 1 / 3;
      }, 30);

      setTimeout(() => {
        clearInterval(interval);
        el.textContent = original;
      }, duration);
    }

    heroTitleEl.addEventListener("pointerenter", () => {
      if (!scrambleScheduled) {
        scrambleScheduled = true;
        scrambleText(heroTitleEl, originalTitle, 800);
        setTimeout(() => {
          scrambleScheduled = false;
        }, 1500);
      }
    });
  }
}

// === Feature card 3D tilt — subtle, pointer-driven, gated ===
if (finePointer.matches && !reducedMotion.matches) {
  document.querySelectorAll(".feature").forEach((card) => {
    card.addEventListener("pointermove", (event) => {
      const rect = card.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      const centerX = rect.width / 2;
      const centerY = rect.height / 2;
      const rotateX = ((y - centerY) / centerY) * -2;
      const rotateY = ((x - centerX) / centerX) * 2;
      card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
    });

    card.addEventListener("pointerleave", () => {
      card.style.transform = "";
    });
  });
}

// === Mask reveal — hero title glows under cursor ===
// Pure CSS gradient driven by JS pointer position. Gated by reduced motion.
if (finePointer.matches && !reducedMotion.matches) {
  const maskReveal = document.createElement("style");
  document.head.appendChild(maskReveal);

  const heroTitleEl = document.getElementById("hero-title");
  if (heroTitleEl) {
    let maskTimeout = null;

    heroTitleEl.addEventListener("pointermove", (event) => {
      const x = (event.clientX / window.innerWidth) * 100;
      const y = (event.clientY / window.innerHeight) * 100;
      maskReveal.textContent = `
        #hero-title {
          background: radial-gradient(600px circle at ${x}% ${y}%, rgba(180, 83, 73, 0.35), transparent 40%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }
      `;
      clearTimeout(maskTimeout);
      maskTimeout = setTimeout(() => {
        maskReveal.textContent = "";
      }, 1500);
    });
  }
}
