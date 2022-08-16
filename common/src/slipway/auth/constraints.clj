(ns slipway.auth.constraints
  (:import (org.eclipse.jetty.security ConstraintMapping)
           (org.eclipse.jetty.util.security Constraint)))

(defn basic-auth-any-constraint ^Constraint []
  (doto (Constraint. Constraint/__BASIC_AUTH Constraint/ANY_AUTH) ;; == allow any authenticated user
    (.setName "auth")
    (.setAuthenticate true)))

(defn form-auth-any-constraint ^Constraint []
  (doto (Constraint. Constraint/__FORM_AUTH Constraint/ANY_AUTH) ;; == allow any authenticated user
    (.setName "auth")
    (.setAuthenticate true)))

(defn no-auth ^Constraint []
  (doto (Constraint.)
    (.setName "no-auth")))

(defn constraint-mapping ^ConstraintMapping
  [^String path ^Constraint constraint]
  (doto (ConstraintMapping.)
    (.setConstraint constraint)
    (.setPathSpec path)))

(defn constraint-mappings
  [& xs]
  (into [] (map (fn [[path constraint]]
                  (constraint-mapping path constraint)))
        xs))