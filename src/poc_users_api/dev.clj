(ns poc-users-api.dev
  (:require [clojure.pprint :refer [pprint]]
            [poc-users-api.server :as local-server]
            [poc-users-api.database :as db]
            [datomic.api :as d]
            [io.pedestal.http :as server]))




;; for development... start and stop server
(def serv (local-server/run-dev))

(server/stop serv)



