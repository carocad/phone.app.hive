(ns hive.components.screens.home.route
  (:require [reagent.core :as r]
            [hive.queries :as queries]
            [hive.components.native-base :as base]
            [hive.components.react :as react]
            [hive.services.directions :as directions]
            [hive.rework.util :as tool]
            [datascript.core :as data]
            [hive.services.raw.http :as http]
            [clojure.core.async :as async]
            [hive.rework.core :as work]))

;(defn- tx-path
;  "takes a mapbox directions response and returns a transaction vector
;  to use with transact!. Return an exception if no path was found"
;  [path]
;  (let [id      (:uuid path)
;        garbage (remove #{id} (:route/remove path))]
;    (concat (map #(vector :db.fn/retractEntity [:route/uuid %]) garbage)
;            [(tool/with-ns :route (dissoc path :user/id))
;             {:user/id (:user/id path)
;              :user/directions [:route/uuid id]}])))

(defn- reform-path
  "takes a mapbox directions response and returns it.
   Return an exception if no path was found"
  [path]
  (cond
    (not= (:code path) "Ok")
    (ex-info (or (:msg path) "invalid response")
             path ::invalid-response)

    (not (contains? path :uuid))
    (recur (assoc path :uuid (data/squuid)))

    :ok (tool/with-ns :route path)))

(defn get-path!
  "takes a geocoded feature (target) and queries the path to get there
  from the current user position. Returns a transaction or error"
  [goal]
  (let [loc  (work/q queries/user-position)
        tok  (work/q queries/mapbox-token)]
    (if (nil? loc)
      (async/to-chan [(ex-info "missing user location" goal ::user-position-unknown)])
      (let [args {::directions/coordinates [(:coordinates (:geometry loc))
                                            (:coordinates (:geometry goal))]
                  ::directions/access_token tok}
            url  (directions/request args)]
        (http/json! [url] (comp (map tool/keywordize)
                                (map reform-path)
                                (map vector)))))))

(defn choose-path
  "assocs the path to :user/directions and remove all other temporary paths"
  [path]
  (let [id      (:route/uuid path)
        garbage (remove #{id} (:route/remove path))]
    (concat (map #(vector :db.fn/retractEntity [:route/uuid %]) garbage)
            [{:user/id         (:user/id path)
              :user/directions [:route/uuid id]}])))

(def set-route!
  "takes a mapbox directions object and assocs the user/directions with
  it. All other temporary paths are removed"
  (work/pipe (work/inject :user/id queries/user-id)
             (work/inject :route/remove queries/routes-ids)
             choose-path
             work/transact!))

(defn- next-path!
  "get the next route in memory or fetch one otherwise"
  [routes i goal]
  (swap! i inc)
  (when (nil? (get @routes (inc @i)))
    (go-try (work/transact! (<? (fetch-path! @goal))))))

(defn route-controllers
  "display previous, ok and next buttons to the user to choose which route
  too take"
  [props routes i]
  (let [goal (work/q! queries/user-goal)
        path (work/entity [:route/uuid (get @routes @i)])]
    [:> react/View {:style {:flexDirection "row" :justifyContent "space-around"
                            :flex 1}}
     (when (> @i 0)
       [:> base/Button {:warning true :bordered false
                        :on-press #(swap! i dec)}
        [:> base/Icon {:name "ios-arrow-back"}]
        [:> base/Text "previous"]])
     [:> base/Button {:success true :bordered false
                      :on-press #(do (set-route! (into {} path))
                                     ((:goBack (:navigation props))))}
      [:> base/Text "OK"]]
     [:> base/Button {:warning true :iconRight true :bordered false
                      :on-press #(next-path! routes i goal)}
      [:> base/Text "next"]
      [:> base/Icon {:name "ios-arrow-forward"}]]]))

(defn route-meta
  "displays route meta information like distance, time, uuid etc"
  [route path]
  [:> react/View
   [:> base/CardItem [:> base/Icon {:name "flag"}]
    [:> base/Text (str "distance: " (:distance route) " meters")]]
   [:> base/CardItem [:> base/Icon {:name "flag"}]
    [:> base/Text (str "UUID: " (:route/uuid path) " meters")]]
   [:> base/CardItem [:> base/Icon {:name "information-circle"}]
    [:> base/Text "duration: " (Math/round (/ (:duration route) 60)) " minutes"]]
   [:> base/CardItem [:> base/Icon {:name "time"}]
    [:> base/Text (str "time of arrival: " (js/Date. (+ (js/Date.now)
                                                        (* 1000 (:duration route))))
                       " minutes")]]])

(defn route-details
  "display the complete route information to the user"
  [props i]
  (let [routes       (work/q! queries/routes-ids)
        path         (work/entity [:route/uuid (get @routes @i)])
        route        (first (:route/routes path))
        instructions (sequence (comp (mapcat :steps)
                                     (map :maneuver)
                                     (map :instruction)
                                     (map-indexed vector))
                               (:legs route))]
    (if (nil? path)
      [:> base/Spinner]
      [:> react/View
       [:> base/Card
        [route-meta route path]
        [route-controllers props routes i]]
       [:> base/Card
        [:> base/CardItem [:> base/Icon {:name "map"}]
         [:> base/Text "Instructions: "]]
        (for [[id text] instructions]
          ^{:key id}
          [:> base/CardItem
           (if (= id (first (last instructions)))
             [:> base/Icon {:name "flag"}]
             [:> base/Icon {:name "ios-navigate-outline"}])
           [:> base/Text text]])]])))

(defn instructions
  "basic navigation directions"
  [props]
  (let [counter (r/atom 0)]
    (fn []
      [:> base/Container
       [:> base/Content
        [route-details props counter]]])))

;(work/q queries/routes-ids)
