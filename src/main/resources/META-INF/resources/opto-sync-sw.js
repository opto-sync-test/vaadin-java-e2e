const DATABASE = "vaadin-opto-sync";
const STORE = "pending";

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1);
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore(STORE, { keyPath: "id" });
      store.createIndex("lane", "lane");
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function useStore(mode, operation) {
  const database = await openDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const request = operation(database.transaction(STORE, mode).objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally {
    database.close();
  }
}

async function enqueue(mutation) {
  const immutable = Object.freeze({
    ...mutation,
    id: mutation.id ?? `${mutation.lane}:${mutation.sequence}`,
  });
  await useStore("readwrite", (store) => store.put(immutable));
  if (self.registration.sync) {
    await self.registration.sync.register("vaadin-opto-sync-drain");
  }
}

async function drainLane(items) {
  for (const item of items.sort((left, right) => left.sequence - right.sequence)) {
    const response = await fetch(`/api/sync/${encodeURIComponent(item.lane)}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(item),
    });
    if (!response.ok) {
      throw new Error(`Vaadin OptoSync lane ${item.lane} failed with ${response.status}`);
    }
    const authoritative = await response.json();
    await useStore("readwrite", (store) => store.delete(item.id));
    const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    windows.forEach((client) => client.postMessage({ type: "authoritative", authoritative }));
  }
}

async function drain() {
  const pending = await useStore("readonly", (store) => store.getAll());
  const lanes = Map.groupBy(pending, (item) => item.lane);
  await Promise.all([...lanes.values()].map(drainLane));
}

self.addEventListener("install", (event) => event.waitUntil(self.skipWaiting()));
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));
self.addEventListener("message", (event) => {
  if (event.data?.type === "enqueue") {
    event.waitUntil(enqueue(event.data.mutation).then(drain));
  } else if (event.data?.type === "drain") {
    event.waitUntil(drain());
  }
});
self.addEventListener("sync", (event) => {
  if (event.tag === "vaadin-opto-sync-drain") {
    event.waitUntil(drain());
  }
});
