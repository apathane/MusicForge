(() => {
  const doc = document.documentElement;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const dock = document.querySelector(".dock");
  const windows = document.querySelector(".windows");
  const dockIcons = document.querySelectorAll(".dock-icon");

  // Track state
  let zIndexCounter = 1000;
  let focusedWindow = null;
  let activeWindow = null;
  let minimizedWindows = new Map(); // app key -> window element
  let trackList = [];
  let currentTrackIndex = -1;

  // Track data for player preview
  const defaultTracks = [
    { title: "Night Drive", artist: "MusicForge", duration: "3:42" },
    { title: "River Flow", artist: "Indie Collective", duration: "4:15" },
    { title: "City Lights", artist: "Ambient Suite", duration: "2:58" },
  ];

  // Initialise dock icon app keys
  const appKeys = {
    player: "player",
    library: "library",
    lyrics: "lyrics",
    about: "about",
  };

  // Create a window element
  function createWindow(app) {
    // Check if already open
    const existing = document.querySelector(`.window[data-app="${app}"]`);
    if (existing) {
      focusWindow(existing);
      return;
    }

    const win = document.createElement("section");
    win.className = "window";
    win.setAttribute("role", "dialog");
    win.setAttribute("aria-modal", "true");
    win.setAttribute("data-app", app);
    win.style.zIndex = ++zIndexCounter;
    win.hidden = false;

    // Set title and content based on app
    let title, body;
    switch (app) {
      case "player":
        title = "Now playing";
        body = playerWindowHtml();
        break;
      case "library":
        title = "Library";
        body = libraryWindowHtml();
        break;
      case "lyrics":
        title = "Lyrics";
        body = lyricsWindowHtml();
        break;
      case "about":
        title = "About";
        body = aboutWindowHtml();
        break;
      default:
        title = "Window";
        body = "";
    }

    win.innerHTML = `
      <div class="window-header" role="region" aria-label="Now playing">
        <div class="window-title" id="window-title-${app}">${title}</div>
        <div class="window-controls">
          <button class="window-control" data-action="minimize" aria-label="Minimize">_</button>
          <button class="window-control" data-action="close" aria-label="Close">×</button>
        </div>
      </div>
      <div class="window-body">${body}</div>
    `;

    // Append to windows container
    windows.appendChild(win);

    // Set up title element reference
    const titleEl = win.querySelector(".window-title");
    win.setAttribute("aria-labelledby", `window-title-${app}`);

    // Position window near dock if not first
    positionWindow(win, app);

    // Make draggable
    makeDraggable(win);

    // Click brings to front
    win.addEventListener("click", (e) => {
      if (e.target.closest(".window-controls")) return;
      focusWindow(win);
    });

    // Store reference
    minimizedWindows.set(app, win);

    // Focus window
    focusWindow(win);
    activeWindow = win;
    return win;
  }

  // Player window HTML
  function playerWindowHtml() {
    // Build track list
    let tracksHtml = "";
    trackList.forEach((track, i) => {
      const isCurrent = i === currentTrackIndex;
      tracksHtml += `
        <div class="queue-item${isCurrent ? " is-current" : ""}" data-index="${i}" role="button" tabindex="0" aria-label="Play ${track.title}" onfocus="this.select()">
          <span class="queue-track">${i + 1}</span>
          <span class="queue-title">${track.title}</span>
          <span class="queue-artist">${track.artist}</span>
        </div>
      `;
    });
    if (trackList.length === 0) {
      tracksHtml = `<div class="empty-state">No tracks in queue. Press play to start.</div>`;
    }

    return `
      <div class="now-playing">
        <div class="track-artist" id="now-playing-title">${defaultTracks[0]?.title || "—"}</div>
        <div class="track-artist" id="now-playing-artist">${defaultTracks[0]?.artist || "—"}</div>
      </div>
      <div class="progress-container">
        <div class="progress-bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0" style="width: 0%;"></div>
      </div>
      <div class="controls">
        <button class="control-btn" data-action="prev" aria-label="Previous track">‹</button>
        <button class="control-btn" data-action="play" aria-label="Play/pause">▐▌</button>
        <button class="control-btn" data-action="next" aria-label="Next track">›</button>
      </div>
      <div class="queue-list" id="queue-list">${tracksHtml}</div>
    `;
  }

  // Library window HTML (already in HTML, but kept for completeness)
  function libraryWindowHtml() {
    return `
      <div class="library-tabs" role="tablist">
        <button class="library-tab is-active" data-tab="songs" role="tab" aria-controls="songs-section" aria-selected="true">Songs</button>
        <button class="library-tab" data-tab="albums" role="tab" aria-controls="albums-section" aria-selected="false">Albums</button>
        <button class="library-tab" data-tab="artists" role="tab" aria-controls="artists-section" aria-selected="false">Artists</button>
        <button class="library-tab" data-tab="downloads" role="tab" aria-controls="downloads-section" aria-selected="false">Downloads</button>
      </div>
      <div class="library-content">
        <div id="songs-section" class="library-section is-active"><h3>Songs</h3><p class="empty-state">No songs in library.</p></div>
        <div id="albums-section" class="library-section"><h3>Albums</h3><p class="empty-state">No albums in library.</p></div>
        <div id="artists-section" class="library-section"><h3>Artists</h3><p class="empty-state">No artists in library.</p></div>
        <div id="downloads-section" class="library-section"><h3>Downloads</h3><p class="empty-state">Nothing downloaded yet. Press play somewhere and it'll fill up.</p></div>
      </div>
    `;
  }

  // Lyrics window HTML
  function lyricsWindowHtml() {
    const lines = [
      "I see you drifting, golden in the light",
      "Floating on a river of quiet nights",
      "And I wonder, do you see me too?",
      "The stars are aligning, one by one",
      "A melody that lingers when the day is done",
    ];
    let linesHtml = "";
    lines.forEach((line, i) => {
      linesHtml += `<div class="lyrics-line" data-line-index="${i}">${line}</div>`;
    });
    return `<div class="lyrics-content">${linesHtml}</div>`;
  }

  // About window HTML
  function aboutWindowHtml() {
    return `
      <p><a href="https://github.com/apathane/MusicForge" target="_blank" rel="noreferrer">GitHub repository</a></p>
      <p>GPL-3.0 licence</p>
      <p>Credits: Metrolist, ConvX, BitChord</p>
    `;
  }

  // Position window near dock
  function positionWindow(win, app) {
    const dockRect = dock.getBoundingClientRect();
    let left, top;

    // Simple positioning: spread windows out
    const count = minimizedWindows.size;
    const index = Array.from(minimizedWindows.keys()).indexOf(app);

    if (count === 0) {
      // First window - position near right of dock
      left = dockRect.right - 280;
      top = dockRect.top - 320;
    } else {
      // Subsequent windows - stack vertically
      const spacing = 30;
      left = dockRect.right - 280;
      top = dockRect.top - (320 + index * (200 + spacing));
    }

    win.style.left = `${Math.max(20, Math.min(window.innerWidth - 300, left))}px`;
    win.style.top = `${Math.max(20, Math.min(window.innerHeight - 400, top))}px`;
  }

  // Make window draggable by title bar
  function makeDraggable(win) {
    const handle = win.querySelector(".window-header");
    if (!handle) return;

    let isDragging = false;
    let offsetX, offsetY;

    function handleMouseDown(e) {
      // Don't drag if clicking controls
      if (e.target.closest(".window-controls")) return;

      isDragging = true;
      const rect = win.getBoundingClientRect();
      offsetX = e.clientX - rect.left;
      offsetY = e.clientY - rect.top;

      // Bring to front
      win.style.zIndex = ++zIndexCounter;
      focusedWindow = win;
      activeWindow = win;

      // Remove any previous drag styles
      win.style.transition = "none";

      document.addEventListener("mousemove", handleMouseMove, { passive: true });
      document.addEventListener("mouseup", handleMouseUp, { once: true });
    }

    function handleMouseMove(e) {
      if (!isDragging) return;
      const x = e.clientX - offsetX;
      const y = e.clientY - offsetY;
      win.style.left = `${x}px`;
      win.style.top = `${y}px`;
    }

    function handleMouseUp() {
      isDragging = false;
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    }

    handle.addEventListener("mousedown", handleMouseDown);
    handle.addEventListener("touchstart", (e) => {
      e.preventDefault();
      handleMouseDown(e.touches[0]);
    }, { passive: false });

    // On mobile, clicking title bar also focuses
    handle.addEventListener("click", () => {
      focusWindow(win);
    });
  }

  // Focus window (bring to front, set as focused)
  function focusWindow(win) {
    // Remove active class from all windows
    document.querySelectorAll(".window.is-active").forEach((w) => w.classList.remove("is-active"));

    // Add active class to this window
    win.classList.add("is-active");

    // If there was a previously focused window, restore focus to dock
    if (focusedWindow && focusedWindow !== win) {
      const app = focusedWindow.dataset.app;
      const icon = document.querySelector(`.dock-icon[data-app="${app}"]`);
      if (icon) {
        icon.focus();
      }
    }

    focusedWindow = win;
  }

  // Minimize window to dock
  function minimizeWindow(win) {
    const app = win.dataset.app;
    win.hidden = true;
    minimizedWindows.set(app, win);
    const icon = document.querySelector(`.dock-icon[data-app="${app}"]`);
    if (icon) {
      icon.classList.add("minimized");
    }
    // Restore focus to dock
    if (focusedWindow === win) {
      focusedWindow = null;
      const firstIcon = dockIcons[0];
      if (firstIcon) firstIcon.focus();
    }
  }

  // Close window
  function closeWindow(win) {
    const app = win.dataset.app;
    minimizedWindows.delete(app);
    win.remove();
    // Update dock icon
    const icon = document.querySelector(`.dock-icon[data-app="${app}"]`);
    if (icon) {
      icon.classList.remove("minimized");
    }
    // If closing focused window, restore focus to dock
    if (focusedWindow === win) {
      focusedWindow = null;
      const firstIcon = dockIcons[0];
      if (firstIcon) firstIcon.focus();
    }
    // If this was the active window and there are others, focus next
    if (activeWindow === win) {
      activeWindow = document.querySelector(".window.is-active") || null;
    }
  }

  // Player control handlers
  function setupPlayerWindow(win) {
    const progressBar = win.querySelector(".progress-bar");
    const nowPlayingTitle = win.querySelector("#now-playing-title");
    const nowPlayingArtist = win.querySelector("#now-playing-artist");
    const playBtn = win.querySelector('[data-action="play"]');
    const prevBtn = win.querySelector('[data-action="prev"]');
    const nextBtn = win.querySelector('[data-action="next"]');
    const queueItems = win.querySelectorAll(".queue-item");

    // Track state
    let isPlaying = false;
    let progressInterval = null;

    // Update progress bar
    function updateProgress() {
      if (reducedMotion.matches) {
        progressBar.style.width = "0%";
        return;
      }
      if (isPlaying && currentTrackIndex >= 0) {
        // Simulate progress: 0% → 100% over ~4 minutes
        const elapsed = Date.now() - (win._lastProgress || Date.now());
        const pct = Math.min(100, (elapsed / 240000) * 100);
        progressBar.style.width = `${pct}%`;
        win._lastProgress = Date.now();
      }
    }

    // Play/pause
    playBtn.addEventListener("click", () => {
      isPlaying = !isPlaying;
      playBtn.textContent = isPlaying ? "■" : "▐▌";
      if (isPlaying) {
        win._lastProgress = Date.now();
        progressInterval = setInterval(updateProgress, 100);
      } else {
        clearInterval(progressInterval);
      }
    });

    // Previous track
    prevBtn.addEventListener("click", () => {
      if (currentTrackIndex <= 0) {
        currentTrackIndex = trackList.length - 1;
      } else {
        currentTrackIndex--;
      }
      updateNowPlaying();
      updateQueueSelection();
    });

    // Next track
    nextBtn.addEventListener("click", () => {
      if (currentTrackIndex >= trackList.length - 1) {
        currentTrackIndex = 0;
      } else {
        currentTrackIndex++;
      }
      updateNowPlaying();
      updateQueueSelection();
    });

    // Update now displaying track
    function updateNowPlaying() {
      if (trackList.length === 0) return;
      const track = trackList[currentTrackIndex];
      nowPlayingTitle.textContent = track.title;
      nowPlayingArtist.textContent = track.artist;
      progressBar.style.width = "0%";
    }

    // Update queue selection styling
    function updateQueueSelection() {
      queueItems.forEach((item, i) => {
        item.classList.toggle("is-current", i === currentTrackIndex);
      });
    }

    // Queue item click
    queueItems.forEach((item) => {
      item.addEventListener("click", () => {
        currentTrackIndex = parseInt(item.dataset.index, 10);
        updateNowPlaying();
        updateQueueSelection();
      });
    });

    // Initialize with first track
    if (trackList.length === 0) {
      trackList = [...defaultTracks];
      currentTrackIndex = 0;
      updateNowPlaying();
      updateQueueSelection();
    }

    // Keyboard support
    win.addEventListener("keydown", (e) => {
      if (e.key === " ") {
        e.preventDefault();
        playBtn.click();
      }
      if (e.key === "ArrowLeft") prevBtn.click();
      if (e.key === "ArrowRight") nextBtn.click();
      if (e.key === "Escape" && focusedWindow === win) closeWindow(win);
    });
  }

  // Library tab handlers
  function setupLibraryWindow(win) {
    const tabs = win.querySelectorAll(".library-tab");
    const sections = win.querySelectorAll(".library-section");

    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        const tabName = tab.dataset.tab;

        // Update tab state
        tabs.forEach((t) => t.classList.toggle("is-active", t === tab));
        tabs.forEach((t) => t.setAttribute("aria-selected", t === tab));

        // Update section state
        sections.forEach((section) => {
          section.classList.toggle("is-active", section.id === `${tabName}-section`);
        });

        // Update empty state visibility for downloads
        const downloadsSection = document.getElementById("downloads-section");
        const downloadsEmpty = downloadsSection ? downloadsSection.querySelector(".empty-state") : null;
        if (downloadsSection && tabName === "downloads") {
          if (trackList.length === 0 && !downloadsSection.classList.contains("is-active")) {
            // Already handled by tab switch
          }
        }
      });
    });
  }

  // Lyrics window handlers
  function setupLyricsWindow(win) {
    const lines = win.querySelectorAll(".lyrics-line");
    let currentLine = 0;

    // Simple highlight cycle (no actual playback, just preview)
    function highlightLine(index) {
      lines.forEach((line, i) => {
        line.classList.toggle("highlight", i === index);
      });
      currentLine = index;
    }

    // Cycle through lines every 2s (preview only)
    if (lines.length > 0) {
      setInterval(() => {
        if (reducedMotion.matches) return;
        const next = (currentLine + 1) % lines.length;
        highlightLine(next);
      }, 2000);
    }

    // Keyboard support
    win.addEventListener("keydown", (e) => {
      if (e.key === "Escape") closeWindow(win);
    });
  }

  // About window handlers
  function setupAboutWindow(win) {
    // No specific interactivity needed
    win.addEventListener("keydown", (e) => {
      if (e.key === "Escape") closeWindow(win);
    });
  }

  // Theme toggle (existing, kept lightweight)
  function setupThemeToggle() {
    const themeButton = document.getElementById("theme-toggle");
    if (!themeButton) return;

    // Check for saved theme
    let savedTheme = null;
    try { savedTheme = localStorage.getItem("musicforge-site-theme"); } catch (_) {}
    if (savedTheme === "light") doc.classList.add("theme-light");

    function syncThemeLabel() {
      const light = doc.classList.contains("theme-light");
      themeButton.setAttribute("aria-label", light ? "Switch to dark theme" : "Switch to light theme");
      themeButton.setAttribute("title", light ? "Switch to dark theme" : "Switch to light theme");
    }
    syncThemeLabel();

    themeButton.addEventListener("click", () => {
      doc.classList.toggle("theme-light");
      try { localStorage.setItem("musicforge-site-theme", doc.classList.contains("theme-light") ? "light" : "dark"); } catch (_) {}
      syncThemeLabel();
    });
  }

// Mobile: windows become full-screen sheets
  function setupMobile() {
    if (window.innerWidth > 720) return;

    const windowHandles = document.querySelectorAll(".window .window-header");
    windowHandles.forEach(handle => {
      handle.style.cursor = "grab";
      handle.style.userSelect = "none";
    });

    // Fix gutter on mobile
    const sheetStyle = document.createElement("style");
    sheetStyle.textContent = `
      .dock { padding-left: 16px; padding-right: 16px; }
      .dock-icons { gap: 12px; }
    `;
    document.head.append(sheetStyle);
  }

// Initialise everything
  function init() {
    setupThemeToggle();
    setupMobile();

    // Dock icon click handlers
    dockIcons.forEach((icon) => {
      icon.addEventListener("click", () => {
        const app = icon.dataset.app;
        if (minimizedWindows.has(app)) {
          // Restore minimized window
          const win = minimizedWindows.get(app);
          win.hidden = false;
          minimizedWindows.delete(app);
          icon.classList.remove("minimized");
          focusWindow(win);
        } else {
          // Create new window
          createWindow(app);
        }
      });
    });

    // Window control handlers
    windows.addEventListener("click", (e) => {
      const control = e.target.closest(".window-control");
      if (!control) return;

      const win = control.closest(".window");
      const action = control.dataset.action;

      switch (action) {
        case "minimize":
          minimizeWindow(win);
          break;
        case "close":
          closeWindow(win);
          break;
      }
    });

    // Escape key closes focused window
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        if (focusedWindow) {
          closeWindow(focusedWindow);
          focusedWindow = null;
        }
      }
    });

    // Setup player window if it's the focused one
    // (will be set up when player window is created/focused)

    // Reduced motion media query
    reducedMotion.addEventListener("change", (e) => {
      // Update drag easing and progress animation
      const draggable = document.querySelector(".window.is-active");
      if (draggable) {
        draggable.style.transition = e.matches ? "none" : "";
      }
    });
  }

  init();
})();