(ns hive.core
  (:require [reagent.core :as r]
            [oops.core :as oops]
            [hive.foreigns :as fl]
            [hive.state :as state]
            [hive.services.location :as position]
            [hive.services.raw.location :as location]
            [hive.rework.core :as work]
            [hive.components.screens.home.welcome :as welcome]
            [datascript.core :as data]
            [hive.services.store :as store]
            [hive.queries :as queries]
            [hive.rework.util :as tool]
            [hive.components.screens.home.core :as home]
            [hive.components.router :as router]
            [hive.components.screens.settings.core :as settings]
            [hive.components.screens.settings.city-picker :as city-picker]
            [cljs.core.async :as async]
            [cljs-react-navigation.reagent :as rn-nav]
            [hive.components.screens.home.route :as route]))

"Each Screen will receive two props:
 - screenProps - Extra props passed down from the router (rarely used)
 - navigation  - The main navigation functions in a map as follows:
   {:state     - routing state for this screen
    :dispatch  - generic dispatch fn
    :goBack    - pop's the current screen off the stack
    :navigate  - most common way to navigate to the next screen
    :setParams - used to change the params for the current screen}"

(defn RootUi []
  (let [Navigator     (rn-nav/stack-navigator
                        {:home           {:screen home/Screen}
                         :welcome        {:screen welcome/Screen}
                         :directions     {:screen route/Screen}
                         :settings       {:screen settings/Screen}
                         :select-city    {:screen city-picker/Screen}
                         :location-error {:screen home/LocationError}}
                        {:headerMode "none"})]
    ;id       @(work/q! queries/user-id)]
    ;(if (= -1 id) ;; default
    ; [router/router {:root Navigator :init "welcome"}]
    [router/Router {:root Navigator :init "home"}]))

(defn- reload-config!
  "takes a sequence of keys and attempts to read them from LocalStorage.
  Returns a channel with a transaction or Error"
  [ks]
  (let [data (store/load! ks)
        c    (async/chan 1 (comp (map (tool/validate not-empty ::missing-data))
                                 tool/bypass-error
                                 (map (work/inject :user/id queries/user-id))))]
    (async/pipe data c)))

(defn- back-listener
  "a generic Android back button listener which pops the last element from the
  navigation stack or exists otherwise.

  Note: for Component specific back listeners it might be necessary to unsubscribe
  this listener and subscribe your own for the lifecycle of the UI component.
  See `with-let` https://reagent-project.github.io/news/news060-alpha.html"
  []
  (let [r  (work/q router/data-query)
        tx (delay (router/goBack r))]
    (cond
      (nil? (second r))
      false ;; no router initialized, Exit

      (= (tool/keywordize (first r))
         (tool/keywordize (:react.navigation/state (first @tx))))
      false ;; nothing to go back to, Exit

      :else (do (work/transact! @tx) true))))

(defn init!
  "register the main UI component in React Native"
  [] ;; todo: add https://github.com/reagent-project/historian
  (let [conn   (data/create-conn state/schema)
        data   (cons {:session/uuid (data/squuid)
                      :session/start (js/Date.now)}
                     state/init-data)
        config (reload-config! [:user/city])]
    (work/init! conn)
    (work/transact! data)
    (location/watch! position/defaults)
    (oops/ocall fl/Expo "registerRootComponent" (r/reactify-component RootUi))
    ;; handles Android BackButton
    (oops/ocall fl/ReactNative "BackHandler.addEventListener"
                "hardwareBackPress"
                back-listener)
    (let [default (work/inject state/defaults :user/id queries/user-id)
          tx      (async/into [default] config)]
      (work/transact! tx))))

;(async/take! (store/delete! [:user/city]) println)

;; this also works but it is not as clear
;(async/take! (location/watch! {::location/enableHighAccuracy true
;                               ::location/timeInterval 3000})
;             cljs.pprint/pprint)

;hive.rework.state/conn
