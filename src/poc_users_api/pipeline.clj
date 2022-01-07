(ns poc-users-api.pipeline
  (:require [poc-users-api.database :as db]
            [datomic.api :as d]
            [schema.core :as s]))



;; ================================================
;;                      Schemas
;; ================================================
(def UserSchema
  "Validation for POST|PUT /users request"
  {:name                  s/Str
   :username              s/Str
   :active                s/Bool
   (s/optional-key :tags) [s/Str]
   s/Any                  s/Any})


;; ================================================
;;                 Conversions
;; ================================================
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


;; ================================================
;;              Database Interceptor
;; ================================================
(def database-interceptor
  {:name :database-interceptor
   :enter (fn
            [context]
            (let [conn     (d/connect db/db-uri)
                  database (d/db conn)]
              (assoc-in context [:request :database] {:db   database
                                        :conn conn}))
            )})


;; ================================================
;;             Users Validation Format
;; ================================================
(defn- user-format-validation [user]
  (nil? (s/check UserSchema user)))


;; ================================================
;;              Users Creation Flow
;; ================================================
(defn users-create
  [conn database user]
  ;; format validation - no IO
  (if (user-format-validation user)

    ;; ok branch
    (let [username             (:username user)

          ;; uniqueness validation - yes IO!
          user-already-exists? (db/user-get! database username)]
      (if user-already-exists?

         ;; nok branch
        {:ok        false
         :exception false
         :message   "This username has been taken already."
         :details   "You cannot create an user with a duplicate username."}

        (try
          (let [db-user   (user->db-user user)
                tx-result (db/user-transact! conn db-user)]
            ;; final OK
            {:ok        true
             :exception false
             :message   "User created."
             :details   @tx-result})

          ;; shit happens...
          (catch Exception e
            {:ok        false
             :exception true
             :message   "Error creating user."
             :details   (ex-message e)}))))

    ;; nok branch
    {:ok        false
     :exception false
     :message   "Invalid format."
     :details   (or (s/check UserSchema user) "Please, check types.")})
  )



;; ================================================
;;              Users Update Flow
;; ================================================
(defn users-update
  [conn database username informed-user]
  ;; format validation - no IO
  (if (user-format-validation informed-user)

    ;; ok branch
    (let [user         (assoc informed-user :username username)
          ;; existence validation - yes IO!
          user-exists? (db/user-get! database username)]
            
      (if-not user-exists?
         ;; nok branch
        {:ok          false
         :user-exists false
         :exception   false
         :message     "This user does not exist."
         :details     "Please, choose an existing user to update."}

        (try
          (let [db-user   (user->db-user user)
                tx-result (db/user-transact! conn db-user)]
            ;; final OK
            {:ok        true
             :user-exists true
             :exception false
             :message   "User updated."
             :details   @tx-result})

          ;; shit happens...
          (catch Exception e
            {:ok        false
             :user-exists nil
             :exception true
             :message   "Error updating user."
             :details   (ex-message e)}))))

    ;; nok branch
    {:ok        false
     :user-exists nil
     :exception false
     :message   "Invalid format."
     :details   (or (s/check UserSchema informed-user) "Please, check types.")}))


;; ================================================
;;              Users Delete Flow
;; ================================================
(defn users-delete
  [conn database username]
  (if-not (db/user-get! database username)
    ;; nok branch
    {:ok          false
     :user-exists false
     :exception   false
     :message     "This user does not exist."
     :details     "Please, choose an existing user to delete."}

    (try
      (let [tx-result (db/user-set-deleted->true! conn username)]
        ;; OK
        {:ok          true
         :user-exists true
         :exception   false
         :message     "User deleted."
         :details     @tx-result})

      ;; shit happens...
      (catch Exception e
        {:ok          false
         :user-exists nil
         :exception   true
         :message     "Error updating user."
         :details     (ex-message e)}))))


;; ================================================
;;              User Get One Flow
;; ================================================
(defn users-get-one
  [database username]
  (let [db-user (db/user-get! database username)
        user (db-user->user db-user)]
    user)
  )



;; ================================================
;;              User Get List Flow
;; ================================================
(defn users-get-list
  [database filter]
  (let [db-users (db/users-get! database filter)]
    (->> db-users
        (map first)
        (map db-user->user))))