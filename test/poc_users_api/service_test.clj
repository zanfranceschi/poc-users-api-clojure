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

(let [deleted? (d/delete-database db-url)
      created? (d/create-database db-url)
      conn (d/connect db-url)
      db (d/db conn)
      schema-tx-future (d/transact conn db/user-schema)]
  (println "deleted and created db for testing?" (and deleted? created?))
  (println "transacted schema?" (not (nil? (:db-after @schema-tx-future))))
  (println "setup finished ─ all good to go!"))


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


(defn rand-username []
  (rand-str (max 1 (rand-int 32))))


(deftest happy-scenarios-test
  (let [username (rand-username)]

    (testing "User creation"
      (let [response (response-for service
                                   :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     "temp"
                                                       :active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))
            headers (:headers response)]
        
        (is (= (:status response) 201))
        (is (= (get headers "Location") (str "/users/" username)))
        (is (= decoded-body {"message" "User created."}))))

    (testing "User update"
      (let [response (response-for service
                                   :put (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     "temp2"
                                                       :active   true
                                                       :tags     ["c", "d"]}))
            body (:body response)]
        (is (= (:status response) 204))
        (is (= body ""))))
        

    (testing "Users consultation ─ one"
      (let [response (response-for service
                                   :get (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 200))
        (is (= decoded-body {"username" username
                             "name" "temp2"
                             "active" true
                             "tags" ["c" "d"]}))))
        
        

    (testing "Users consultation ─ list"
      (let [response (response-for service
                                   :get "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})
            decoded-body (json/decode (:body response))
            decoded-body-users (get decoded-body "result")]

        (is (= (:status response) 200))
        (is (contains? decoded-body "total"))
        (is (contains? decoded-body "result"))
        (is (= (first decoded-body-users) {"username" username
                                           "name" "temp2"
                                           "active" true
                                           "tags" ["c" "d"]}))))
    
    (testing "Users exlusion"
      (let [response (response-for service
                                   :delete (str "/users/" username)
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)})
            body (:body response)]
        (is (= (:status response) 204))
        (is (= body ""))))))


(deftest creation-validation-test
  (let [username (rand-username)]
    (testing "Invalid user creation - empty username"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username ""
                                                       :name     "X"
                                                       :active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"username" "invalid-value"}}))))
        
    
    (testing "Invalid user creation - wrong username type"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username 1
                                                       :name     "X"
                                                       :active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"username" "invalid-value"}})))
        
      )
    
    (testing "Invalid user creation - missing username"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:name     "X"
                                                       :active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"username" "missing-required-key"}}))))
    
    (testing "Invalid user creation - missing username and name"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"username" "missing-required-key"
                           "name" "missing-required-key"}}))))
    
    (testing "Invalid user creation - wrong name type"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     1
                                                       :active   true
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"name" "invalid-value"}})))
    
     (testing "Invalid user creation - missing active"
       (let [response (response-for service :post "/users"
                                    :headers {"Authorization" (str "Bearer " jwt-access-token)
                                              "Content-Type"  "application/json"}
                                    :body (json/encode {:username username
                                                        :name     "1"
                                                        :tags     ["a", "b"]}))
             decoded-body (json/decode (:body response))]
         
         (is (= (:status response) 422))
         (is (= decoded-body
                {"error" "Invalid format."
                 "details" {"active" "missing-required-key"}})))))
         
    
    (testing "Invalid user creation - wrong active type"
      (let [response (response-for service :post "/users"
                                   :headers {"Authorization" (str "Bearer " jwt-access-token)
                                             "Content-Type"  "application/json"}
                                   :body (json/encode {:username username
                                                       :name     "string"
                                                       :active   "true"
                                                       :tags     ["a", "b"]}))
            decoded-body (json/decode (:body response))]
        (is (= (:status response) 422))
        (is (= decoded-body
               {"error" "Invalid format."
                "details" {"active" "invalid-value"}}))))))
    
    
    
    