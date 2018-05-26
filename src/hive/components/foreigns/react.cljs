(ns hive.components.foreigns.react
  (:require [hive.foreigns :as fl]
            [oops.core :as oops]))

(def View   (oops/oget fl/ReactNative "View"))
(def Image  (oops/oget fl/ReactNative "Image"))
(def Modal  (oops/oget fl/ReactNative "Modal"))
(def Text   (oops/oget fl/ReactNative "Text"))
(def Button (oops/oget fl/ReactNative "Button"))
(def TouchableOpacity (oops/oget fl/ReactNative "TouchableOpacity"))
(def TouchableHighlight (oops/oget fl/ReactNative "TouchableHighlight"))
(def ScrollView (oops/oget fl/ReactNative "ScrollView"))
