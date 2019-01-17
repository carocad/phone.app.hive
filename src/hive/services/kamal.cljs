(ns hive.services.kamal
  (:require [clojure.string :as str]
            [cljs.tools.reader.edn :as edn]
            [hive.state.queries :as queries]
            [datascript.core :as data]
            [lambdaisland.uri :as uri]
            [hive.utils.miscelaneous :as misc])
  (:import (goog.date DateTime Interval)))

(def readers {'uuid uuid})

(defn- zoned-time
  "returns a compatible Java LocalDateTime string representation"
  ([]
   (zoned-time (new DateTime)))
  ([^js/DateTime now]
   (let [gtime (. now (toIsoString true true))]
     (str/replace gtime " " "T"))))

;(def template "https://hive-6c54a.appspot.com/directions/v5")
(def server (uri/uri "https://kamal-live.herokuapp.com/"))
(def templates
  {:area/directions ["area" ::area "directions"] ;?coordinates={coordinates}&departure={departure}
   :area/entity     ["area" ::area ::entity ::id]
   :area/meta       ["area" ::area]
   :kamal/areas     ["area"]})

(defn- query-string
  [m]
  (str/join "&" (for [[k v] m] (str k "=" (js/encodeURIComponent v)))))

(defn- path
  [k values]
  (let [template (get templates k)]
    (str "/" (str/join "/" (replace values template)))))

(defn- read-text [^js/Response response] (. response (text)))

(defn- parse-edn [text] (edn/read-string {:readers readers} text))

(defn entity
  "ref is a map with a single key value pair of the form {:trip/id 2}"
  [db ref]
  (let [[k v]    (first ref)
        area-id  (data/q queries/user-area-id db)
        resource (path :area/entity
                       {::area area-id
                        ::entity (namespace k)
                        ::id v})
        url      (assoc server :path resource)]
    [(str url) {:method  "GET"
                :headers {:Accept "application/edn"}}]))

(defn get-entity!
  "executes the result of entity with js/fetch.

  Returns a promise that will resolve to a transaction with the
  requested entity"
  [db ref]
  ;; TODO: dont request if entity already exists in db
  (let [[url opts] (entity db ref)]
    (.. (js/fetch url (clj->js opts))
        (then (fn [^js/Response response] (. response (text))))
        (then #(edn/read-string {:readers readers} %))
        (then vector))))
;; TODO: error handling)

(defn- chain!
  "request an remote entity and also fetches the entity under keyword k
  when it arrives.

  For example: fetch the trip/id 123 and then the :trip/route that it
  points to"
  [db trip-ref k]
  (.. (get-entity! db trip-ref)
      (then (fn [[trip]] [trip [get-entity! db (k trip)]]))))

(defn- process-directions
  "takes a kamal directions response and attaches it to the current user.
  Further trip information is also retrieved"
  [db path]
  (let [user (data/q queries/user-id db)
        base [path
              {:user/uid        user
               :user/directions [:directions/uuid (:directions/uuid path)]}]]
    (concat base
      (distinct
        (for [step (:directions/steps path)
              :when (= (:step/mode step) "transit")
              ;; check just in case ;)
              :when (some? (:step/trip step))]
          [chain! db (:step/trip step) :trip/route])))))


(defn directions
  "takes a map with the items required by ::request and replaces their values into
   the Mapbox URL template. Returns the full url to use with an http service

   https://www.mapbox.com/api-documentation/#request-format"
  [db coordinates departure]
  (let [area-id    (data/q queries/user-area-id db)
        query      (query-string {"coordinates" coordinates
                                  ;;"departure"   (zoned-time departure)
                                  "departure"   "2018-05-07T10:15:30+01:00"})
        url        (assoc server :path (path :area/directions
                                             {::area area-id})
                                 :query query)]
    [(str url) {:method  "GET"
                :headers {:Accept "application/edn"}}]))

(defn- on-directions-response
  [^js/Response response]
  (if (.-ok response)
    (. response (text))
    (.. (. response (text))
        (then #(throw (ex-info (str "Error fetching directions." %)
                               (misc/roundtrip response)))))))

(defn get-directions!
  "executes the result of directions with js/fetch.

  Returns a transaction that will resolve to a transaction that assigns the
  returned route to the current user.

  All gtfs trips and route are also requested"
  ^js/Promise
  ([db coordinates departure]
   (let [[url opts] (directions db coordinates departure)]
     (.. (js/fetch url (clj->js opts))
         (then on-directions-response)
         (then #(edn/read-string {:readers readers} %))
         (then #(process-directions db %)))))
  ([db coordinates]
   (get-directions! db coordinates (new DateTime))))

(defn get-areas!
  "fetches the supported areas from kamal"
  []
  (let [resource (path :kamal/areas {})
        uri      (str (assoc server :path resource))]
    (.. (js/fetch uri)
        (then read-text)
        (then parse-edn))))
