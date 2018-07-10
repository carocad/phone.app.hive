(ns user
  (:require [figwheel-sidecar.repl-api :as ra]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [hawk.core :as hawk]
            [clojure.tools.reader.edn :as edn])
  (:import (java.net NetworkInterface InetAddress Inet4Address)
           (java.io File)))
;; This namespace is loaded automatically by nREPL

(defn enable-source-maps
  "patch the metro packager to use Clojurescript source maps"
  []
  (let [path "node_modules/metro/src/Server/index.js"
        content (slurp path)]
    (spit path (str/replace content #"match\(\/\\.map\$\/\)"
                                    "match(/main\\.*\\\\.map\\$/)"))
    (println "Source maps enabled.")))

(defn write-main-js
  "create a fake main.js file to make the metro packager happy"
  []
  (spit "main.js"
    "'use strict';
    // cljsbuild adds a preamble mentioning goog so hack around it
    window.goog = {
      provide: function() {},
      require: function() {},
      };
    require('./target/expo/env/index.js');"))

(defn get-expo-settings []
  (try
    (let [settings (-> (slurp ".expo/settings.json") json/read-str)]
      settings)
    (catch Exception _
      nil)))

(def ip-validator #"\d+\.\d+\.\d+\.\d+")

(defn- linux-ip
  "attempts to retrieve the ip on linux OS"
  []
  (try
    (-> (Runtime/getRuntime)
        (.exec "ip route get 8.8.8.8 | head -n 1 | tr -s ' ' | cut -d ' ' -f 7")
        (.getInputStream)
        (slurp)
        (str/trim-newline)
        (re-matches ip-validator))
    (catch Exception _
      nil)))

(defn- standard-ip
  "attemps to check the lan ip through the Java API"
  []
  (cond
    (some #{(System/getProperty "os.name")} ["Mac OS X" "Windows 10"])
    (.getHostAddress (InetAddress/getLocalHost))

    :else
    (->> (NetworkInterface/getNetworkInterfaces)
         (enumeration-seq)
         (filter #(not (or (str/starts-with? (.getName %) "docker")
                           (str/starts-with? (.getName %) "br-"))))
         (map #(.getInterfaceAddresses %))
         (map
           (fn [ip]
             (seq (filter #(instance?
                             Inet4Address
                             (.getAddress %))
                          ip))))
         (remove nil?)
         (first)
         (filter #(instance?
                    Inet4Address
                    (.getAddress %)))
         (first)
         (.getAddress)
         (.getHostAddress))))

(defn get-lan-ip
  "fetch the ip of the computer that is available for expo app to communicate"
  []
  (let [lip (linux-ip)
        sip (standard-ip)
        ip  (or lip sip)]
    (println "using ip:" ip)
    ip))

(defn get-expo-ip []
  (if-let [expo-settings (get-expo-settings)]
    (case (get expo-settings "hostType")
      "lan" (get-lan-ip)
      "localhost" "localhost"
      "tunnel" (throw (Exception. "Expo Setting tunnel doesn't work with figwheel.  Please set to LAN or Localhost.")))
    "localhost"))                                         ;; default

(defn write-env-dev
  "First check the .expo/settings.json file to see what host is specified.  Then set the appropriate IP."
  []
  (let [hostname (.getHostName (InetAddress/getLocalHost))
        ip (get-expo-ip)]
    (-> "
    (ns env.dev)

    (def hostname \"%s\")
    (def ip \"%s\")"
        (format
          hostname
          ip)
        ((partial spit "env/dev/env/dev.cljs")))))

(defn rebuild-env-index
  "prebuild the set of files that the metro packager requires in advance"
  [js-modules]
  (let [devHost (get-expo-ip)
        modules (->> (file-seq (io/file "assets"))
                     (filter #(and (not (re-find #"DS_Store" (str %)))
                                   (.isFile %)))
                     (map (fn [file] (when-let [unix-path (->> file .toPath .iterator iterator-seq (str/join "/"))]
                                       (str "../../" unix-path))))
                     (concat js-modules ["react" "react-native" "expo" "create-react-class"])
                     (distinct))
        modules-map (zipmap
                      (->> modules
                           (map #(str "\""
                                      (if (str/starts-with? % "../../assets")
                                        (-> %
                                            (str/replace "../../" "./")
                                            (str/replace "\\" "/")
                                            (str/replace "@2x" "")
                                            (str/replace "@3x" ""))
                                        %)
                                      "\"")))
                      (->> modules
                           (map #(format "(js/require \"%s\")"
                                         (-> %
                                             (str/replace "../../" "../../../")
                                             (str/replace "\\" "/")
                                             (str/replace "@2x" "")
                                             (str/replace "@3x" ""))))))]
    (try
      (-> "
      (ns env.index
        (:require [env.dev :as dev]))

      ;; undo main.js goog preamble hack
      (set! js/window.goog js/undefined)

      (-> (js/require \"figwheel-bridge\")
          (.withModules %s)
          (.start \"main\" \"expo\" \"%s\"))"
          (format
            (str "#js " (with-out-str (println modules-map)))
            devHost)
          ((partial spit "env/dev/env/index.cljs")))

      (catch Exception e
        (println "Error: " e)))))

(defn- required-modules
  "returns a vector of string with the names of the imported modules. Ignoring those
  that are commented out"
  [file-content]
  (some->> file-content
           (re-seq #"(?m)^[^;\n]+?\(js/require \"([^\"]+)\"\)")
           (map last)
           (vec)))

;; Each file maybe corresponds to multiple modules.
(defn watch-for-external-modules
  []
  (let [path ".js-modules.edn"]
    (hawk/watch! [{:paths   ["src"]
                   :filter  hawk/file?
                   :handler (fn [ctx {:keys [kind file] :as event}]
                              (let [m (edn/read-string (slurp path))
                                    file-name (-> (.getPath file)
                                                  (str/replace (str (System/getProperty "user.dir") "/") ""))]

                                  ;; file is deleted
                                (when (= :delete kind)
                                  (let [new-m (dissoc m file-name)]
                                    (spit path new-m)
                                    (rebuild-env-index (flatten (vals new-m)))))

                                (when (.exists file)
                                  (let [content (slurp file)
                                        js-modules (required-modules content)]
                                    (let [old-js-modules (get m file-name)]
                                      (when (not= old-js-modules js-modules)
                                        (let [new-m (if (seq js-modules)
                                                      (assoc m file-name js-modules)
                                                      (dissoc m file-name))]
                                          (spit path new-m)

                                          (rebuild-env-index (flatten (vals new-m)))))))))
                              ctx)}])))

(defn rebuild-modules
  []
  (let [path ".js-modules.edn"
        m (atom {})]
      ;; delete path
    (when (.exists (File. path))
      (clojure.java.io/delete-file path))

    (doseq [file (file-seq (File. "src"))]
      (when (.isFile file)
        (let [file-name (-> (.getPath file)
                            (str/replace (str (System/getProperty "user.dir") "/") ""))
              content (slurp file)
              js-modules (required-modules content)]
          (if js-modules
            (swap! m assoc file-name (vec js-modules))))))
    (spit path @m)
    (rebuild-env-index (flatten (vals @m)))))

  ;; Lein
(defn start-figwheel
  "Start figwheel for one or more builds"
  [];& build-ids]
  (rebuild-modules)
  (enable-source-maps)
  (write-main-js)
  (write-env-dev)
  (watch-for-external-modules)
  (ra/start-figwheel! "main")
  (ra/cljs-repl))

(defn stop-figwheel
  "Stops figwheel"
  []
  (ra/stop-figwheel!))

(defn -main
  [args]
  (case args
    "--figwheel"
    (start-figwheel)

    "--rebuild-modules"
    (rebuild-modules)

    (prn "You can run lein figwheel or lein rebuild-modules.")))
