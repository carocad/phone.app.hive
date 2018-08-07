(ns hive.services.kamal
  (:require [clojure.string :as str]
            [cljs.tools.reader.edn :as edn])
  (:import (goog.date DateTime Interval)))

(defn read-object
  [[tag _ text]]
  (case tag
    java.time.Duration (Interval.fromIsoString text)
    java.time.LocalDateTime (DateTime.fromRfc822String text)))

(def readers {'uuid   uuid
              'object read-object})

(defn- local-time
  "returns a compatible Java LocalDateTime string representation"
  ([]
   (local-time (new DateTime)))
  ([^js/DateTime now]
   (let [gtime (. now (toIsoString true))]
     (str/replace gtime " " "T"))))

;(def template "https://hive-6c54a.appspot.com/directions/v5")
(def route-url "http://192.168.0.45:3000/area/frankfurt/directions?coordinates={coordinates}&departure={departure}")
(def entity-url "http://192.168.0.45:3000/area/frankfurt/{entity}/{id}")

(defn entity
  "ref is a map with a single key value pair of the form {:trip/id 2}"
  [ref]
  (let [[k v]   (first ref)
        url (-> (str/replace entity-url "{entity}" (namespace k))
                (str/replace "{id}" v))]
    [url {:method "GET"
          :headers {:Accept "application/edn"}}]))

(defn entity!
  "executes the result of entity with js/fetch.

  Returns a promise that will resolve
  "
  [ref] ;; TODO: dont request if entity already exists in db
  (let [[url opts] (entity ref)]
    (.. (js/fetch url (clj->js opts))
        (then (fn [^js/Response response] (. response (text))))
        (then #(edn/read-string {:readers readers} %))
        (then vector))))
        ;; TODO: error handling)

(defn process-directions
  "takes a kamal directions response and attaches it to the current user.
  Further trip information is also retrieved"
  [path user]
  (let [base [path
              {:user/id user
               :user/route [:route/uuid (:route/uuid path)]}]]
    (concat base
      (eduction (filter #(= (:step/mode %) "transit"))
                ;; check just in case ;)
                (filter #(some? (:stop_times/trip %)))
                (map #(vector entity! (:stop_times/trip %)))
                (distinct)
                (:route/steps path)))))

(defn directions
  "takes a map with the items required by ::request and replaces their values into
   the Mapbox URL template. Returns the full url to use with an http service

   https://www.mapbox.com/api-documentation/#request-format"
  [coordinates departure]
  (let [url (-> (str/replace route-url "{coordinates}" coordinates)
                (str/replace "{departure}" (local-time departure)))]
    [url {:method "GET"
          :headers {:Accept "application/edn"}}]))

(defn directions!
  "executes the result of directions with js/fetch.

  Returns a transaction that will resolve to a transaction that assigns the
  returned route to the current user.

  All gtfs trips and route are also requested"
  ^js/Promise
  [coordinates departure user]
  (let [[url opts] (directions coordinates departure)]
    (.. (js/fetch url (clj->js opts))
        (then (fn [^js/Response response] (. response (text))))
        (then #(edn/read-string {:readers readers} %))
        (then #(process-directions % user)))))
        ;; TODO: error handling
