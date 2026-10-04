import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TurnBaseClient } from "../../main/ts/client.js";
import type { InboundMessage, Player, Team } from "../../main/ts/types.js";

const PARENT_ORIGIN = "https://platform.example.com";

const player: Player = { id: "p1", displayName: "Ada", teamId: "t1" };
const teams: Team[] = [{ id: "t1", name: "Red", players: [player] }];

function dispatchFromParent(message: InboundMessage, origin = PARENT_ORIGIN): void {
  window.dispatchEvent(
    new MessageEvent("message", { data: message, origin, source: window.parent }),
  );
}

const realParent = Object.getOwnPropertyDescriptor(window, "parent");

/** jsdom is a top-level window (`window.parent === window`); a framed game has a distinct parent. */
function frameUnderParent(): void {
  Object.defineProperty(window, "parent", {
    configurable: true,
    value: { postMessage: () => undefined },
  });
}

beforeEach(() => {
  delete window.__TURNBASE__;
  frameUnderParent();
});

afterEach(() => {
  if (realParent) Object.defineProperty(window, "parent", realParent);
  vi.restoreAllMocks();
});

describe("TurnBaseClient bootstrap and lifecycle (frontend-gdk-api.md §4, §9)", () => {
  it("throws synchronously from start() when the bootstrap is missing and no override was given", () => {
    const client = new TurnBaseClient();
    expect(() => {
      client.start();
    }).toThrow();
  });

  it("does not throw when a parentOrigin override is supplied without a bootstrap", () => {
    const client = new TurnBaseClient({ parentOrigin: PARENT_ORIGIN });
    expect(() => {
      client.start();
    }).not.toThrow();
    client.dispose();
  });

  it("reads parentOrigin from window.__TURNBASE__ and posts { type: 'ready' } to window.parent", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const postMessage = vi.spyOn(window.parent, "postMessage");
    const client = new TurnBaseClient();

    client.start();

    expect(postMessage).toHaveBeenCalledWith({ type: "ready" }, PARENT_ORIGIN);
    client.dispose();
  });

  it("does not post when the frame has no parent (window.parent === window)", () => {
    if (realParent) Object.defineProperty(window, "parent", realParent);
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const postMessage = vi.spyOn(window, "postMessage");
    const client = new TurnBaseClient();

    client.start();

    expect(postMessage).not.toHaveBeenCalled();
    client.dispose();
  });

  it("never posts with targetOrigin '*'", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const postMessage = vi.spyOn(window.parent, "postMessage");
    const client = new TurnBaseClient();

    client.start();
    dispatchFromParent({
      type: "init",
      role: "player",
      self: player,
      roster: [player],
      teams,
      sessionId: "s1",
    });
    client.sendAction({ value: 1 });

    for (const call of postMessage.mock.calls) {
      expect(call[1]).not.toBe("*");
    }
    client.dispose();
  });

  it("start() is safe to call more than once — subsequent calls are no-ops", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const postMessage = vi.spyOn(window.parent, "postMessage");
    const client = new TurnBaseClient();

    client.start();
    client.start();
    client.start();

    expect(postMessage).toHaveBeenCalledTimes(1);
    client.dispose();
  });
});

describe("TurnBaseClient callbacks (frontend-gdk-api.md §3, §5, §10)", () => {
  it("registration methods return `this` for chaining", () => {
    const client = new TurnBaseClient();
    const noop = vi.fn();
    expect(client.onInit(noop)).toBe(client);
    expect(client.onGameEvent(noop)).toBe(client);
    expect(client.onPlayerJoined(noop)).toBe(client);
    expect(client.onPlayerLeft(noop)).toBe(client);
    expect(client.onGameOver(noop)).toBe(client);
  });

  it("delivers init, game_event, player_joined, player_left and game_over to their callbacks", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient<{ value: number }, { hint: string }>();
    const onInit = vi.fn();
    const onGameEvent = vi.fn();
    const onPlayerJoined = vi.fn();
    const onPlayerLeft = vi.fn();
    const onGameOver = vi.fn();

    client
      .onInit(onInit)
      .onGameEvent(onGameEvent)
      .onPlayerJoined(onPlayerJoined)
      .onPlayerLeft(onPlayerLeft)
      .onGameOver(onGameOver)
      .start();

    dispatchFromParent({
      type: "init",
      role: "player",
      self: player,
      roster: [player],
      teams,
      sessionId: "s1",
    });
    dispatchFromParent({ type: "game_event", payload: { hint: "warmer" } });
    dispatchFromParent({ type: "player_joined", player });
    dispatchFromParent({ type: "player_left", player });
    dispatchFromParent({ type: "game_over", results: { winner: player.id } });

    expect(onInit).toHaveBeenCalledWith({
      role: "player",
      self: player,
      roster: [player],
      teams,
      sessionId: "s1",
    });
    expect(onGameEvent).toHaveBeenCalledWith({ hint: "warmer" });
    expect(onPlayerJoined).toHaveBeenCalledWith(player);
    expect(onPlayerLeft).toHaveBeenCalledWith(player);
    expect(onGameOver).toHaveBeenCalledWith({ winner: player.id });
    client.dispose();
  });
});

describe("TurnBaseClient origin validation (frontend-gdk-api.md §8, NFR-SEC-004)", () => {
  it("drops a message whose origin does not match parentOrigin", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient();
    const onInit = vi.fn();
    client.onInit(onInit).start();

    dispatchFromParent(
      { type: "init", role: "player", self: player, roster: [player], teams, sessionId: "s1" },
      "https://evil.example.com",
    );

    expect(onInit).not.toHaveBeenCalled();
    client.dispose();
  });

  it("drops a message whose source is not window.parent", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient();
    const onInit = vi.fn();
    client.onInit(onInit).start();

    window.dispatchEvent(
      new MessageEvent("message", {
        data: {
          type: "init",
          role: "player",
          self: player,
          roster: [player],
          teams,
          sessionId: "s1",
        },
        origin: PARENT_ORIGIN,
        source: null,
      }),
    );

    expect(onInit).not.toHaveBeenCalled();
    client.dispose();
  });
});

describe("TurnBaseClient actions (frontend-gdk-api.md §7, FR-FA-001…003)", () => {
  it("throws when sendAction is called before init", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient<{ value: number }>();
    client.start();

    expect(() => {
      client.sendAction({ value: 1 });
    }).toThrow();
    client.dispose();
  });

  it("wraps sendAction payload as { type: 'action', payload } with no player identity, posted to parentOrigin", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const postMessage = vi.spyOn(window.parent, "postMessage");
    const client = new TurnBaseClient<{ value: number }>();
    client.start();
    dispatchFromParent({
      type: "init",
      role: "player",
      self: player,
      roster: [player],
      teams,
      sessionId: "s1",
    });

    client.sendAction({ value: 42 });

    expect(postMessage).toHaveBeenCalledWith(
      { type: "action", payload: { value: 42 } },
      PARENT_ORIGIN,
    );
    client.dispose();
  });
});

describe("TurnBaseClient dispose (frontend-gdk-api.md §3)", () => {
  it("stops delivering messages after dispose", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient();
    const onInit = vi.fn();
    client.onInit(onInit).start();

    client.dispose();
    dispatchFromParent({
      type: "init",
      role: "player",
      self: player,
      roster: [player],
      teams,
      sessionId: "s1",
    });

    expect(onInit).not.toHaveBeenCalled();
  });

  it("is idempotent", () => {
    window.__TURNBASE__ = { parentOrigin: PARENT_ORIGIN, sessionId: "s1" };
    const client = new TurnBaseClient();
    client.start();
    expect(() => {
      client.dispose();
      client.dispose();
    }).not.toThrow();
  });
});
