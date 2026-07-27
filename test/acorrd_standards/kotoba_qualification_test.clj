(ns acorrd-standards.kotoba-qualification-test
  (:require [acorrd-standards.main :as oracle]
            [clojure.test :refer [deftest is]]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.ir :as compiler-ir]
            [kotoba.runtime :as runtime]
            [kotoba.wasm-exec :as wasm-exec]))

(deftest q9-page-limit-oracle-and-backends-agree
  (let [source (slurp "src/acorrd_standards/page_limit.kotoba")
        forms (runtime/read-forms source :kotoba)
        reference (runtime/wasm-binary forms)
        compiled (compiler/compile-source source :wasm32-kotoba-v1 {:allow #{}})]
    (is (:kotoba.wasm/ok? reference))
    (is (= 100 (oracle/page-limit 250)
           (wasm-exec/run-main (:kotoba.wasm/binary reference) [])
           (compiler-ir/execute (:kir compiled) 'main [])))
    (is (= #{} (get-in compiled [:hir :effects])))
    (is (= [20 20 1 20 100] (mapv oracle/page-limit [-1 0 1 20 250])))))
