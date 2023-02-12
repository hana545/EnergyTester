package com.battery.energytester.Database

data class Iteration(val number : Int,
                     val duration : Float,
                     val current : Float,
                     val voltage : Float,
                     val power : Float,
                     val energy : Float)
