(ns pm.kit
  "The game's starting world is the purchased Hiroshi's Island kit (3Ditions,
   single-project license). Its built player lives in `public/kit/`, copied from
   `assets/private/kit/dist/` (both gitignored: the license allows use, not
   redistribution). This namespace boots that player inside our page and hands
   back its live handle, `window.hiroshi` = {THREE scene camera rig renderer}.

   No kit (a fresh public clone): `load!` calls `on-missing` and the game falls
   back to the scaffold scene.")

(def kit-index "/kit/index.html")

(defn handle
  "The kit's live handle once its scene is ready, else nil."
  []
  (.-hiroshi js/window))

(defn- inject!
  "Copy the kit page's stylesheet links and external scripts into this page,
   with URLs made absolute against the kit's own index (the kit's loader finds
   its chunks relative to its own script URL). Inline scripts are skipped: in
   the private copy those are local experiments, now ported to CLJS."
  [html]
  (let [doc  (.parseFromString (js/DOMParser.) html "text/html")
        base (js/URL. kit-index js/location.href)
        abs  (fn [u] (.-href (js/URL. u base)))]
    (doseq [^js l (array-seq (.querySelectorAll doc "link[rel=stylesheet]"))]
      (let [^js el (.createElement js/document "link")]
        (set! (.-rel el) "stylesheet")
        (set! (.-href el) (abs (.getAttribute l "href")))
        (.appendChild js/document.head el)))
    (doseq [^js s (array-seq (.querySelectorAll doc "script[src]"))]
      (let [^js el (.createElement js/document "script")]
        (set! (.-src el) (abs (.getAttribute s "src")))
        (set! (.-async el) false)
        (.appendChild js/document.head el)))))

(defn- hide-kit-title!
  "The kit shows its own title ('Hiroshi's island') in its UI; this is
   Prime Meridian. Hide that text wherever the kit renders it."
  []
  (let [re    #"(?i)^Hiroshi.s island$"
        sweep (fn []
                (doseq [^js el (array-seq (.querySelectorAll js/document "h1,h2,h3,p,span,div,strong,em"))]
                  (when (and (zero? (.-length (.-children el)))
                             (re-matches re (.trim (.-textContent el))))
                    (set! (.. el -style -display) "none"))))]
    (sweep)
    (.observe (js/MutationObserver. sweep) js/document.documentElement
              #js {:childList true :subtree true :characterData true})))

(defn- close-studio!
  "The kit opens its Studio panel on every load, covering half the scene.
   Start with it closed (its Studio button still opens it): press its own
   close button once, when it has rendered."
  ([] (close-studio! 40))
  ([tries]
   (if-let [^js btn (.querySelector js/document "button[data-action='closeStudio']")]
     (when-let [^js aside (.closest btn "aside")]
       (when-not (.-hidden aside) (.click btn)))
     (when (pos? tries) (js/setTimeout #(close-studio! (dec tries)) 250)))))

(defn- when-ready!
  "Poll until the kit publishes `window.hiroshi` (after its first frame)."
  [on-ready]
  (if-let [h (handle)]
    (on-ready h)
    (js/setTimeout #(when-ready! on-ready) 250)))

(defn load!
  "Boot the kit. Calls (on-ready handle) when its scene is live, or (on-missing)
   when there's no kit in public/kit/."
  [on-ready on-missing]
  (-> (js/fetch kit-index)
      (.then (fn [^js r] (if (.-ok r) (.text r) (throw (js/Error. "no kit")))))
      (.then (fn [html]
               (set! (.-className js/document.body) "kit")
               (inject! html)
               (hide-kit-title!)
               (when-ready! (fn [h] (close-studio!) (on-ready h)))))
      (.catch (fn [e]
                (js/console.info "Prime Meridian: no kit in public/kit/ (" (str e) "); using the scaffold scene")
                (on-missing)))))

(defn backend-name
  "'webgpu' or 'webgl2' for the kit's renderer."
  [^js h]
  (let [^js b (.-backend (.-renderer h))]
    (if (.-isWebGPUBackend b) "webgpu" "webgl2")))
