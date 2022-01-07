(ns poc-users-api.service-test
  (:use clojure.pprint)
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [poc-users-api.service :as service]
            [clojure.data.json :as json]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(deftest home-page-test
  (pprint (response-for service :get "/"))
  (is (=
       (:body (response-for service :get "/"))
       (json/write-str {:msg "Hello World!"})))
  (is (=
       (:status (response-for service :get "/"))
       200)))


(deftest about-page-test
  (pprint (response-for service :get "/about"))
  (is
   (re-find #"Clojure \d+\.\d+(\.\d+)?"
            (:body (response-for service :get "/about"))))
  (is (=
       (:status (response-for service :get "/about")) 200)))