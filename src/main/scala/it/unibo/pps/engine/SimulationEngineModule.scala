package it.unibo.pps.engine

import alice.tuprolog.{Term, Theory}
import it.unibo.pps.controller.ControllerModule
import it.unibo.pps.model.{Car, ModelModule, Snapshot}
import it.unibo.pps.view.ViewModule
import monix.eval.Task
import monix.execution.Scheduler

import concurrent.duration.{Duration, DurationDouble, DurationInt, FiniteDuration}
import scala.language.postfixOps
import it.unibo.pps.engine.SimulationConstants.*
import it.unibo.pps.prolog.Scala2P
import it.unibo.pps.utility.monadic.*
import it.unibo.pps.utility.GivenConversion.ModelConversion
import it.unibo.pps.view.simulation_panel.DrawingCarParams
import it.unibo.pps.utility.GivenConversion.GuiConversion.given_Conversion_Unit_Task

given Conversion[String, Term] = Term.createTerm(_)
given Conversion[Seq[_], Term] = _.mkString("[", ",", "]")
given Conversion[String, Theory] = Theory.parseLazilyWithStandardOperators(_)
given Itearable2List[E]: Conversion[Iterable[E], List[E]] = _.toList

object SimulationEngineModule:
  trait SimulationEngine:
    def simulationStep(): Task[Unit]
    def decreaseSpeed(): Unit
    def increaseSpeed(): Unit

  trait Provider:
    val simulationEngine: SimulationEngine

  type Requirements = ViewModule.Provider with ModelModule.Provider with ControllerModule.Provider

  trait Component:
    context: Requirements =>
    class SimulationEngineImpl extends SimulationEngine:

      private val speedManager = SpeedManager()
      private val engine = Scala2P.createEngine("/prolog/movements.pl")

      override def decreaseSpeed(): Unit =
        speedManager.decreaseSpeed()

      override def increaseSpeed(): Unit =
        speedManager.increaseSpeed()

      override def simulationStep(): Task[Unit] =
        for
          _ <- moveCars()
          //_ <- updateStanding()
          //_ <- updateCharts()
          _ <- updateView()
          _ <- waitFor(speedManager._simulationSpeed)
        yield ()

      private def waitFor(simulationSpeed: Double): Task[Unit] =
        val time = BASE_TIME * simulationSpeed
        Task.sleep(time millis)

      private def moveCars(): Task[Unit] =
        for
          _ <- io(println("Updating cars.... " + speedManager._simulationSpeed))
          lastSnap <- getLastSnapshot()
          newSnap <- updatePositions(lastSnap)
          _ <- io(context.model.addSnapshot(newSnap))
        yield ()

      private def updatePositions(snapshot: Snapshot): Task[Snapshot] =

        for
          time <- io(snapshot.time)
          cars <- io(snapshot.cars)
          newCars = for
            car <- cars
            position = car.drawingCarParams.position
            //velocity = car.velocity
            time = snapshot.time
            newX = calcWithProlog(car,position._1, time + 1, car.actualSpeed)
            newPosition = (newX, position._2)
            d = DrawingCarParams(newPosition, car.drawingCarParams.color)
          yield car.copy(drawingCarParams = d)
          newSnap <- io(Snapshot(newCars, time + 1))
        yield newSnap

      private def calcWithProlog(car:Car, x: Int, time: Int, velocity: Double): Int =

        val inizio = 453
        var start = car.drawingCarParams.position._1

        if time == 1 then
           start = 454

        //val acceleration = (2 * (start - inizio)) / (time * time) + 10
           //TODO - ora è in pixel/s^2 --> bisogna mettere a posto le unità di misura (la vel è in km/h)

        /*
        println("X iniziale: "+x)
        println("Tempo: "+time)
        println("Velocità: " + velocity)
        println("MaxVel: "+car.maxSpeed)
        */

        // Accelerazione macchine di formula 1 --> 11 m/s^2

        if time == 1 then
          car.maxSpeed = (car.maxSpeed / 3.6).toInt //pixel/sec, assumendo che 1km = 1000pixel e 1h = 3600sec

        if car.name.equals("Ferrari") then
          println("car.velocity: "+car.actualSpeed + " -- " + car.name)
          println("acceleration: "+car.acceleration+ "pixel/s^2")
          println("time: "+time)

        val newVelocity = car.actualSpeed + (car.acceleration * time)



        if newVelocity < car.maxSpeed then
          car.actualSpeed = newVelocity

        //println("DOPO - car.Velocity: "+car.velocity)


        val newP = engine(s"computeNewPosition($x, $velocity, $time, ${car.acceleration}, Np)")
          .map(Scala2P.extractTermToString(_, "Np"))
          .toSeq
          .head
          .toDouble
          .toInt

        if car.name.equals("Ferrari") then println("nuova posizione -> " + newP)

        // Tempo = 1s
        // Accelerazione = car.acceleration
        // Velocità = car.actualSpeed
        // Spostamento = ???

        //car.drawingCarParams.position._1 + 5
        newP

      private def getLastSnapshot(): Task[Snapshot] =
        io(context.model.getLastSnapshot())

      private def updateCharts(): Task[Unit] =
        for _ <- io(println("Updating charts...."))
        yield ()

      private def updateStanding(): Task[Unit] =
        for _ <- io(println("Updating standing...."))
        yield ()

      private def updateView(): Task[Unit] =
        for
          //_ <- io(println("Updating view...."))
          cars <- io(context.model.getLastSnapshot().cars)
          _ <- io(context.view.updateCars(cars))
        yield ()

  trait Interface extends Provider with Component:
    self: Requirements =>
