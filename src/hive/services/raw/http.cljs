(ns hive.services.raw.http
  (:require [cljs.core.async :as async]
            [cljs.spec.alpha :as s]
            [hive.rework.core :as rework]
            [hive.rework.util :as tool]))

(s/def ::init (s/map-of keyword? any?))
(s/def ::url (s/and string? not-empty))
(s/def ::request (s/cat :URL ::url :options (s/? ::init)))

(defn json!
  "takes a request shaped according to ::request and executes it asynchronously.
   Extra http properties can be passed as per fetch documentation.
  Returns a channel with the result of fetch as a JS object or an exception on error

  https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope/fetch"
  [[url init]]
  (let [result (async/chan)]
    (-> (js/fetch url (clj->js init))
        (.then #(.json %))
        (.then #(async/put! result %))
        (.catch #(async/put! result (ex-info "network error" %
                                             ::network-error))))
    result))

(defn text!
  "takes a request shaped according to ::request and executes it asynchronously.
   Extra http properties can be passed as per fetch documentation.
  Returns a channel with the result of fetch as a String or an exception on error

  https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope/fetch"
  [[url init]]
  (let [result (async/chan)]
    (-> (js/fetch url (clj->js init))
        (.then #(.text %))
        (.then #(async/put! result %))
        (.catch #(async/put! result (ex-info "network error" %
                                             ::network-error))))
    result))

(s/fdef request! :args (s/cat :request ::request))
