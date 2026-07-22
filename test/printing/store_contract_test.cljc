(ns printing.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `tobaccoops.store-contract-test` (cloud-itonami-isic-0115)."
  (:require [clojure.test :refer [deftest is]]
            [printing.store :as store]))

(defn- exercise [s]
  (store/add-press-line s "press-x" {:id "press-x" :name "X Line" :press-type "offset"})
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:press-line/id is :db.unique/identity) the same way
  ;; MemStore's plain `assoc` re-registration does.
  (store/add-press-line s "press-x" {:id "press-x" :name "X Line (renamed)" :press-type "offset"})
  (store/record-quality-inspection! s "press-x" "run-1" "fail")
  ;; re-recording (upsert) exercises the identity-upsert path on
  ;; :inspection/key the same way MemStore's plain `assoc` re-recording does.
  (store/record-quality-inspection! s "press-x" "run-1" "pass")
  (store/append-ledger! s {:t :committed :op :log-production-record :subject "press-x"})
  (store/append-ledger! s {:t :approval-requested :op :flag-quality-concern :subject "press-x"})
  {:press-line (store/registered-press-line s "press-x")
   :absent     (store/registered-press-line s "no-such-line")
   :inspection (store/quality-inspection-status s "press-x" "run-1")
   :inspection-absent (store/quality-inspection-status s "press-x" "run-2")
   :ledger     (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:press-line m) (:press-line d)))
    (is (= "X Line (renamed)" (:name (:press-line m))) "re-registration upserts, not forks history")
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= "pass" (:inspection m)))
    (is (= "pass" (:inspection d)) "inspection re-recording upserts, not forks history")
    (is (nil? (:inspection-absent m)))
    (is (nil? (:inspection-absent d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))

(deftest datomic-store-seeded-press-lines
  (let [dat (store/datomic-store {:initial-press-lines
                                   {"press-y" {:id "press-y" :name "Y Line"}}})]
    (is (= {:id "press-y" :name "Y Line"} (store/registered-press-line dat "press-y")))
    (is (empty? (store/ledger dat)))
    (is (nil? (store/quality-inspection-status dat "press-y" "run-1")))))
