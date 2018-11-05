(ns hive.core
  (:require [reagent.core :as r]
            [expo :as Expo]
            [react-native :as React]
            [hive.state.core :as state]
            [hive.services.firebase :as firebase]
            [datascript.core :as data]
            [hive.services.sqlite :as sqlite]
            [hive.state.queries :as queries]
            [hive.utils.miscelaneous :as misc]
            [hive.screens.home.core :as home]
            [hive.screens.home.gtfs :as gtfs]
            [hive.screens.errors :as errors]
            [hive.screens.router :as router]
            [hive.screens.settings.core :as settings]
            [hive.screens.settings.city-picker :as city-picker]
            [cljs-react-navigation.reagent :as rn-nav]
            [hive.screens.home.route :as route]
            [hive.services.secure-store :as secure]
            [hiposfer.rata.core :as rata]))

(defn- MessageTray
  [props]
  (let [id      @(state/q! queries/session)
        alert   @(state/pull! [:session/alert]
                             [:session/uuid id])]
    (when-not (empty? (:session/alert alert))
      [:> React/View {:flex 1 :justifyContent "flex-end" :alignItems "center"
                      :bottom 0 :width "100%" :height "5%" :position "absolute"}
        [:> React/Text
          {:style {:width "100%" :height "100%" :textAlign "center"
                   :backgroundColor "grey" :color "white"}
           :onPress #(state/transact! [{:session/uuid id :session/alert {}}])}
          (:session/alert alert)]])))

(defn- screenify
  [component props]
  (rn-nav/stack-screen
    (fn [props]
      [:> React/View {:flex 1 :alignItems "stretch"}
        [component props]
        [MessageTray props]])
    props))

(defn RootUi []
  "Each Screen will receive two props:
   - screenProps - Extra props passed down from the router (rarely used)
   - navigation  - The main navigation functions in a map as follows:
     {:state     - routing state for this screen
      :dispatch  - generic dispatch fn
      :goBack    - pop's the current screen off the stack
      :navigate  - most common way to navigate to the next screen
      :setParams - used to change the params for the current screen}"
  (let [Navigator (rn-nav/stack-navigator
                    {:home           {:screen (screenify home/Home {:title "map"})}
                     :directions     {:screen (screenify route/Instructions {:title "directions"})}
                     :gtfs           {:screen (screenify gtfs/Data {:title "gtfs"})}
                     :settings       {:screen (screenify settings/Settings {:title "settings"})}
                     :select-city    {:screen (screenify city-picker/Selector {:title "Select City"})}
                     :location-error {:screen (screenify errors/UserLocation {:title "location-error"})}}
                    {:headerMode "none"})]
    [router/Router {:root Navigator :init "home"}]))

(defn- back-listener!
  "a generic Android back button listener which pops the last element from the
  navigation stack or exists otherwise.

  Note: for Component specific back listeners it might be necessary to unsubscribe
  this listener and subscribe your own for the lifecycle of the UI component.
  See `with-let` https://reagent-project.github.io/news/news060-alpha.html"
  []
  (let [r  (data/q router/data-query (state/db))
        tx (delay (router/goBack r))]
    (cond
      (nil? (second r))
      false ;; no router initialized, Exit

      (= (misc/keywordize (first r))
         (misc/keywordize (:react.navigation/state (first @tx))))
      false ;; nothing to go back to, Exit

      :else (some? (state/transact! @tx))))) ;; always returns true

(defn- internet-connection-listener
  "Listens to connection changes mainly for internet access."
  [connected]
  (let [sid @(state/q! queries/session)]
    (if-not connected
        (state/transact! [{:session/uuid sid :session/alert "You are offline."}]))))

(defn init!
  "register the main UI component in React Native"
  []
  (let [conn       (data/create-conn state/schema)
        config #js {:apiKey (:ENV/FIREBASE_API_KEY state/tokens)
                    :authDomain (:ENV/FIREBASE_AUTH_DOMAIN state/tokens)
                    :databaseUrl (:ENV/FIREBASE_DATABASE_URL state/tokens)
                    :storageBucket (:ENV/FIREBASE_STORAGE_BUCKET state/tokens)}]
    (println "hello World")
    (println (get @conn ::rata/ratom))
    (state/transact! [{:session/uuid (data/squuid)
                       :session/start (js/Date.now)}])
    ;; firebase related funcionality ...............
    (. firebase/ref (initializeApp config))
    ;; restore user data ...........................
    (.. (sqlite/CLEAR!!) ;; TODO: remove this
        (then #(sqlite/read!))
        (then state/transact!)
        ;; listen only AFTER restoration
        (then #(sqlite/listen! conn))
        (then #(state/transact! (state/init-data (state/db))))
        (then #(secure/load! [:user/password]))
        (then #(merge {:user/uid (data/q queries/user-id (state/db))} %))
        (then #(state/transact! [%]))
        (then #(firebase/sign-in! (state/db)))
        (then state/transact!))
    ;; start listening for events ..................
    (Expo/registerRootComponent (r/reactify-component RootUi))
    ;; handles Android BackButton
    (React/BackHandler.addEventListener "hardwareBackPress"
                                        back-listener!)
    (React/NetInfo.isConnected.addEventListener "connectionChange"
                                                internet-connection-listener)))

;(. (sqlite/read!) (then cljs.pprint/pprint))
;(. (sqlite/CLEAR!!) (then cljs.pprint/pprint))

;(. (secure/load! [:user/password]) (then cljs.pprint/pprint))
