(defproject hive "0.1.0-SNAPSHOT"
  :description "Your go-to routing app for public transport"
  :url "http://example.com/FIXME"
  :license {:name "LGPL v3"
            :url  "https://choosealicense.com/licenses/gpl-3.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [react-native-externs "0.1.0"]
                 [org.clojure/core.async "0.3.465"]
                 [reagent "0.7.0" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server cljsjs/create-react-class]]
                 [datascript "0.16.2"]
                 [cljs-react-navigation "0.1.1"]
                 [hiposfer/geojson.specs "0.2.0"]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.11"]]
  :clean-targets ["target/" "main.js"]
  :aliases {"figwheel"        ["run" "-m" "user" "--figwheel"]
            ; TODO: Remove custom extern inference as it's unreliable
            ;"externs"         ["do" "clean"
            ;                   ["run" "-m" "externs"]]
            "rebuild-modules" ["run" "-m" "user" "--rebuild-modules"]
            "prod-build"      ^{:doc "Recompile code with prod profile."}
                              ["with-profile" "prod" "cljsbuild" "once" "main"]}
  :profiles {:dev  {:dependencies [[figwheel-sidecar "0.5.10"]
                                   [com.cemerick/piggieback "0.2.1"]
                                   [expound "0.3.4"]
                                   [org.clojure/test.check "0.9.0"]]
                    :source-paths ["src" "env/dev"]
                    :cljsbuild    {:builds [{:id           "main"
                                             :source-paths ["src" "env/dev"]
                                             :figwheel     true
                                             :compiler     {:output-to     "target/not-used.js"
                                                            :main          "env.main"
                                                            :output-dir    "target"
                                                            :optimizations :none}}]}
                    :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :prod {:cljsbuild {:builds [{:id           "main"
                                          :source-paths ["src" "env/prod"]
                                          :compiler     {:output-to          "main.js"
                                                         :main               "env.main"
                                                         :output-dir         "target"
                                                         :static-fns         true
                                                         :externs            ["js/externs.js"]
                                                         :infer-externs      true
                                                         :parallel-build     true
                                                         :optimize-constants true
                                                         :optimizations      :advanced
                                                         :closure-defines    {"goog.DEBUG" false}}}]}}})
