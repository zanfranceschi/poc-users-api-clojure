(defproject poc-users-api "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  
  :plugins [[lein-environ "1.2.0"]]
  
  :repositories [["my.datomic.com" {:url   "https://my.datomic.com/repo"
                                    :creds :gpg}]
                 ["central"   {:url "https://nexus.nsd.no/repository/nsd-maven-public"}]
                 ["snapshots" {:url "https://nexus.nsd.no/repository/nsd-maven-public-snapshots"}]
                 ["releases"  {:url "https://nexus.nsd.no/repository/nsd-maven-public-releases"}]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.pedestal/pedestal.service "0.5.9"]
                 [io.pedestal/pedestal.jetty "0.5.9"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.26"]
                 [org.slf4j/jcl-over-slf4j "1.7.26"]
                 [org.slf4j/log4j-over-slf4j "1.7.26"]

                 [org.clojure/data.json "2.4.0"]
                 [com.datomic/datomic-pro "1.0.6344"]

                 [prismatic/schema "1.2.0"]

                  ;; Oauth2.0
                 [environ/environ "1.2.0"]
                 [no.nsd/clj-jwt "0.4.5"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev     {:aliases      {"run-dev" ["trampoline" "run" "-m" "poc-users-api.server/run-dev"]}
                       :dependencies [[io.pedestal/pedestal.service-tools "0.5.9"]]}
             :uberjar {:aot [poc-users-api.server]}}
  :main ^{:skip-aot true} poc-users-api.server)
