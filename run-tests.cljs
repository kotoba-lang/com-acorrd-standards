(ns run-tests
  "Run the portable half of this actor's suite under nbb (ClojureScript on
  Node via SCI), so the .cljc is checked by the runtime that actually ships
  rather than only by the JVM.

  Coverage is DELIBERATELY partial, and the gap is exact. This repo has two
  test namespaces:

    acorrd-standards.main-test  (.cljc) -- portable, listed below
    acorrd-standards.kotoba-qualification-test  (.clj) -- JVM-only, NOT listed

  The qualification namespace requires kotoba.compiler.core, kotoba.runtime
  and kotoba.wasm-exec to compile src/acorrd_standards/page_limit.kotoba and execute it on
  two backends. That toolchain is JVM-only, so nbb cannot run it and must
  not pretend to.

  Measured 2026-08-20 -- the two runtimes agree on the shared namespace:

    clojure -M:test   ->  Ran 12 tests containing 64 assertions
    this runner       ->      11 tests containing 60 assertions

  The difference is exactly 1 deftest(s) and 4 assertions, which is the
  whole of the qualification namespace. If that arithmetic stops holding,
  one of the two runners has silently stopped running something.

  Every namespace is listed explicitly on purpose: clojure -M:test finds
  namespaces by scanning the test directory, but a cljs runner does not, so
  a namespace omitted here would never run and nothing would say so.

    nbb --classpath src:test run-tests.cljs"
  (:require [cljs.test :as t]
            [acorrd-standards.main-test]))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (println (str "\nnbb: " (:test m) " tests, " (:pass m) " passed, "
                (:fail m) " failed, " (:error m) " errors"))
  ;; Without this a failing suite exits 0 and the gate is green forever.
  (when (pos? (+ (or (:fail m) 0) (or (:error m) 0)))
    (set! (.-exitCode js/process) 1)))

(t/run-tests 'acorrd-standards.main-test)
