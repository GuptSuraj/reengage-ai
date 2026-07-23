export type EventType =
  | "page_viewed"
  | "product_viewed"
  | "search_performed"
  | "filter_applied"
  | "product_compared"
  | "add_to_cart"
  | "remove_from_cart"
  | "checkout_started"
  | "purchase_completed"
  | "time_spent"
  | "session_started"
  | "session_ended";

export interface BehaviourEvent {
  eventId: string;
  userId?: string;
  anonymousId?: string;
  productId?: string;
  eventType: EventType;
  timestamp: string;
  device: {
    type: "mobile" | "tablet" | "desktop";
    userAgent: string;
    locale: string;
  };
  sourcePage: string;
  sessionId: string;
  metadata: Record<string, unknown>;
}

export interface TrackerOptions {
  endpoint: string;
  userId?: string;
  anonymousId?: string;
  batchSize?: number;
  flushIntervalMs?: number;
  enrich?: () => Record<string, unknown>;
}

export class ReEngageTracker {
  private readonly options: Required<Pick<TrackerOptions, "batchSize" | "flushIntervalMs">> & TrackerOptions;
  private readonly sessionId = crypto.randomUUID();
  private queue: BehaviourEvent[] = [];
  private timer?: number;
  private pageEnteredAt = performance.now();
  private currentProductId?: string;

  constructor(options: TrackerOptions) {
    this.options = { batchSize: 10, flushIntervalMs: 5000, ...options };
  }

  start(): this {
    this.track("session_started");
    this.timer = window.setInterval(() => void this.flush(), this.options.flushIntervalMs);
    document.addEventListener("visibilitychange", this.onVisibilityChange);
    window.addEventListener("pagehide", this.onPageHide);
    return this;
  }

  identify(userId: string): void {
    this.options.userId = userId;
  }

  page(productId?: string): void {
    this.captureTimeSpent();
    this.pageEnteredAt = performance.now();
    this.currentProductId = productId;
    this.track(productId ? "product_viewed" : "page_viewed", { productId });
  }

  track(
    eventType: EventType,
    input: { productId?: string; metadata?: Record<string, unknown> } = {},
  ): void {
    this.queue.push({
      eventId: crypto.randomUUID(),
      userId: this.options.userId,
      anonymousId: this.options.anonymousId,
      sessionId: this.sessionId,
      eventType,
      productId: input.productId,
      timestamp: new Date().toISOString(),
      sourcePage: location.href,
      device: {
        type: matchMedia("(max-width: 640px)").matches
          ? "mobile"
          : matchMedia("(max-width: 1024px)").matches ? "tablet" : "desktop",
        userAgent: navigator.userAgent,
        locale: navigator.language,
      },
      metadata: { ...this.options.enrich?.(), ...input.metadata },
    });
    if (this.queue.length >= this.options.batchSize) void this.flush();
  }

  async flush(keepalive = false): Promise<void> {
    if (!this.queue.length) return;
    const events = this.queue.splice(0, this.options.batchSize);
    try {
      const response = await fetch(this.options.endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ events }),
        keepalive,
      });
      if (!response.ok) throw new Error(`Ingestion failed with ${response.status}`);
    } catch {
      // Preserve order and retry on the next flush. The backend deduplicates by eventId.
      this.queue.unshift(...events);
    }
    if (this.queue.length) queueMicrotask(() => void this.flush(keepalive));
  }

  destroy(): void {
    this.captureTimeSpent();
    this.track("session_ended");
    if (this.timer) clearInterval(this.timer);
    document.removeEventListener("visibilitychange", this.onVisibilityChange);
    window.removeEventListener("pagehide", this.onPageHide);
    void this.flush(true);
  }

  private captureTimeSpent(): void {
    const seconds = Math.round((performance.now() - this.pageEnteredAt) / 1000);
    if (seconds >= 2) this.track("time_spent", {
      productId: this.currentProductId,
      metadata: { seconds },
    });
  }

  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState === "hidden") {
      this.captureTimeSpent();
      void this.flush(true);
    } else {
      this.pageEnteredAt = performance.now();
    }
  };

  private readonly onPageHide = (): void => {
    this.track("session_ended");
    void this.flush(true);
  };
}

export const createTracker = (options: TrackerOptions): ReEngageTracker =>
  new ReEngageTracker(options).start();
