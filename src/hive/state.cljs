(ns hive.state
  (:require [hive.rework.util :as tool]))

;;FIXME: this should come from the server, not being hardcoded
(def cities (js->clj (js/require "./assets/cities.json")
              :keywordize-keys true))
(def tokens (js->clj (js/require "./assets/init.json")
              :keywordize-keys true))

(def schema {:user/city             {:db.valueType   :db.type/ref
                                     :db.cardinality :db.cardinality/one}

             :user/directions       {:db.valueType   :db.type/ref
                                     :db.cardinality :db.cardinality/one}

             :user/id               {:db.unique :db.unique/identity}

             :city/name             {:db.unique :db.unique/identity}

             :session/uuid          {:db.unique :db.unique/identity}

             :route/uuid            {:db.unique :db.unique/identity}
             ;; needed to tell datascript to keep only 1 of these
             :react.navigation/name {:db.unique :db.unique/identity}})

;; needs to be an array of maps. This will be used for data/transact!
(def init-data
  (concat (map #(tool/with-ns "city" %) cities)
          [{:user/id -1} ;; dummy
           (tool/with-ns "token" tokens)]))


(def defaults {:user/city [:city/name "Frankfurt am Main"]})
