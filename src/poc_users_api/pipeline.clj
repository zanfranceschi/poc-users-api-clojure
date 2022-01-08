(ns poc-users-api.pipeline
  (:require [clojure.pprint :refer [pprint]]
            [poc-users-api.database :as db]
            [datomic.api :as d]
            [schema.core :as s]))


;;================================================
;;                Conversions
;;================================================
(defn db-user->user [db-user]
  (if db-user

    {:username (:user/username db-user)
     :name     (:user/name db-user)
     :active   (:user/active db-user)
     :tags     (or (:user/tags db-user) [])}

    nil))

(defn user->db-user [user]
  (if user

    {:user/username (:username user)
     :user/name     (:name user)
     :user/active   (:active user)
     :user/tags     (or (:tags user) [])
     :user/deleted   false}

    nil))


;;================================================
;;             Database Interceptor
;;================================================
(def database-interceptor
  {:name :database-interceptor
   :enter (fn
            [context]
            (let [conn     (d/connect db/db-uri)
                  database (d/db conn)]
              (assoc-in context [:request :database] {:db   database
                                                      :conn conn})))})



;;================================================
;;             Users Creation Flow
;;================================================
(def UserCreationSchema
  "Validation for POST /users request"
  {:name                  s/Str
   :username              s/Str
   :active                s/Bool
   (s/optional-key :tags) [s/Str]
   s/Any                  s/Any})

(defn- validate-user-creation-payload
  [_ _ user]
  (if (nil? (s/check UserCreationSchema user))
    {:continue true}

    {:continue  false
     :ok        false
     :exception false
     :message   "Invalid format."
     :details   (or (s/check UserCreationSchema user) "Please, check types.")}))

(defn- validate-user-uniqueness
  [_ database user]
  (let [username             (:username user)
        user-already-exists? (db/user-get! database username)]
    (if user-already-exists?
      {:continue false
       :ok        false
       :exception false
       :message   "This username has been taken already. You cannot create an user with a duplicate username."
       :details   nil}
      {:continue true})))

(defn- persist-new-user
  [conn _ user]
  (try
    (let [db-user   (user->db-user user)
          tx-result (db/user-transact! conn db-user)]
      {:continue false
       :ok        true
       :exception false
       :message   "User created."
       :details   @tx-result})

    (catch Exception e
      {:continue false
       :ok        false
       :exception true
       :message   "Error creating user."
       :details   (ex-message e)})))

(defn user-create
  ([conn database user]
   (let [fns [validate-user-creation-payload
              validate-user-uniqueness
              persist-new-user]]
     (user-create conn database user fns)))
  ([conn database user fns]
  (let [fn (first fns)
         result (fn conn database user)
         continue? (:continue result)]
    (if-not continue?
      result
      (recur conn database user (drop 1 fns))))))



;;================================================
;;             Users Update Flow
;;================================================
(def UserUpdateSchema
  "Validation for PUT /users request"
  {:name                  s/Str
   :active                s/Bool
   (s/optional-key :tags) [s/Str]
   s/Any                  s/Any})

(defn- validate-user-update-payload
  [_ _ _ user]
  (if (nil? (s/check UserUpdateSchema user))
    {:continue true}
    
    {:continue  false
     :ok        false
     :user-exists nil
     :exception false
     :message   "Invalid format."
     :details   (or (s/check UserUpdateSchema user) "Please, check types.")}))

(defn- validate-user-existence
  ([_ database username]
   (validate-user-existence _ database username _))
  ([_ database username _]
  (let [user-exists? (db/user-get! database username)]
    (if-not user-exists?
      {:continue  false
       :ok        false
       :user-exists false
       :exception false
       :message   "This user does not exist."
       :details    nil}
      {:continue true}))))

(defn- persist-updated-user
  [conn _ username user]
  (try
    (let [user-with-username (assoc user :username username)
          db-user   (user->db-user user-with-username)
          _         (db/user-retract-all-tags! conn username)
          tx-result (db/user-transact! conn db-user)]
      {:continue  false
       :ok        true
       :user-exists true
       :exception false
       :message   "User updated."
       :details   @tx-result})

    (catch Exception e
      {:continue  false
       :ok        false
       :user-exists nil
       :exception true
       :message   "Error updating user."
       :details   (ex-message e)})))


(defn user-update
  ([conn database username user]
   (let [fns [validate-user-update-payload
              validate-user-existence
              persist-updated-user]]
     (user-update conn database username user fns)))
  ([conn database username user fns]
   (let [fn (first fns)
         result (fn conn database username user)
         continue? (:continue result)]
     (if-not continue?
       result
       (recur conn database username user (drop 1 fns))))))


;;================================================
;;             Users Delete Flow
;;================================================
(defn- persist-deleted-user
  [conn _ username]
  (try
    (let [tx-result (db/user-set-deleted->true! conn username)]
      {:continue    false
       :ok          true
       :user-exists true
       :exception   false
       :message     "User deleted."
       :details     @tx-result})

    (catch Exception e
      {:continue    false
       :ok          false
       :user-exists nil
       :exception   true
       :message     "Error deleting user."
       :details     (ex-message e)})))

(defn user-delete
  ([conn database username]
   (let [fns [validate-user-existence
              persist-deleted-user]]
     (user-delete conn database username fns)))
  ([conn database username fns]
   (let [fn (first fns)
         result (fn conn database username)
         continue? (:continue result)]
     (if-not continue?
       result
       (recur conn database username (drop 1 fns))))))


;;================================================
;;             User Get One Flow
;;================================================
(defn user-get-one
  [database username]
  (let [db-user (db/user-get! database username)
        user (db-user->user db-user)]
    user))


;;================================================
;;             User Get List Flow
;;================================================
(defn users-get-list
  [database filter]
  (let [db-users (db/users-get! database filter)]
    (->> db-users
         (map first)
         (map db-user->user))))