#!/usr/bin/env python3
"""hal-relay: put a message from the game into Hal's own Hermes chat.

    hal_relay.py <session_id> "text"

Uses Hermes's own gateway client (tui_gateway.owner_forward.connect_owner):
resumes the live session and submits the text as a prompt, exactly as the
desktop app does when it forwards one. Prints the gateway's receipt as JSON
(status queued|started|terminal on success). Run with the Hermes venv's
python and PYTHONPATH=~/.hermes/hermes-agent.
"""
import asyncio
import json
import sys
import uuid
from pathlib import Path


async def main(session_id: str, text: str) -> dict:
    from tui_gateway.owner_forward import connect_owner, discover_owner
    home = Path.home() / ".hermes"
    async with connect_owner(home, discover_owner(home)) as client:
        # omit_messages: this chat's history is far bigger than the socket's
        # 1 MiB frame limit, and the relay only needs the session attached
        attached = await client.rpc("session.resume", session_id=session_id,
                                    omit_messages=True, defer_history=True)
        target = str(attached.get("session_id") or session_id)
        return await client.rpc("prompt.submit", session_id=target, text=text,
                                submission_id="pm-game-" + uuid.uuid4().hex)


if __name__ == "__main__":
    if len(sys.argv) != 3 or not sys.argv[1] or not sys.argv[2].strip():
        print('usage: hal_relay.py <session_id> "text"', file=sys.stderr)
        sys.exit(1)
    try:
        receipt = asyncio.run(main(sys.argv[1], sys.argv[2]))
    except Exception as exc:  # the bridge reports it to the game
        print(json.dumps({"ok": False, "error": f"{type(exc).__name__}: {exc}"}))
        sys.exit(2)
    print(json.dumps({"ok": True, "status": receipt.get("status"), "admission_id": receipt.get("admission_id")}))
