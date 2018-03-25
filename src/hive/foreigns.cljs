(ns hive.foreigns)

(def React       (js/require "react"))
(def Expo        (js/require "expo"))
(def ReactNative (js/require "react-native"))
(def NativeBase  (js/require "native-base"))
(def ReactNavigation (js/require "react-navigation"))

(def Location (js->clj (.-Location Expo)
                       :keywordize-keys true))
(def Constants (js->clj (.-Constants Expo)
                        :keywordize-keys true))
(def Permissions (js->clj (.-Permissions Expo)
                          :keywordize-keys true))
(def Platform (js->clj (.-Platform ReactNative)
                       :keywordize-keys true))
(def Store (js->clj (.-SecureStore Expo)
                    :keywordize-keys true))
(def IntentLauncherAndroid (js->clj (.-IntentLauncherAndroid Expo)
                                    :keywordize-keys true))
(def Keyboard (.-Keyboard ReactNative))

(def app-registry  (.-AppRegistry ReactNative))
(def back-handler  (js->clj (aget ReactNative "BackHandler")
                            :keywordize-keys true))
;(def async-storage (.-AsyncStorage ReactNative))
(def toast-android (js->clj (.-ToastAndroid ReactNative)
                            :keywordize-keys true))
(def dimensions    (.-Dimensions ReactNative))

(defn alert [title] (.alert (.-Alert ReactNative) title))

;; ------ images -----
(def thumb-sign (js/require "./assets/images/tb_sign2.png"))
(def thumb-run  (js/require "./assets/images/tbrun1.png"))

;; ----- config files ----

(def init-config (js->clj (js/require "./assets/init.json")
                   :keywordize-keys true))

;;; - - -- - - - - - -
(defn toast!
  ([text] (toast! text nil))
  ([text duration]
   ((:show toast-android) text (or duration (:SHORT toast-android)))))
