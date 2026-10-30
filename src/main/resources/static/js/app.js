/* Progressive enhancement: financial calculations and validation remain on the server. */
document.querySelectorAll("form[data-confirm]").forEach((form) => {
  form.addEventListener("submit", (event) => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});
document.querySelectorAll("form").forEach((form) => {
  const kind =
    form.querySelector("[data-kind-select]") ||
    form.querySelector('[name="kind"]');
  const categories = form.querySelector('[name="category_id"]');
  function filterCategories() {
    if (!(categories instanceof HTMLSelectElement) || !kind) return;
    [...categories.options].forEach((option) => {
      if (option.dataset.kind)
        option.hidden = option.dataset.kind !== kind.value;
    });
    if (categories.selectedOptions[0]?.hidden) categories.value = "";
  }
  kind?.addEventListener("change", filterCategories);
  filterCategories();
  const status = form.querySelector("[data-status]");
  function updateDate() {
    const field = form.querySelector("[data-realized]");
    if (field && status) {
      field.hidden = status.value !== "REALIZADO";
      field.querySelector("input").required = status.value === "REALIZADO";
    }
  }
  status?.addEventListener("change", updateDate);
  updateDate();
  form.addEventListener("submit", (event) => {
    if (!navigator.onLine) {
      event.preventDefault();
      document.querySelector(".offline")?.removeAttribute("hidden");
      return;
    }
    if (!event.defaultPrevented) {
      const button = event.submitter;
      if (button) {
        button.textContent = "Salvando…";
        setTimeout(() => {
          button.disabled = true;
        }, 0);
      }
    }
  });
});
document.querySelectorAll(".export-filter").forEach((link) => {
  link.href += window.location.search;
});
document.querySelectorAll(".page-link").forEach((link) => {
  const url = new URL(window.location.href);
  url.searchParams.set("page", link.dataset.page);
  link.href = url.toString();
});
document.querySelector("[data-menu]")?.addEventListener("click", (event) => {
  const button = event.currentTarget,
    menu = document.getElementById("mobile-more");
  menu.hidden = !menu.hidden;
  button.setAttribute("aria-expanded", String(!menu.hidden));
});
document
  .querySelector("[data-back]")
  ?.addEventListener("click", () => window.history.back());
function networkState() {
  const notice = document.querySelector(".offline");
  if (notice) notice.hidden = navigator.onLine;
}
window.addEventListener("offline", networkState);
window.addEventListener("online", networkState);
window.addEventListener("pageshow", () => {
  document.querySelectorAll("button:disabled").forEach((button) => {
    button.disabled = false;
  });
});
networkState();
