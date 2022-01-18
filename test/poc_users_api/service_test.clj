(ns poc-users-api.service-test
  (:use clojure.pprint)
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [poc-users-api.service :as service]
            [datomic.api :as d]
            [poc-users-api.database :as db]
            [cheshire.core :as json]
            [clj-http.client :as http-client]
            [environ.core :refer [env]]))
            
(println "-------------------------------")
(println (str "running tests for env '" (:env env) "'"))
(println "-------------------------------")

(def db-url (:database-uri env))
(d/delete-database db-url)

(let [deleted? (d/delete-database db-url)
      created? (d/create-database db-url)
      conn (d/connect db-url)
      db (d/db conn)
      schema-tx-future (d/transact conn db/user-schema)]
  (println "setup finished"))


(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(def jwt-access-token-response
    (http-client/request {:url (:jwt-get-token-url env)
                          :method "POST"
                          :content-type "application/json"
                          :body (json/encode {:client_id (:jwt-client-id env)
                                              :client_secret (:jwt-client-secret env)
                                              :audience (:jwt-audience env)
                                              :grant_type "client_credentials"})}))
(def jwt-access-token-response-body (json/decode (:body jwt-access-token-response)))
(def jwt-access-token (get jwt-access-token-response-body "access_token"))



(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))


(deftest happy-scenarios
  (let [username (rand-str (max 1 (rand-int 32)))]

    (testing "User creation"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     "temp"
                                                       :active   true
                                                       :tags     ["a", "b"]}))]
        ;(pprint response)
        (is (= (:status response) 201))))

    (testing "User update"
      (let [response (response-for service :put (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     "temp2"
                                                       :active   true
                                                       :tags     ["c", "d"]}))]
        ;(pprint response)
        (is (= (:status response) 204))))

    (testing "Users consultation ─ one"
      (let [response (response-for service :get (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})]
        ;(pprint response)
        (is (=
             (:status response)
             200))))

    (testing "Users consultation ─ list"
      (let [response (response-for service :get "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})]
        ;(pprint response)
        (is (=
             (:status response)
             200))))

    (testing "Users exlusion"
      (let [response (response-for service :delete (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})]
        ;(pprint response)
        (is (=
             (:status response)
             204))))))



