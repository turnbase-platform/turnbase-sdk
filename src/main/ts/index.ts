/**
 * `@turnbase/gdk` — the public frontend Game Development Kit.
 *
 * IMP-M0-02 ships the declared types only (`frontend-gdk-api.md` §5, §6, §9, §10).
 * `TurnBaseClient` — the class a game frontend actually instantiates — is IMP-M3-12.
 */
export type {
  Role,
  Player,
  Team,
  InitContext,
  TurnBaseBootstrap,
  InboundMessage,
  OutboundMessage,
} from "./types.js";
