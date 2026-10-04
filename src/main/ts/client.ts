import type { InboundMessage, InitContext, OutboundMessage, Player } from "./types.js";

/**
 * Options for {@link TurnBaseClient}. See `frontend-gdk-api.md` §3, §9.
 */
export interface TurnBaseClientOptions {
  /**
   * Override the trusted parent origin. Normally omitted — the client reads it from the shell
   * bootstrap (`window.__TURNBASE__`, §9). Intended for the local test harness; SHOULD NOT be
   * set by production game code.
   */
  parentOrigin?: string;
}

/**
 * Client for a TurnBase game frontend. Instantiate once, register callbacks, then call
 * {@link TurnBaseClient.start}. All platform communication is funneled through this object;
 * games never touch `postMessage` directly (FR-FEGDK-001/005, `frontend-gdk-api.md` §3).
 */
export class TurnBaseClient<TAction = unknown, TEvent = unknown> {
  private readonly parentOriginOverride: string | undefined;
  private parentOrigin: string | null = null;
  private started = false;
  private disposed = false;
  private initReceived = false;

  private readonly onMessage = (event: MessageEvent): void => {
    this.handleMessage(event);
  };

  private initCallback: ((ctx: InitContext) => void) | null = null;
  private gameEventCallback: ((event: TEvent) => void) | null = null;
  private playerJoinedCallback: ((player: Player) => void) | null = null;
  private playerLeftCallback: ((player: Player) => void) | null = null;
  private gameOverCallback: ((results: unknown) => void) | null = null;

  constructor(options?: TurnBaseClientOptions) {
    this.parentOriginOverride = options?.parentOrigin;
  }

  /** Register the `init` handler (§4, §5). Replaces any prior handler. */
  onInit(cb: (ctx: InitContext) => void): this {
    this.initCallback = cb;
    return this;
  }

  /** Register the `game_event` handler (§10). Replaces any prior handler. */
  onGameEvent(cb: (event: TEvent) => void): this {
    this.gameEventCallback = cb;
    return this;
  }

  /** Register the `player_joined` handler (§10). Replaces any prior handler. */
  onPlayerJoined(cb: (player: Player) => void): this {
    this.playerJoinedCallback = cb;
    return this;
  }

  /** Register the `player_left` handler (§10). Replaces any prior handler. */
  onPlayerLeft(cb: (player: Player) => void): this {
    this.playerLeftCallback = cb;
    return this;
  }

  /** Register the `game_over` handler (§10). Replaces any prior handler. */
  onGameOver(cb: (results: unknown) => void): this {
    this.gameOverCallback = cb;
    return this;
  }

  /**
   * Begin listening and announce readiness to the platform (§4). Safe to call once; subsequent
   * calls are no-ops. Throws synchronously when `window.__TURNBASE__` is missing and no
   * `parentOrigin` override was supplied — the game cannot run untrusted.
   */
  start(): void {
    if (this.started) {
      return;
    }

    const parentOrigin = this.parentOriginOverride ?? window.__TURNBASE__?.parentOrigin;
    if (!parentOrigin) {
      throw new Error(
        "TurnBaseClient.start(): window.__TURNBASE__ is missing or malformed and no " +
          "parentOrigin override was supplied",
      );
    }

    this.started = true;
    this.parentOrigin = parentOrigin;
    window.addEventListener("message", this.onMessage);
    this.post({ type: "ready" });
  }

  /**
   * Send a player action to the platform (FR-FEGDK-003, FR-FA-001). Valid only after the `init`
   * event has been delivered.
   */
  sendAction(payload: TAction): void {
    if (!this.initReceived) {
      throw new Error("TurnBaseClient.sendAction(): called before the init event was delivered");
    }
    this.post({ type: "action", payload });
  }

  /** Remove the message listener and stop the client. Idempotent. */
  dispose(): void {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    window.removeEventListener("message", this.onMessage);
  }

  private post(message: OutboundMessage<TAction>): void {
    // A detached frame (Return to lobby, a remount) has no parent: `window.parent` is then the
    // game's own window, and posting there only makes the browser warn about the origin mismatch.
    if (!this.parentOrigin || window.parent === window) {
      return;
    }
    // Never '*' (§8): every outbound post is addressed to the trusted parent origin only.
    window.parent.postMessage(message, this.parentOrigin);
  }

  private handleMessage(event: MessageEvent): void {
    // Both checks required (§8, NFR-SEC-004); the trusted origin never comes from the message
    // itself. Failing either drops the message silently — never delivered to a game callback.
    if (event.origin !== this.parentOrigin || event.source !== window.parent) {
      return;
    }

    const data = event.data as InboundMessage<TEvent> | null | undefined;
    if (!data || typeof data !== "object" || typeof data.type !== "string") {
      return;
    }

    switch (data.type) {
      case "init":
        this.initReceived = true;
        this.initCallback?.({
          role: data.role,
          self: data.self,
          roster: data.roster,
          teams: data.teams,
          sessionId: data.sessionId,
        });
        return;
      case "game_event":
        this.gameEventCallback?.(data.payload);
        return;
      case "player_joined":
        this.playerJoinedCallback?.(data.player);
        return;
      case "player_left":
        this.playerLeftCallback?.(data.player);
        return;
      case "game_over":
        this.gameOverCallback?.(data.results);
        return;
      default:
        // Malformed or unrecognized shapes from a valid origin are ignored (§8).
        return;
    }
  }
}
