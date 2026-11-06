if ('serviceWorker' in navigator && window.isSecureContext) {
  navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => {
    // Navigation remains available when service workers are unavailable.
  });
}
// Revalidate pages restored by browser history, including after logout.
window.addEventListener('pageshow', event => {
  if (event.persisted) window.location.reload();
});
