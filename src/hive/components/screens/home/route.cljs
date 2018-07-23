(ns hive.components.screens.home.route
  (:require [hive.queries :as queries]
            [hive.components.foreigns.react :as react]
            [hive.rework.util :as tool]
            [datascript.core :as data]
            [hive.rework.core :as work]
            [hive.components.symbols :as symbols]
            [hive.foreigns :as fl]
            [hive.components.foreigns.expo :as expo]
            [hive.libs.geometry :as geometry]
            [goog.date.duration :as duration]
            [clojure.string :as str]
            [reagent.core :as r])
  (:import [goog.date Interval DateTime]))

;(.. DateTime (fromRfc822String "2018-05-07T10:15:30"))

(defn process-directions
  "takes a mapbox directions response and returns it.
   Return an exception if no path was found"
  [path user]
  [path
   {:user/id user
    :user/route [:route/uuid (:route/uuid path)]}])

(def big-circle (symbols/circle 16))
(def small-circle (symbols/circle 12))

(defn- TransitLine
  [data]
  [:> react/View {:flex 1 :alignItems "center"}
    [:> react/View (merge {:backgroundColor "red"}
                          big-circle)]
    [:> react/View {:backgroundColor "red" :width "8%" :height "80%"}]
    [:> react/View (merge {:backgroundColor "red" :elevation 10}
                          big-circle)]])

(defn- WalkingSymbols
  [data]
  [:> react/View {:flex 1 :alignItems "center"
                  :justifyContent "space-around"}
    (for [i (range 5)]
      ^{:key i} [:> react/View (merge {:backgroundColor "gray"}
                                      small-circle)])])

(def section-height 140)
(def subsection-height 20)

(defn- SectionDetails
  [section human-time]
  (r/with-let [expanded? (r/atom false)]
    (let [height (+ section-height (* subsection-height
                                      (if @expanded? 1 0)
                                      (count section)))]
      [:> react/View {:height height :flexDirection "row"}
        [:> react/View {:flex 1 :alignItems "center"}
          [:> react/Text {:style {:color "gray" :fontSize 12}}
                         human-time]]
        (if (= "walking" (some :step/mode section))
          [WalkingSymbols section]
          [TransitLine section])
        [:> react/View {:flex 9 :justifyContent "space-between"}
          [:> react/Text (some :step/name (butlast section))]
          [:> react/TouchableOpacity
            {:flex 1 :onPress #(reset! expanded? (not @expanded?))}
            (if (not @expanded?)
             [:> react/Text {:style {:color "gray"}}
                            (:maneuver/instruction (first section))]
             (for [step section]
               ^{:key (:step/distance step)}
               [:> react/Text {:style {:color "gray"}}
                              (:maneuver/instruction step)]))]
         [:> react/Text (:step/name (last section))]]])))

(defn- Route
  [uid]
  (let [route   @(work/pull! [{:route/steps [:step/departure :step/mode
                                             :step/name :maneuver/instruction
                                             :step/distance]}]
                             [:route/uuid uid])]
    [:> react/View {:flex 1}
      (for [part (partition-by :step/mode (:route/steps route))
            :let [departs ^DateTime (:step/departure (first part))
                  human-time (subs (. departs (toIsoTimeString))
                                   0 5)]]
        ^{:key human-time}
         [SectionDetails part human-time])]))

(defn- Transfers
  []
  (into [:> react/View {:flex 4 :flexDirection "row" :justifyContent "space-around"
                        :top "0.5%"}]
        [[:> expo/Ionicons {:name "ios-walk" :size 32}]
         [:> expo/Ionicons {:name "ios-arrow-forward" :size 26 :color "gray"}]
         [:> expo/Ionicons {:name "ios-bus" :size 32}]
         [:> expo/Ionicons {:name "ios-arrow-forward" :size 26 :color "gray"}]
         [:> expo/Ionicons {:name "ios-train" :size 32}]]))

(defn- Info
  [props]
  (let [[duration goal] @(work/q! '[:find [?duration ?goal]
                                    :where [_      :user/route ?route]
                                           [?route :route/duration ?duration]
                                           [_      :user/goal ?goal]])]
    [:> react/View props
     [:> react/View {:flexDirection "row" :paddingLeft "1.5%"}
      [Transfers]
      [:> react/Text {:style {:flex 5 :color "gray" :paddingTop "2.5%"
                              :paddingLeft "10%"}}
       (when (some? duration)
         (duration/format (* 1000 (. ^Interval duration (getTotalSeconds)))))]]
     [:> react/Text {:style {:color "gray" :paddingLeft "2.5%"}}
                    (:text goal)]]))

(defn- path
  [db uid]
  (let [route (data/pull db [{:route/steps [:step/geometry]}]
                            [:route/uuid uid])]
    (sequence (comp (map :step/geometry)
                    (map :coordinates)
                    (mapcat #(drop-last %)) ;; last = first of next
                    (map geometry/latlng))
              (:route/steps route))))

(defn Instructions
  "basic navigation directions.

   Async, some data might be missing when rendered !!"
  [props]
  (let [window (tool/keywordize (. fl/ReactNative (Dimensions.get "window")))
        uid   @(work/q! '[:find ?route . :where [_ :user/route ?route]])]
    [:> react/ScrollView {:flex 1}
      [:> react/View {:height (* 0.9 (:height window))}
        [symbols/CityMap
          (when (some? uid)
            [:> expo/MapPolyline {:coordinates (path (work/db) uid)
                                  :strokeColor "#3bb2d0"
                                  :strokeWidth 4}])]]
      [:> react/View {:flex 1 :backgroundColor "white"}
        [Info {:flex 1 :paddingTop "1%"}]
        (when (some? uid)
          [Route uid])]
      [:> react/View (merge (symbols/circle 52) symbols/shadow
                            {:position "absolute" :right "10%"
                             :top (* 0.88 (:height window))})
        [:> expo/Ionicons {:name "ios-navigate" :size 62 :color "blue"}]]]))

;hive.rework.state/conn
;(into {} (work/entity [:route/uuid #uuid"5b2d247b-f8c6-47f3-940e-dee71f97d451"]))
;(work/q queries/routes-ids)

;(let [id      (data/q queries/user-id (work/db))]
;  (:steps (:route/route (:user/route @(work/pull! [{:user/route [:route/route]}]
;                                                  [:user/id id])))))

