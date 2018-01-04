(ns dynadoc.examples
  (:require [dynadoc.state :refer [*state]])
  (:require-macros [dynadoc.example :refer [defexamples]]))

(swap! *state assoc :dev? true)

(defexamples dynadoc.core/form->serializable
  [{:doc "This is a test example"
    :with-focus [focus (+ a b)]}
   (let [a 1
         b 2]
     focus)]
  ["Serialize an error"
   (form->serializable (js/Error. "This is an error!"))])

(defprotocol Screen
  "A screen object provides the basic lifecycle for a game.
Simple games may only need to have one screen. They are a useful way to
isolate different aspects of your game. For example, you could make one
screen display the title and menu, and another screen contain the game
itself. 

You can create a screen by using `reify` like this:

```
(def main-screen
  (reify p/Screen
    (on-show [this])
    (on-hide [this])
    (on-render [this])))
```"
  (on-show [screen]
    "Runs once, when the screen first appears.")
  (on-hide [screen]
    "Runs once, when the screen is no longer displayed.")
  (on-render [screen]
    "Runs each time the game is ready to render another frame."))


