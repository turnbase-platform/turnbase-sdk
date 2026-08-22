/**
 * Declared types for the frontend GDK, per {@code frontend-gdk-api.md} §5, §6, §9 and §10.
 *
 * IMP-M0-02 ships these declarations only. `TurnBaseClient` itself — the class that reads
 * these types off the wire and exposes them through callbacks — is IMP-M3-12.
 */

/** A client's role for the session (`frontend-gdk-api.md` §5). */
export type Role = "player" | "spectator";

/** A player as seen by the game frontend. Mirrors the backend `Player` (§6). */
export interface Player {
  /** UUID, canonical string form on the postMessage wire. */
  id: string;
  displayName: string;
  /** UUID, canonical string form on the postMessage wire. */
  teamId: string;
}

/** A team and its players, as configured in the lobby. Mirrors the backend `Team` (§6). */
export interface Team {
  /** UUID, canonical string form on the postMessage wire. */
  id: string;
  /** Display name resolved from the manifest team template. */
  name: string;
  players: Player[];
}

/**
 * The context delivered with the platform's `init` event — the only event guaranteed to be
 * first (FR-FE-001). Carries no game state; the first `game_event` after it is always the
 * viewer's full current view, from the backend's `onResync` hook (§4, §5).
 */
export interface InitContext {
  /** This client's role for the session. */
  role: Role;
  /** The current player, or null for spectators (FR-FE-006). */
  self: Player | null;
  /** Full roster: every player with id, display name, team id (FR-FEGDK-004). */
  roster: Player[];
  /** Team structure for the session. */
  teams: Team[];
  /** The session id (also present in the bootstrap; echoed for convenience). */
  sessionId: string;
}

/**
 * The bootstrap global the platform shell injects into the game iframe document before the
 * game bundle's JS executes (§9). Security-critical configuration — the trusted origin — is
 * provided by the trusted shell, never inferred from message traffic and never read from a
 * query parameter or build-time constant.
 */
export interface TurnBaseBootstrap {
  /** The exact origin `TurnBaseClient` must trust. */
  parentOrigin: string;
  /** The active session UUID. */
  sessionId: string;
}

declare global {
  interface Window {
    __TURNBASE__?: TurnBaseBootstrap;
  }
}

/**
 * The inbound postMessage envelope shapes (§10), documented so the platform shell and the
 * local test harness emit the right wire shapes. `TurnBaseClient` abstracts these away from
 * game code entirely (FR-FEGDK-005); a game never sees an `InboundMessage` directly.
 */
export type InboundMessage<TEvent = unknown> =
  | {
      type: "init";
      role: Role;
      self: Player | null;
      roster: Player[];
      teams: Team[];
      sessionId: string;
    }
  | { type: "game_event"; payload: TEvent }
  | { type: "player_joined"; player: Player }
  | { type: "player_left"; player: Player }
  | { type: "game_over"; results: unknown };

/**
 * The outbound postMessage envelope shapes (§10). `ready` is the handshake sent once, on
 * `start()`; `action` is the only outbound message a game can produce (FR-FA-003) and never
 * carries player identity — the platform attaches it server-side (FR-FA-002, NFR-SEC-001).
 */
export type OutboundMessage<TAction = unknown> =
  { type: "ready" } | { type: "action"; payload: TAction };
