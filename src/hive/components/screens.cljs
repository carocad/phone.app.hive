(ns hive.components.screens
  (:require [hive.components.core :refer [Container Header Text Icon MapView Body
                                          Content Button Title Card MapPolyline
                                          CardItem MapMarker View]]
            [hive.components.elements :as els]
            [hive.queries :as queries]
            [hive.rework.core :as rework]
            [cljs.core.async :as async :refer [go]]
            [clojure.string :as str]))

"Each Screen will receive two props:
 - screenProps - Extra props passed down from the router (rarely used)
 - navigation  - The main navigation functions in a map as follows:
   {:state     - routing state for this screen
    :dispatch  - generic dispatch fn
    :goBack    - pop's the current screen off the stack
    :navigate  - most common way to navigate to the next screen
    :setParams - used to change the params for the current screen}"

(defn latlng
  [coordinates]
  {:latitude (second coordinates) :longitude (first coordinates)})

(defn directions
  "basic navigation directions"
  [props]
  (let [route        @(rework/q! queries/user-route)
        instructions (sequence (comp (mapcat :steps)
                                     (map :maneuver)
                                     (map :instruction)
                                     (map-indexed vector))
                               (:legs route))]
    [:> Container
     [:> Content
      [:> Card
       [:> CardItem [:> Icon {:name "flag"}]
        [:> Text "distance: " 5 " meters"]] ;(:distance @route)
       [:> CardItem [:> Icon {:name "information-circle"}]
        [:> Text "duration: " (Math/round (/ 10 60)) " minutes"]] ;(:duration @route)
       [:> CardItem [:> Icon {:name "time"}]
        [:> Text "time of arrival: " (str (js/Date. (+ (js/Date.now)
                                                       (* 1000 20)))) ;(:duration @route))))))
         " minutes"]]]
      [:> Card
       [:> CardItem [:> Icon {:name "map"}]
        [:> Text "Instructions: "
         (for [[id text] instructions]
           (if (= id (first (last instructions)))
             ^{:key id} [:> CardItem [:> Icon {:name "flag"}]
                         [text text]]
             ^{:key id} [:> CardItem [:> Icon {:name "ios-navigate-outline"}]
                         [text text]]))]]]]]))

(defn home
  [props]
  (let [city      @(rework/q! queries/user-city)
        features  (rework/q! queries/user-places)
        goal      @(rework/q! queries/user-goal)
        route     @(rework/q! queries/user-route)]
    [:> Container {}
     [els/search-bar props features]
     (if (empty? @features)
       [:> View {:style {:flex 1}}
        [:> MapView {:initialRegion (merge (latlng (:coordinates (:city/geometry city)))
                                           {:latitudeDelta 0.02,
                                            :longitudeDelta 0.02})
                     :showsUserLocation true :style {:flex 1}
                     :showsMyLocationButton true}
          (when goal
            [:> MapMarker {:title (:text goal)
                           :coordinate (latlng (:coordinates (:geometry goal)))
                           :description (str/join ", " (map :text (:context goal)))}])
          (when route
            (let [path (map latlng (:coordinates (:geometry (first (:routes route)))))]
              [:> MapPolyline {:coordinates path
                               :strokeColor "#3bb2d0" ;; light
                               :strokeWidth 4}]))]
        (when goal
          [:> Button {:full true :on-press #((:navigate (:navigation props)) "directions")}
           [:> Icon {:name "information-circle" :transparent true}]
           [:> Text (:text goal)]])]
       [els/places @features])]))

(defn settings
  [props]
  (let [cities @(rework/q! queries/cities)
        navigate (:navigate (:navigation props))]
    [:> Container
     [:> Header
       [:> Button {:transparent true :full true
                   :on-press #(navigate "DrawerToggle")}
        [:> Icon {:name "menu"}]]
       [:> Body [:> Title "Settings"]]]
     [:> Content
      (map els/city-selector cities (repeat props))]]))
