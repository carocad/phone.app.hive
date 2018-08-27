(ns hive.components.screens.settings.core
  (:require [hive.rework.core :as work]
            [hive.components.foreigns.react :as react]
            [hive.components.symbols :as symbols]
            [hive.components.foreigns.expo :as expo]
            [hive.queries :as queries]
            [reagent.core :as r]
            [cljs.spec.alpha :as s]
            [hive.services.secure-store :as secure]
            [hive.services.firebase :as firebase]))

(s/def ::email (s/and string? #(re-matches #"\S+@\S+\.\S+" %)))
(s/def ::password (s/and string? #(>= (count %) 8)))

(defn- UserIcon
  []
  [:> react/View {:width 50 :height 50 :margin 5 :alignItems "center"
                  :justifyContent "center" :backgroundColor "lightgray"
                  :borderRadius 50/2}
    [:> expo/Ionicons {:name "ios-person-outline" :size 30}]])

(defn- EmailInput
  [user stage]
  (let [info (work/pull! [:user/email] user)]
    [:> react/View
      (if (= :password @stage)
        [:> react/Text (:user/email @info)]
        [:> react/Input {:placeholder "example@domain.com" :textAlign "center"
                         :defaultValue (:user/email @info)
                         :autoCapitalize "none" :returnKeyType "next"
                         :keyboardType "email-address"
                         :onSubmitEditing #(when (s/valid? ::email (:user/email @info))
                                             (reset! stage :password))
                         :onChangeText #(work/transact! [{:db/id user :user/email %}])
                         :style {:width 150 :height 50}}])
      (cond
        (empty? (:user/email @info)) nil

        (not (s/valid? ::email (:user/email @info)))
        [:> react/Text {:style {:color "coral"}} "invalid email"])]))

(defn- on-sign-up
  [data db]
  (when (s/valid? ::password (:user/password data))
        [[firebase/sign-in-or-up! db]
         [secure/save! (select-keys data [:user/password])]]))

(defn- PasswordInput
  [user]
  (let [info (work/pull! [:user/password] user)]
    [:> react/View
      [:> react/Input {:placeholder     "secret password" :textAlign "center"
                       :autoCapitalize  "none" :returnKeyType "send"
                       :onChangeText    #(work/transact! [{:db/id user :user/password %}])
                       :onSubmitEditing #(work/transact! (on-sign-up @info (work/db)))
                       :style           {:width 150 :height 50}}]
      (cond
        (empty? (:user/password @info)) nil

        (not (s/valid? ::password (:user/password @info)))
        [:> react/Text {:style {:color "coral"}} "password is too short"])]))

(defn SignUp
  [user]
  (r/with-let [info (work/pull! [:user/email :user/password] user)
               stage (r/atom :email)]
    [:> react/View {:backgroundColor "rgba(0,0,0,0.5)" :flex 1
                    :alignItems "center" :justifyContent "center"}
      [:> react/View {:backgroundColor "white" :height 300 :width 300
                      :alignItems "center" :justifyContent "space-around"
                      :borderRadius 5}
        [UserIcon]
        [EmailInput user stage]
        (when (= :password @stage)
          [PasswordInput user])
        (when (s/valid? ::password (:user/password @info))
          [:> react/TouchableOpacity {:onPress #(work/transact! (on-sign-up @info (work/db)))}
            [:> expo/Ionicons {:name "ios-checkmark" :size 26}]])]]))

(defn- UserInfo
  [user]
  (let [info     (work/pull! [:user/displayName :user/isAnonymous :user/email]
                             user)
        visible? (r/atom false)]
    (fn []
      [:> react/TouchableOpacity {:style {:height 125 :paddingBottom 20 :paddingTop 20}
                                  :onPress #(reset! visible? true)}
        (when (:user/isAnonymous @info)
          [:> react/Modal {:animationType "slide" :presentationStyle "overFullScreen"
                           :transparent true :visible @visible?
                           :onRequestClose #(reset! visible? false)}
           [SignUp user]])
        [:> react/Text {:style {:color "slategray" :fontSize 15
                                :paddingLeft 20 :justifyContent "flex-end"}}
         "USER INFO"]
        [:> react/View {:height 50 :flex-direction "row"}
          [UserIcon]
          [:> react/View {:flex 0.7 :justifyContent "center"}
           (cond
             (:user/isAnonymous @info)
             [:> react/Text "ANONYMOUS"]

             (:user/displayName @info)
             [:> react/Text (:user/displayName @info)])
           (when (not (:user/isAnonymous @info))
             [:> react/Text {:style {:color "gray"}}
              (:user/email @info)])]]])))

(defn- UserCity
  [props user]
  (let [info (work/pull! [{:user/city [:city/name :city/region :city/country]}]
                         user)
        navigate (:navigate (:navigation props))]
    [:> react/View {:height 125}
     [:> react/Text {:style {:color "slategray" :fontSize 15 :paddingLeft 20
                             :justifyContent "flex-end"}}
      "CURRENT CITY"]
     [:> react/TouchableOpacity {:onPress #(navigate "select-city")
                                 :style {:height 45}}
      [symbols/PointOfInterest
       [:> expo/Ionicons {:name "md-map" :size 26}]
       [:> react/Text ""]
       [:> react/Text (:city/name (:user/city @info))]
       [:> react/Text {:style {:color "gray"}}
        (str (:city/region (:user/city @info)) ", "
             (:city/country (:user/city @info)))]
       [:> expo/Ionicons {:name "ios-checkmark" :size 26}]]]]))

;; NOTE: here we use the entity and not the uid because the sign in
;; process might change it, thus breaking all pull patterns
(defn Settings
  [props]
  (let [user (work/q! queries/user-entity)]
    [:> react/View {:flex 1}
    ;; HEADER .....................
      [:> react/View {:height 60 :alignItems "center" :justifyContent "center"
                      :backgroundColor "blue"}
        [:> react/Text {:style {:color "white" :fontSize 20}}
                       "Settings"]]
    ;; USER INFO ...................
      [UserInfo @user]
    ;; USER CITY .................
      [UserCity props @user]]))

;hive.rework.state/conn
