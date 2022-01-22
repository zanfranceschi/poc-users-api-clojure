(ns poc-users-api.dev
  (:require [clojure.pprint :refer [pprint]]
            [poc-users-api.server :as local-server]
            [poc-users-api.database :as db]
            [datomic.api :as d]
            [schema.core :as s]
            [clojure.data.json :as json]
            [io.pedestal.http :as server]
            [clojure.string :refer [blank?]]))




;; for development... start and stop server
(def serv (local-server/run-dev))

(server/stop serv)

(defn string-not-blank? [v]
  (not (blank? v))
  )

(def StringNotEmpty (s/pred string-not-blank? 'string-not-blank?))

(def CustomSchema {:username StringNotEmpty})

(pprint (s/check CustomSchema {:username ""}))



(def xpto {:a 1 :b #(str "vc informou " %)})
(def xpto {:a 1 :b "ss"})

(-> (vals xpto)
    println )


(defn x [[key val]]
  (println (str "chave " key " / value " val))
  {key (str val)})

(into {} (map x xpto))

(json/write-str xpto)