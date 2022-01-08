(ns poc-users-api.database
  (:require [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [environ.core :refer [env]]))

(def user-schema [;{:db/ident :user/id
                   ;:db/unique :db.unique/identity
                   ;:db/valueType :db.type/uuid
                   ;:db/cardinality :db.cardinality/one
                   ;;:db/doc "User id"}

                  {:db/ident :user/username
                   :db/unique :db.unique/identity
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc "User username"}

                  {:db/ident :user/name
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc "User name"}

                  {:db/ident :user/active
                   :db/valueType :db.type/boolean
                   :db/cardinality :db.cardinality/one
                   :db/doc "User active"}

                  {:db/ident :user/deleted
                   :db/valueType :db.type/boolean
                   :db/cardinality :db.cardinality/one
                   :db/doc "User 'exists' (deleted or not)"}

                  {:db/ident :user/tags
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/many
                   :db/doc "User tags"}])

(def db-uri (:database-uri env))

(defn- db-admin-tx-schema []
  (d/create-database db-uri))
(let [conn (d/connect db-uri)
      db (d/db conn)]
  (d/transact conn user-schema))


(defn user-get!
  "Gets an user by username"
  [db username]
  (let [db-user (ffirst (d/q '[:find (pull ?e [*])
                               :in $ ?username
                               :where
                               [?e :user/username ?username]
                               [?e :user/deleted false]] db username))]
    db-user))
    

(defn users-get-filter
  [filter name username]
  (re-find (re-pattern (str "(?i)" filter)) (str name username)))

(defn users-get!
  "Gets users by filtering username/name"
  [db filter]
  (let [db-users (d/q '[:find (pull ?e [*])
                        :in $ ?filter
                        :where
                        [?e :user/username ?username]
                        [?e :user/name ?name]
                        [?e :user/deleted false]
                        [(poc-users-api.database/users-get-filter ?filter ?name ?username)]] db (or filter ""))]
    db-users))

(defn user-transact!
  "Adds a new user"
  [conn db-user]
  (let [fut (d/transact conn [db-user])]
    fut))


(defn user-set-deleted->true!
  "Updates an user to :deleted true"
  [conn username]
  (let [fut (d/transact conn [[:db/add [:user/username username] :user/deleted true]])]
    fut))

(defn user-retract-all-tags!
  [conn username]
  (let [fut (d/transact conn [[:db/retract [:user/username username] :user/tags]])]
    fut))