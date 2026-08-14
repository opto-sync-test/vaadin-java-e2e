if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/opto-sync-sw.js");
  window.addEventListener("online", () => {
    navigator.serviceWorker.controller?.postMessage({ type: "drain" });
  });
}
