(ns slipway.auth.constraints
  (:import (org.eclipse.jetty.util.security Constraint)
           (org.eclipse.jetty.security ConstraintMapping)))

(defn ^Constraint basic-auth-any-constraint []
  (doto (Constraint. Constraint/__BASIC_AUTH Constraint/ANY_AUTH) ;; == allow any authenticated user
    (.setName "auth")
    (.setAuthenticate true)))

(defn ^Constraint form-auth-any-constraint []
  (doto (Constraint. Constraint/__FORM_AUTH Constraint/ANY_AUTH) ;; == allow any authenticated user
    (.setName "auth")
    (.setAuthenticate true)))

(defn ^Constraint no-auth []
  (doto (Constraint.)
    (.setName "no-auth")))

(defn ^ConstraintMapping constraint-mapping
  [^String path ^Constraint constraint]
  (doto (ConstraintMapping.)
    (.setConstraint constraint)
    (.setPathSpec path)))

(defn constraint-mappings
  [& xs]
  (into [] (map (fn [[path constraint]]
                  (constraint-mapping path constraint)))
        xs))