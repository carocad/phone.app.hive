(ns hive.core
  (:require [reagent.core :as r]
            [hive.foreigns :as fl]
            [hive.state :as state]
            [hive.services.location :as position]
            [hive.services.raw.location :as location]
            [hive.components.navigation :as nav]
            [hive.rework.core :as work :refer-macros [go-try <?]]
            [datascript.core :as data]
            [hive.services.store :as store]
            [hive.queries :as queries]
            [hive.rework.util :as tool]
            [hive.components.screens.home.core :as home]
            [hive.components.router :as router]
            [hive.components.screens.settings :as settings]
            [cljs.core.async :as async]
            [clojure.data]))

"Each Screen will receive two props:
 - screenProps - Extra props passed down from the router (rarely used)
 - navigation  - The main navigation functions in a map as follows:
   {:state     - routing state for this screen
    :dispatch  - generic dispatch fn
    :goBack    - pop's the current screen off the stack
    :navigate  - most common way to navigate to the next screen
    :setParams - used to change the params for the current screen}"

(defn root-ui []
  (let [Root     (nav/drawer-navigator {:Home {:screen home/Screen}
                                        :Settings {:screen settings/Screen}}
                                       {})]
    [router/router {:root Root :init "Home"}]))

(defn reload-config!
  "takes a sequence of keys and attempts to read them from LocalStorage.
  Returns a channel with a transaction or Error"
  [ks]
  (let [data (store/load! ks)
        c    (async/chan 1 (comp (map (tool/validate not-empty ::missing-data))
                                 tool/bypass-error
                                 (map (work/inject :user/id queries/user-id))
                                 (map vector)))]
    (async/pipe data c)))

;; TODO: for some reason, the app seems to not exit directly through
;; the back button but keep removing some stacks? I think it might be
;; related to the DrawerToggle
(defn back-listener []
  (let [r @(work/q! router/data-query)
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
        data   (cons {:app/session (data/squuid)}
                     state/init-data)
        w      (location/watch! position/defaults) ;; displays Toast on error
        config (reload-config! [:user/city])
        report (async/chan 1 (comp (filter tool/error?)
                                   (map #(fl/toast! (ex-message %)))))]
    (work/init! conn)
    (work/transact! data)
    (.registerComponent fl/app-registry "main" #(r/reactify-component root-ui))
    ;; handles Android BackButton
    ((:addEventListener fl/back-handler) "hardwareBackPress"
      back-listener)
    (let [default (work/inject state/defaults :user/id queries/user-id)
          tx      (async/merge [config (async/to-chan [[default]])])]
      (async/pipe w report)
      (work/transact-chan tx (remove tool/error?)))))

;(async/take! (store/delete! [:user/city]) println)

;; this also works but it is not as clear
;(async/take! (location/watch! {::location/enableHighAccuracy true
;                               ::location/timeInterval 3000})
;             cljs.pprint/pprint)

;hive.rework.state/conn


;; FOOD FOR THOUGHT
;; a possible way of synchronizing the entire datascript option would
;; be to create a serializer which takes the datascript content as datoms,
;; enumerates and stores them using the number as key and the datom as value.
;; This however would be very inneficient, therefore it would be best
;; to throtle it to say every 15 seconds or so.
;; Furthermore, storing the complete state again would be very inneficient
;; so a complete diff of the two states should be performed and only those
;; that changed should be stored.
;; Just to avoid mixing old datoms with recently removed datoms, a remove until
;; exception is found should be executed as well.
;; To restore the datoms, simply pass (range) and stop taking elements when a
;; read fails
;; Did I forget anything there?
;; PS: simply use add-watch to the datascript conn
