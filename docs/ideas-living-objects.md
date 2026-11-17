# Ideas: living objects (v2, not this game)

From a chat on Nov 16. Michael's idea: "a frog came to town and you noticed after a while the frog was hungry and relayed it to me". Objects with state that talk by passing messages. It's Alan Kay's original OOP and Hewitt's actor model (1973). What's new is that one of the actors (the twin) can think.

## The shape

- **An object is a value.** A plain map, e.g. `{:id :frog :kind :creature :last-fed t :every-ms ...}`.
- **Time is an input, not a tick.** `(hungry? frog now)` is a pure function, so nothing needs to poll to *know*.
- **Messages are data, and state is a fold over them.** `(reduce apply-event initial events)` is event sourcing. `pm.events` / `world/apply-event` already work this way.
- **Dispatch by kind.** `(defmulti receive (fn [obj msg] [(:kind obj) (:type msg)]))`.
- **The twin is the router with judgment.** It decides what's worth interrupting the player for: now, later, bundled, or handled itself. A cron job doesn't know whether you already did it; the object does.

## The Clojure pieces

- **core.async**: channels are the mailboxes, `go-loop`s are the objects (CSP, Hoare 1978). This is the one Michael was reaching for.
- **Sleep, not polling.** You only need a wake-up to *tell* someone. On each feed the object books one wake for `last-fed + every`: `(alts! [inbox (timeout ms)])`. A new feed cancels the old booking and makes a new one. That's one alarm per feed, versus cron's fixed checks every day. (Erlang `send_after`, an OS timer queue.)
- **`atom` / `agent`**: where the current value lives. Clojure agents take functions, not messages, and their state is readable by anyone. Hickey chose that deliberately over sealed-off Erlang actors.
- **`add-watch`**: a state change turns into an outgoing message.
- **Datomic / XTDB**: facts that are never overwritten, with `as-of` queries ("what did the frog know last Tuesday?"). That's long-term memory for a twin; `state/crew-log.edn` is a small version.

## Sketch

```clojure
(defn creature-loop [obj inbox twin]
  (go-loop [o obj]
    (let [wake    (- (+ (:last-fed o) (:every-ms o)) (now))
          [msg ch] (alts! [inbox (timeout (max 0 wake))])]
      (if (= ch inbox)
        (recur (receive o msg))                      ; e.g. {:type :fed :at t}
        (do (>! twin {:from (:id o) :type :hungry})  ; the twin decides if it reaches the player
            (recur (assoc o :every-ms ##Inf)))))))   ; one nudge, then wait to be fed
```

## Where it would land in Prime Meridian

The mirrors (`:state` plus `:rules [{:when [:due? :feed] :say :hungry}]`) are a static version. v2 would let any stateful thing arrive, age and speak, with the twin choosing what reaches the player. For Flora, that could replace the 9:00/17:00 cron (option B from the same chat: game first, text only if she's more than 12 h overdue).
