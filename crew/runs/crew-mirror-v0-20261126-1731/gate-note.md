FAIL
Build completed. (0 warnings)
Ran 1633 tests containing 8738 assertions.
4 failures, 0 errors.

FAIL in (t15-constants) (pm/crew_talk_test.cljs:177:7)
expected: (= #{"allow" "deny" "say"} b/actions)
  actual: (not (= #{"allow" "deny" "say"} #{"allow" "always" "deny" "say" "approve"}))

FAIL in (t20-validate-refusals) (pm/crew_talk_test.cljs:264:7)
expected: (= {:ok false, :status 400, :reason "bad action"} (b/validate (rq "le-testeur" (assoc ALLOW "action" "approve")) CTX))
  actual: (not (= {:ok false, :status 400, :reason "bad action"} {:ok true, :member :le-testeur, :action :approve, :prompt-id "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6", :text nil}))

FAIL in (t40-stale-and-error-outcomes-invalid-calls) (pm/crew_talk_test.cljs:568:9)
expected: (identical? T1 (tk/crew-send T1 1 :approve nil))

FAIL in (t26-refusals-in-order) (pm/crew_talk_v1_test.cljs:279:7)
expected: (= #{"allow" "deny" "say"} b/actions)
  actual: (not (= #{"allow" "deny" "say"} #{"allow" "always" "deny" "say" "approve"}))
