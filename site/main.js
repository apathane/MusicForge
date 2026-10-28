(() => {
  const root = document.documentElement;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const themeButton = document.getElementById("theme-toggle");
  const menuButton = document.getElementById("menu-toggle");
  const mobileNav = document.getElementById("mobile-nav");
  const rail = document.getElementById("screenshot-rail");

  // Theme state is intentionally small and resilient if storage is unavailable.
  let savedTheme = null;
  try { savedTheme = localStorage.getItem("musicforge-site-theme"); } catch (_) {}
  if (savedTheme === "light") root.classList.add("theme-light");

  function syncThemeLabel() {
    const light = root.classList.contains("theme-light");
    themeButton.setAttribute("aria-label", light ? "Switch to dark theme" : "Switch to light theme");
    themeButton.setAttribute("title", light ? "Switch to dark theme" : "Switch to light theme");
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", light ? "#efeee8" : "#10110f");
  }
  syncThemeLabel();
  themeButton.addEventListener("click", () => {
    root.classList.toggle("theme-light");
    try { localStorage.setItem("musicforge-site-theme", root.classList.contains("theme-light") ? "light" : "dark"); } catch (_) {}
    syncThemeLabel();
  });

  menuButton.addEventListener("click", () => {
    const open = menuButton.getAttribute("aria-expanded") !== "true";
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
    mobileNav.hidden = !open;
  });
  mobileNav.querySelectorAll("a").forEach((link) => link.addEventListener("click", () => {
    mobileNav.hidden = true;
    menuButton.setAttribute("aria-expanded", "false");
    menuButton.setAttribute("aria-label", "Open navigation");
  }));

  // Recorded measurements from docs/BENCHMARKS.md. Values are MB/s, not a live feed.
  const benchmarks = {
    long: {
      rows: [
        { label: "Single request", note: "Unbounded GET", value: 0.03, max: 5.16, display: "0.03" },
        { label: "Chunked transfer", note: "2 MiB ranges", value: 5.16, max: 5.16, display: "5.16" }
      ],
      delta: "172×",
      caption: "70.1 MB stream · same source and connection. Recorded in the project benchmark notes, not a promise for every network or device."
    },
    short: {
      rows: [
        { label: "Single request", note: "Unbounded GET", value: 0.03, max: 5.50, display: "0.03" },
        { label: "Chunked transfer", note: "2 MiB ranges", value: 5.50, max: 5.50, display: "5.50" }
      ],
      delta: "183×",
      caption: "3.3 MB song · plain unbounded GET compared with 2 MiB range requests in the recorded test. Small samples vary; see the full table for all strategies."
    }
  };
  const chart = document.getElementById("benchmark-chart");
  const delta = document.getElementById("benchmark-delta");
  const caption = document.getElementById("benchmark-caption");
  const tabs = [...document.querySelectorAll("[data-benchmark]")];

  function renderBenchmark(name) {
    const data = benchmarks[name];
    const max = Math.max(...data.rows.map((row) => row.value));
    chart.replaceChildren();
    data.rows.forEach((row, index) => {
      const line = document.createElement("div");
      line.className = "chart-row";
      const label = document.createElement("div");
      label.className = "chart-label";
      const title = document.createElement("span");
      title.textContent = row.label;
      const note = document.createElement("small");
      note.textContent = row.note;
      label.append(title, note);
      const track = document.createElement("div");
      track.className = "chart-track";
      track.setAttribute("role", "img");
      track.setAttribute("aria-label", `${row.label}: ${row.display} megabytes per second`);
      const bar = document.createElement("div");
      bar.className = `chart-bar ${index === 0 ? "chart-bar-muted" : "chart-bar-accent"}`;
      bar.style.setProperty("--bar", `${Math.max(0.58, (row.value / max) * 100)}%`);
      track.append(bar);
      const value = document.createElement("strong");
      value.className = "chart-value";
      value.append(document.createTextNode(row.display + " "));
      const unit = document.createElement("small");
      unit.textContent = "MB/s";
      value.append(unit);
      line.append(label, track, value);
      chart.append(line);
    });
    delta.textContent = data.delta;
    caption.textContent = data.caption;
    tabs.forEach((tab) => {
      const active = tab.dataset.benchmark === name;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-pressed", String(active));
    });
  }
  tabs.forEach((tab) => tab.addEventListener("click", () => renderBenchmark(tab.dataset.benchmark)));

  // Native horizontal scrolling remains available to touch, keyboard and assistive technology.
  document.querySelectorAll("[data-rail-step]").forEach((button) => {
    button.addEventListener("click", () => {
      if (!rail) return;
      const card = rail.querySelector(".screenshot-card");
      if (!card) return;
      const style = getComputedStyle(rail);
      const gap = parseFloat(style.columnGap || style.gap) || 16;
      rail.scrollBy({ left: (card.getBoundingClientRect().width + gap) * Number(button.dataset.railStep), behavior: reducedMotion.matches ? "auto" : "smooth" });
    });
  });

  // Reveal only content that enters view. No animation loop or perpetual background effects.
  const revealTargets = document.querySelectorAll(".principle-card, .feature-story, .benchmark-panel, .pipeline-node, .field-stat, .screenshot-card");
  if ("IntersectionObserver" in window && !reducedMotion.matches) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.08 });
    revealTargets.forEach((target) => {
      target.classList.add("reveal");
      observer.observe(target);
    });
  }

  // Ensure reduced-motion preference changes are respected after page load too.
  reducedMotion.addEventListener?.("change", (event) => {
    if (event.matches) document.querySelectorAll(".reveal").forEach((el) => el.classList.add("is-visible"));
  });
})();
