(ns pm.dev-http
  "The dev server's request handler (shadow-cljs `:dev-http … :handler`), for
   the local state files only. shadow-cljs sends `Access-Control-Allow-Origin: *`
   on everything it serves from `public/`, which would let any web page open in
   a browser on this Mac read the real state, the crew's prompts and the bridge
   token. So those files live outside `public/`, in the gitignored `state/`,
   and are served here: same-origin only, never `*`. Everything else falls
   through to shadow's usual handler."
  (:require [clojure.java.io :as io]
            [shadow.http.push-state :as push-state]))

(def game-origin "http://localhost:8080")

(defn allowed?
  "A same-origin request from the game (browsers omit Origin on same-origin
   GETs), or the address typed in by hand. Refuses any other origin and any
   cross-site or same-site (another localhost port) fetch."
  [{:keys [headers]}]
  (let [origin (get headers "origin")
        site   (get headers "sec-fetch-site")]
    (and (or (nil? origin) (= origin game-origin))
         (not (contains? #{"cross-site" "same-site"} site)))))

(defn handle [{:keys [uri] :as req}]
  (if-let [[_ f] (re-matches #"/state/([a-z0-9][a-z0-9-]{0,40}\.edn)" (or uri ""))]
    (let [file (io/file "state" f)]
      (cond
        (not (allowed? req))
        {:status 403 :headers {"content-type" "text/plain"} :body "forbidden"}

        (.isFile file)
        {:status  200
         :headers {"content-type"                "application/edn; charset=utf-8"
                   "cache-control"               "no-store"
                   "access-control-allow-origin" game-origin
                   "vary"                        "Origin"}
         :body    (slurp file)}

        :else
        {:status 404 :headers {"content-type" "text/plain"} :body "not found"}))
    (push-state/handle req)))
