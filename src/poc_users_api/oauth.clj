(ns poc-users-api.oauth
  (:require [no.nsd.clj-jwt :as clj-jwt]
            [io.pedestal.interceptor.helpers :as interceptor]))


(defn- unauthorized [text]
  {:status  401
   :headers {}
   :body    {:error text}})


(defn decode-jwt [{:keys [required? jwk-endpoint]}]
  (interceptor/before
   ::decode-jwt
   (fn [ctx]
     (if-let [auth-header (get-in ctx [:request :headers "authorization"])]
       (try (->> auth-header
                 (clj-jwt/unsign jwk-endpoint)
                 (assoc-in ctx [:request :claims]))

            (catch Exception _
              (assoc ctx :response (unauthorized "The token provided is not valid"))))

       (if required? (assoc ctx :response (unauthorized "Token not provided"))
           (assoc-in ctx [:request :claims] {}))))))