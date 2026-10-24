// The screenshot row is CSS scroll-snap; these two buttons only move it one card.
// Scrolling itself stays native, so it still works with JS off and with a keyboard.
const rail = document.querySelector("[data-rail]");

document.querySelectorAll("[data-step]").forEach((button) => {
  button.addEventListener("click", () => {
    const card = rail && rail.querySelector(".slide");
    if (!card) return;

    const gap = parseFloat(getComputedStyle(rail).columnGap) || 16;
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

    rail.scrollBy({
      left: (card.offsetWidth + gap) * Number(button.dataset.step),
      behavior: reduced ? "auto" : "smooth",
    });
  });
});
