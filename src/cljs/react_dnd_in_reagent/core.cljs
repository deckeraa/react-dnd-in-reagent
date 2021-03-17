(ns react-dnd-in-reagent.core
  (:require
   [reagent.core :as r :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   ["react-dnd" :as react-dnd :refer [DndProvider useDrag useDrop useDragLayer]]
   ["react-dnd-html5-backend" :as react-html5-backend]))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Page components

(defn draggable-wrapper [item reagent-child-fn]
  [:>
   (fn []
     (let [[dnd-props ref preview-ref] (useDrag (clj->js {:type :block
                                                          :item item
                                                          :collect (fn [monitor]
                                                                     (let [is-dragging? (.isDragging ^js monitor)]
                                                                       {:is-dragging? is-dragging?}))}))]
       (r/as-element
        (reagent-child-fn ref preview-ref dnd-props))))])

(defn droppable-wrapper [can-drop-fn drop-fn reagent-child-fn]
  [:> 
   (fn []
     (let [[dnd-props ref] (useDrop (clj->js {:accept :block
                                              :drop drop-fn
                                              :canDrop can-drop-fn
                                              :collect (fn [monitor]
                                                         (let [is-over? (.isOver ^js monitor)
                                                               can-drop? (.canDrop ^js monitor)]
                                                           {:is-over? is-over?
                                                            :can-drop? can-drop?}))}))]
       (r/as-element (reagent-child-fn ref dnd-props))))])

(defn block [id]
  [:div {:style {:border "1px solid" :height "50px" :margin "5px" :position :relative}}
   [droppable-wrapper (fn [] true) (fn [] (println "drop onto: " id))
    (fn [ref dnd-props]
      [:div {:ref ref :style {:width "100%" :height "100%" :position :absolute :background-color "yellow"}}])]
   [draggable-wrapper {}
    (fn [ref preview-ref dnd-props]
      [:div {:ref ref :style {:z-index 100 :padding "4px" :background-color "grey" :position :relative}} "id: " id])]])

(defn page []
  (r/with-let []
    [:div
     [:div "Welcome to react-dnd-in-reagent"]
     [:> DndProvider {:backend react-html5-backend/HTML5Backend}
      [block 14]
      [droppable-wrapper (fn [] true) (fn [] (println "drop"))
       (fn [ref dnd-props]
         [:div {:ref ref :style {:border "1px solid"}} "foo"])]
      [draggable-wrapper {}
       (fn [ref preview-ref dnd-props]
         [:div {:ref ref :style {:padding "4px"}} "draggable"])]]]))

(defn home-page []
  (fn []
    [page]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [page])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (r/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

