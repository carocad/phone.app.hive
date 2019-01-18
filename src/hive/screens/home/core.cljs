(ns hive.screens.home.core
  (:require [reagent.core :as r]
            [react-native :as React]
            [expo :as Expo]
            [clojure.string :as str]
            [hive.state.queries :as queries]
            [hive.utils.miscelaneous :as tool]
            [hive.services.mapbox :as mapbox]
            [hive.services.location :as location]
            [hive.utils.geometry :as geometry]
            [hive.services.kamal :as kamal]
            [hive.screens.symbols :as symbols]
            [datascript.core :as data]
            [hive.state.core :as state]
            [hive.assets :as assets]
            [hive.utils.promises :as promise]))

; NOTE: this is the way to remove all routes ... not sure where to do this
;(for [r (data/q queries/routes-ids (state/db))]
;  [:db.fn/retractEntity [:route/uuid r]])

(defn- on-directions-response
  "takes a kamal directions response and attaches it to the current user.
  Further trip information is also retrieved"
  [directions db]
  (let [user (data/q queries/user-id db)
        base [directions
              {:user/uid        user
               :user/directions [:directions/uuid (:directions/uuid directions)]}]]
    (concat base
      (distinct
        (for [step (:directions/steps directions)
              :when (= (:step/mode step) "transit")
              ;; check just in case ;)
              :when (some? (:step/trip step))]
          [kamal/chain! db (:step/trip step) :trip/route])))))

(defn- set-target
  "associates a target and a path to get there with the user"
  [db navigate target]
  (let [user     (data/q queries/user-id db)
        position (data/pull db [:user/position] [:user/uid user])
        start    (:coordinates (:geometry (:user/position position)))
        end      (:coordinates (:place/geometry target))]
    [{:user/uid  user
      :user/goal [:place/id (:place/id target)]}
     [promise/finally [kamal/get-directions! db [start end]]
                      [on-directions-response (state/db)]]
     [React/Keyboard.dismiss]
     [navigate "directions"]]))

(defn- humanize-distance
  "Convert a distance (meters) to human readable form."
  [distance]
  (if (> distance 1000)
    (str (. (/ distance 1000) (toFixed 1)) " km")
    (str (. distance (toFixed 0)) " m")))

(defn- Places
  "list of items resulting from a geocode search, displayed to the user to choose his
  destination"
  [props]
  (let [navigate  (:navigate (:navigation props))
        places   @(state/q! '[:find [(pull ?id [:place/id :place/geometry
                                                :place/text :place/context]) ...]
                              :where [?id :place/id]])
        position @(state/q! queries/user-position)
        height    (* 80 (count places))]
    [:> React/View {:height height :paddingTop 100 :paddingLeft 10}
     (for [target places
           :let [distance (geometry/haversine (:coordinates (:geometry position))
                                              (:coordinates (:place/geometry target)))]]
       ^{:key (:place/id target)}
       [:> React/TouchableOpacity
         {:style    {:flex 1 :flexDirection "row"}
          :onPress #(state/transact! (set-target (state/db) navigate target))}
         [:> React/View {:flex 0.2 :alignItems "center" :justifyContent "flex-end"}
           [:> assets/Ionicons {:name "ios-pin" :size 26 :color "red"}]
           [:> React/Text (humanize-distance distance)]]
         [:> React/View {:flex 0.8 :justifyContent "flex-end"}
           [:> React/Text {:numberOfLines 1} (:place/text target)]
           [:> React/Text {:note true :style {:color "gray"} :numberOfLines 1}
            (str/join ", " (map :text (:place/context target)))]]])]))

(defn- reset-places
  "transact the geocoding result under the user id"
  ([db]
   (for [id (data/q '[:find [?id ...] :where [?id :place/id]]
                    db)]
     [:db.fn/retractEntity id]))
  ([db data]
   (concat (reset-places db)
           (for [f (:features data)]
             (tool/with-ns "place" f)))))

(defn- autocomplete
  "request an autocomplete geocoding result from mapbox and adds its result to the
   app state"
  [text db props]
  (when (not (empty? text))
    (let [navigate (:navigate (:navigation props))
          user     (data/q queries/user-id db)
          data     (data/pull db [:user/position {:user/area [:area/bbox]}]
                                 [:user/uid user])
          args {:query        text
                :proximity    (:user/position data)
                :access_token (:ENV/MAPBOX state/tokens)
                :bbox         (:area/bbox (:user/area data))}
          validated (tool/validate ::mapbox/request args ::invalid-input)]
      (if (tool/error? validated)
        [[navigate "location-error" validated]
         [React/Keyboard.dismiss]]
        [(delay (.. (mapbox/geocoding! args)
                    (then #(reset-places (state/db) %))))]))))

(defn- SearchBar
  [props]
  (let [pids @(state/q! queries/places-id)
        ref   (volatile! nil)]
    [:> React/View {:flex 1 :flexDirection "row" :backgroundColor "white"
                    :elevation 5 :borderRadius 5 :shadowColor "#000000"
                    :shadowRadius 5 :shadowOffset {:width 0 :height 3}
                    :shadowOpacity 1.0}
     [:> React/View {:height 30 :width 30 :padding 8 :flex 0.1}
       (if (empty? pids)
         [:> assets/Ionicons {:name "ios-search" :size 26}]
         [:> React/TouchableWithoutFeedback
           {:onPress #(when (some? @ref)
                        (. @ref clear)
                        (state/transact! (reset-places (state/db))))}
           [:> assets/Ionicons {:name "ios-close-circle" :size 26}]])]
     [:> React/TextInput {:placeholder "Where would you like to go?"
                          :ref #(vreset! ref %) :style {:flex 0.9}
                          :underlineColorAndroid "transparent"
                          :onChangeText #(state/transact! (autocomplete % (state/db) props))}]]))

(defn- on-location-updated [position]
  (state/transact! (location/set-location (state/db) position)))

(defn Home
  "The main screen of the app. Contains a search bar and a mapview"
  [props]
  (r/with-let [tracker  (location/watch! (location/defaults on-location-updated))
               navigate (:navigate (:navigation props))
               pids     (state/q! queries/places-id)
               bbox     (state/q! queries/user-area-bbox)
               position (state/q! queries/user-position)]
    [:> React/View {:flex 1}
      (if (empty? @pids)
        [:> Expo/MapView {:region (geometry/mapview-region {:bbox @bbox
                                                            :position @position})
                          :showsUserLocation     true
                          :style                 {:flex 1}
                          :showsMyLocationButton true}]
        [Places props])
      [:> React/View {:position "absolute" :width "95%" :height 44 :top 35
                      :left "2.5%" :right "2.5%"}
        [SearchBar props]]
      (when (empty? @pids)
        [:> React/View (merge (symbols/circle 52) symbols/shadow
                              {:position "absolute" :bottom 20 :right 20
                               :backgroundColor "#FF5722"})
          [:> React/TouchableOpacity {:onPress #(state/transact! [[navigate "settings"]
                                                                  [kamal/get-areas!]])}
            [:> assets/Ionicons {:name "md-apps" :size 26
                                 :style {:color "white"}}]]])]
    ;; remove tracker on component will unmount
    (finally (. tracker (then #(. % remove))))))

;(state/transact! [[:db.fn/retractEntity [:route/uuid "cjd5qccf5007147p6t4mneh5r"]]])
;(data/pull (state/db) '[*] [:route/uuid "5b44dbb7-ac02-40a0-b50f-6c855c5bff14"])

;(data/q '[:find [(pull ?id [*]) ...] :where [?id :place/id]] (state/db))