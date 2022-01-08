(ns poc-users-api.service
  (:require [clojure.pprint :refer [pprint]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.http.body-params :as body-params]
            [poc-users-api.oauth :as oauth]
            [poc-users-api.pipeline :as pipeline]
            [environ.core :refer [env]]
            [clojure.data.json :as json]))



(defn- user->user-http [user]
  ;; if you decide go HATEOAS, uncomment below :)
  ;(assoc user :_links {:self (str "/users/" (:username user))})
  user
  )

(defn users-put
  [request]
  (let [payload   (:json-params request)
        username (get-in request [:path-params :id])
        conn      (get-in request [:database :conn])
        db        (get-in request [:database :db])
        op-result (pipeline/user-update conn db username payload)]

    (if (:ok op-result)

      {:status 204}

      (let [exception? (:exception op-result)
            message    (:message op-result)
            details    (:details op-result)]
        (if exception?

          {:status 500
           :body   {:error   message
                    :details details}}

          (if (:user-exists op-result)
            
            {:status 422
             :body   {:error   message
                      :details details}}
            
            {:status 404
             :body   {:error   message
                      :details details}}
            )
          )))))

(defn users-delete
  [request]
  (let [username  (get-in request [:path-params :id])
        conn      (get-in request [:database :conn])
        db        (get-in request [:database :db])
        op-result (pipeline/user-delete conn db username)]

    (if (:ok op-result)

      {:status 204}

      (let [exception? (:exception op-result)
            message    (:message op-result)
            details    (:details op-result)]
        (if exception?

          {:status 500
           :body   {:error   message
                    :details details}}

          (if (:user-exists op-result)

            {:status 422
             :body   {:error   message
                      :details details}}

            {:status 404
             :body   {:error   message
                      :details details}}))))))

(defn users-post
  [request]
  (let [payload   (:json-params request)
        username  (:username payload)
        conn      (get-in request [:database :conn])
        db        (get-in request [:database :db])
        op-result (pipeline/user-create conn db payload)]
    
    (if (:ok op-result)

      {:status  201
       :body    {:message (:message op-result)}
       :headers {"Location" (route/url-for :users-get-one :params {:id username})}}

      (let [exception? (:exception op-result)
            message    (:message op-result)
            details    (:details op-result)]
        (if exception?

          {:status 500
           :body   {:error   message
                    :details details}}

          {:status 422
           :body   {:error   message
                    :details details}})))))

(defn users-get-one
  [request]
  (let [database (get-in request [:database :db])
        username (get-in request [:path-params :id])
        user (pipeline/user-get-one database username)]
    (if user
      {:status 200
       :body   (user->user-http user)}
      {:status 404
       :body   {:error (str "No user with username '" username "' found")}})))


(defn users-get-list
  [request]
  (let [database (get-in request [:database :db])
        q (get-in request [:query-params :q])
        users (pipeline/users-get-list database q)
        users-http (map user->user-http users)
        users-count (count users)]
    {:status 200
     :body   {:total  users-count
              :result users-http}}))


(defn- json-response
  [status body]
  {:status status
   :headers {"Content-Type" "application/json;charset=utf-8"}
   :body (json/write-str body)})

(def service-error-handler
  (error-int/error-dispatch [ctx ex]

                            [{:exception-type :com.fasterxml.jackson.core.JsonParseException}]
                            (assoc ctx :response (json-response 400 {:error "Malformed JSON, yo. Fix that shit, pls."}))

                            :else
                            (assoc ctx :response (json-response 500 {:error "Mmm, some shit has happend and I'm really sorry :|"}))
                            ))

(def common-interceptors-public [
                                 service-error-handler
                                 pipeline/database-interceptor
                                 (body-params/body-params)
                                 http/json-body
                                 ])


(def jwk-endpoint (:jwk-endpoint env))

(def common-interceptors-protected (conj common-interceptors-public (oauth/decode-jwt {:required?    true
                                                                                       :jwk-endpoint jwk-endpoint})))

(defn home-interceptor [request]
  {:status 200
   :body   {:intc "home"
            :url (route/url-for
                  :users-get-one :params {:id "1"})}})
  

(defn debug-interceptor [request]
  {:status 200
   :body   {:intc "debug"
            :url (route/url-for
                  :users-get-one :params {:id "1"})}})

(def routes #{
              ["/debug" :get debug-interceptor :route-name :debug]
              ["/" :get home-interceptor :route-name :home]
              ["/users" :post (conj common-interceptors-protected `users-post) :route-name :users-post]
              ["/users" :get (conj common-interceptors-protected `users-get-list) :route-name :users-get-list]
              ["/users/:id" :get (conj common-interceptors-protected `users-get-one) :route-name :users-get-one]
              ["/users/:id" :put (conj common-interceptors-protected `users-put) :route-name :users-put]
              ["/users/:id" :delete (conj common-interceptors-protected `users-delete) :route-name :users-delete]
              })
              


;; Consumed by poc-users-api.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 9999
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        
