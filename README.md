# Prime Meridian

> What is even real anymore? Gamifying the agents — for all and none.

A cozy 3D management game where the characters are real AI agents doing real work. Rooms are projects. Talk to the agents, possess one to steer, and watch the world change as they build it.

- **Design**: [docs/GDD.md](docs/GDD.md)
- **Stack**: ClojureScript (shadow-cljs) + three.js, Babashka bridge, Hermes agents. No TypeScript.
- **Agent crew** (Assignment 3): [crew/README.md](crew/README.md): five AI agents that build the game, with a Mermaid diagram of roles and data flow.
- **Status**: the purchased island kit is the world (loaded locally, not in this repo); Flora's feeding loop and stand-ins are built by the crew.

## Run

```bash
npm install
npx shadow-cljs watch app     # http://localhost:8080
```

Built by Michael Lopez with Hal Niner (bot). Course project, fall 2026.
