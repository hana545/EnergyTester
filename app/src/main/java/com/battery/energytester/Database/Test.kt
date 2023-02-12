package com.battery.energytester.Database

data class Test(var id : Long,
                var name : String,
                val iterations : ArrayList<Iteration>,
                var iteration_size : Int,
                var duration : Float,
                var avg_current : Float,
                var avg_voltage : Float,
                var avg_power : Float,
                var energy : Float,
                var startBatLevel : Int,
                var endBatLevel : Int)

