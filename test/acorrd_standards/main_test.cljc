(ns acorrd-standards.main-test
  "Contract + behavioral test for the acorrd-standards L4 actor (cljc port).
  Runs under babashka: `bb test`. Stronger than the py static contract test —
  exercises CRUD / pagination / filtering / expansion / validation against the
  in-memory Datom-log store."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [acorrd-standards.main :as m]))

(def entities ["Account" "Loan" "Policy" "Claim" "Transaction" "Customer"])

(deftest schema-has-all-entities
  (is (= (set entities) (set m/entities))))

(deftest full-crud-per-entity
  (testing "every entity exposes POST/GET-list/GET-one/PATCH/DELETE"
    (doseq [{:keys [plural]} m/entity-specs]
      (let [base (str "/v1/" plural)
            paths (set (map (juxt :method :path) m/routes))]
        (is (contains? paths ["POST" base]))
        (is (contains? paths ["GET" base]))
        (is (contains? paths ["GET" (str base "/{id}")]))
        (is (contains? paths ["PATCH" (str base "/{id}")]))
        (is (contains? paths ["DELETE" (str base "/{id}")]))))
    (is (= 30 (count m/routes)))))

(deftest create-and-get
  (let [s (m/fresh-store)
        [rec status] (m/handle-create s "Account" {:type "savings" :balance 1000.0})]
    (is (= 201 status))
    (is (= "savings" (:type rec)))
    (is (= 1000.0 (:balance rec)))
    (is (re-find #"^acorrdst_acc_" (:id rec)))
    (is (= [rec 200] (m/handle-get s "Account" (:id rec) {})))))

(deftest validation-required-and-unknown
  (let [s (m/fresh-store)]
    (testing "missing required field -> 400"
      (is (= 400 (second (m/handle-create s "Account" {})))))
    (testing "unknown field -> 400"
      (is (= 400 (second (m/handle-create s "Account" {:type "x" :balance 100 :bogus 1})))))))

(deftest coercion
  (let [s (m/fresh-store)
        [rec _] (m/handle-create s "Account" {:type "checking" :balance "2500.75"})]
    (is (= 2500.75 (:balance rec)))
    (let [[loan _] (m/handle-create s "Loan" {:principal "50000" :rate "5.5" :termMonths "60"})]
      (is (= 50000.0 (:principal loan)))
      (is (= 5.5 (:rate loan)))
      (is (= 60 (:termMonths loan))))
    (let [[policy _] (m/handle-create s "Policy" {:type "auto" :premium "1200.00"})]
      (is (= 1200.0 (:premium policy))))))

(deftest list-filter-and-paginate
  (let [s (m/fresh-store)]
    (dotimes [i 25] (m/handle-create s "Account" {:type (str "t" (mod i 3)) :balance (float i) :status (if (even? i) "active" "closed")}))
    (let [[body _] (m/handle-list s "Account" {})]
      (is (= 20 (:count body)))            ; default limit
      (is (true? (:has_more body)))
      (is (= 25 (:total body))))
    (let [[body _] (m/handle-list s "Account" {:status "active"})]
      (is (= 13 (:total body))))))         ; even i in 0..24 -> 13

(deftest expansion
  (let [s (m/fresh-store)
        [policy _] (m/handle-create s "Policy" {:type "health" :premium 500.0})
        [claim _] (m/handle-create s "Claim" {:policyId (:id policy) :amount 1000.0 :status "pending"})
        [got _] (m/handle-get s "Claim" (:id claim) {:expand "policyId"})]
    (is (= policy (:policyId_obj got)))))

(deftest update-and-delete
  (let [s (m/fresh-store)
        [rec _] (m/handle-create s "Account" {:type "savings" :balance 100.0})
        [upd _] (m/handle-update s "Account" (:id rec) {:balance 200.0})]
    (is (= 200.0 (:balance upd)))
    (is (= "savings" (:type upd)))        ; immutable field preserved
    (is (= (:id rec) (:id upd)))           ; id immutable
    (is (= 200 (second (m/handle-delete s "Account" (:id rec)))))
    (is (= 404 (second (m/handle-get s "Account" (:id rec) {}))))))

(deftest eavt-fact-emission
  (testing "datomic EAVT mapping preserved: acorrd_standards.<Entity>/<field>"
    (let [facts (m/emit-facts "Account" {:id "acorrdst_acc_x" :type "savings" :balance 1000.0})]
      (is (= "savings" (get facts "acorrd_standards.Account/type")))
      (is (= 1000.0 (get facts "acorrd_standards.Account/balance")))
      (is (= "acorrdst_acc_x" (get facts "acorrd_standards.Account/id"))))))

(deftest healthz
  (is (= [{:status "ok" :actor "acorrd_standards-compat" :tier "L4" :entities entities} 200] (m/healthz))))

#?(:clj (defn -main [& _]
          (let [{:keys [fail error]} (run-tests 'acorrd-standards.main-test)]
            (System/exit (if (pos? (+ fail error)) 1 0)))))
