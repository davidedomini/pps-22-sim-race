package it.unibo.pps.engine

import monix.execution.Scheduler.Implicits.global
import alice.tuprolog.{Term, Theory}
import it.unibo.pps.controller.ControllerModule
import it.unibo.pps.model.{Car, ModelModule, Phase, Sector, Snapshot, Standing, Straight, Turn, Tyre}
import it.unibo.pps.view.ViewModule
import monix.eval.Task
import monix.execution.Scheduler
import scala.io.StdIn.readLine
import concurrent.duration.{Duration, DurationDouble, DurationInt, FiniteDuration}
import scala.language.postfixOps
import it.unibo.pps.engine.SimulationConstants.*
import it.unibo.pps.prolog.Scala2P
import it.unibo.pps.utility.monadic.*
import it.unibo.pps.utility.GivenConversion.ModelConversion
import it.unibo.pps.view.simulation_panel.{DrawingCarParams, DrawingParams, DrawingStraightParams, DrawingTurnParams}
import it.unibo.pps.utility.GivenConversion.GuiConversion.given_Conversion_Unit_Task
import it.unibo.pps.utility.PimpScala.RichInt.*
import it.unibo.pps.utility.PimpScala.RichTuple2.*
import it.unibo.pps.view.ViewConstants.*

import scala.collection.mutable
import scala.collection.mutable.{HashMap, Map}

given Itearable2List[E]: Conversion[Iterable[E], List[E]] = _.toList
given Conversion[Task[(Int, Int)], (Int, Int)] = _.runSyncUnsafe()
given Conversion[Task[Car], Car] = _.runSyncUnsafe()

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
      private val movementsManager = Movements()
      private val sectorTimes: HashMap[String, Int] =
        HashMap("Ferrari" -> 0, "McLaren" -> 0, "Red Bull" -> 0, "Mercedes" -> 0)
      private val angles = TurnAngles()
      private val finalPositions = List((633, 272), (533, 272), (433, 272), (333, 272))
      private var carsArrived = 0

      override def decreaseSpeed(): Unit =
        speedManager.decreaseSpeed()

      override def increaseSpeed(): Unit =
        speedManager.increaseSpeed()

      override def simulationStep(): Task[Unit] =
        for
          _ <- moveCars()
          _ <- updateStanding()
          _ <- updateView()
          _ <- waitFor(speedManager._simulationSpeed)
          _ <- if carsArrived == NUM_CARS then
            controller.notifyStop()
            context.view.setFinalReportEnabled()
        yield ()

      private def waitFor(simulationSpeed: Double): Task[Unit] =
        val time = BASE_TIME * simulationSpeed
        Task.sleep(time millis)

      private def moveCars(): Task[Unit] =
        for
          lastSnap <- io(context.model.getLastSnapshot())
          newSnap <- computeNewSnapshot(lastSnap)
          _ <- io(context.model.addSnapshot(newSnap))
        yield ()

      private def computeNewSnapshot(snapshot: Snapshot): Task[Snapshot] =
        for
          time <- io(snapshot.time + 1)
          cars <- io(snapshot.cars)
          newCars <- io(cars.map(updateCar(_, time)))
          newSnap <- io(Snapshot(newCars, time))
        yield newSnap

      private def updateCar(car: Car, time: Int): Car =
        for
          newVelocity <- io(updateVelocity(car, time))
          newPosition <- io(
            if car.actualLap > context.model.totalLaps then getFinalPositions(car) else updatePosition(car)
          )
          newFuel <- io(updateFuel(car, newPosition))
          newDegradation <- io(updateDegradation(car, newPosition, newVelocity))
          newDrawingParams <- io(car.drawingCarParams.copy(position = newPosition))
        yield car.copy(
          actualSpeed = newVelocity,
          fuel = newFuel,
          degradation = newDegradation,
          drawingCarParams = newDrawingParams
        )

      private val computeRadius = (d: DrawingParams, position: Tuple2[Int, Int]) =>
        d match {
          case DrawingTurnParams(center, _, _, _, _, _, _) =>
            center euclideanDistance position
          case _ => 0
        }

      private def updateFuel(car: Car, newPosition: Tuple2[Int, Int]): Double = car.actualSector match {
        case Straight(_, _) =>
          val oldPosition = car.drawingCarParams.position
          car.fuel - Math.abs(oldPosition._1 - newPosition._1) * 0.0015
        case Turn(_, _) =>
          val r = computeRadius(car.actualSector.drawingParams, car.drawingCarParams.position)
          val teta = angles.difference(car.name)
          val l = (teta / 360) * 2 * r * Math.PI
          car.fuel - l * 0.0015
      }

      private def updateDegradation(car: Car, newPosition: Tuple2[Int, Int], v: Double): Double =
        val f = (d: Double, s: Double, v: Double, l: Int) => //TODO - refactor
          if d >= 50 then d
          else
            var m = 0
            car.tyre match {
              case Tyre.HARD => m = 10
              case Tyre.MEDIUM => m = 5
              case Tyre.SOFT => m = 1
            }
            d + (s + v + l) / (3000 + 50 * m)
        car.actualSector match {
          case Straight(_, _) =>
            val oldPosition = car.drawingCarParams.position
            f(car.degradation, Math.abs(oldPosition._1 - newPosition._1) * 2, v, car.actualLap)
          case Turn(_, _) =>
            val r = computeRadius(car.actualSector.drawingParams, car.drawingCarParams.position)
            val teta2 = angles.difference(car.name)
            println(s"Car: ${car.name}")
            println(s"Teta2: $teta2")
            //Old pos --> A
            //new pos --> B
            val oldPos = car.drawingCarParams.position
            val AB = oldPos euclideanDistance newPosition
            val teta = Math.acos(((2 * r * r) - AB) / (2 * r * r))
            println(s"Teta: $teta")
            val l = (teta / 360) * 2 * r * Math.PI
            f(car.degradation, l, v, car.actualLap)
        }

      private def updateVelocity(car: Car, time: Int): Double = car.actualSector match {
        case Straight(_, _) =>
          car.actualSector.phase(car.drawingCarParams.position) match {
            case Phase.Acceleration =>
              val v = movementsManager.newVelocityStraightAcc(car, sectorTimes(car.name))
              if v > car.maxSpeed then car.maxSpeed else v
            case Phase.Deceleration => movementsManager.newVelocityStraightDec(car, sectorTimes(car.name))
            case _ => car.actualSpeed // TODO ending
          }
        case Turn(_, _) =>
          car.actualSector.phase(car.drawingCarParams.position) match {
            case Phase.Acceleration => car.actualSpeed
            case _ => 6 //TODO - magic number
          }
      }

      private def updatePosition(car: Car): Tuple2[Int, Int] = car.actualSector match {
        case Straight(_, _) => straightMovement(car)
        case Turn(_, _) => turnMovement(car)
      }

      private def straightMovement(car: Car): Tuple2[Int, Int] =
        car.actualSector.phase(car.drawingCarParams.position) match {
          case Phase.Acceleration =>
            val p = movementsManager.acceleration(car, sectorTimes(car.name))
            sectorTimes(car.name) = sectorTimes(car.name) + 1
            p
          case Phase.Deceleration =>
            val p = movementsManager.deceleration(car, sectorTimes(car.name))
            sectorTimes(car.name) = sectorTimes(car.name) + 1
            val i = if car.actualSector.id == 1 then 1 else -1
            car.actualSector.drawingParams match {
              case DrawingStraightParams(_, _, _, _, endX) => //TODO - fare un metodo di check
                val d = (p._1 - endX) * i
                if d >= 0 then
                  sectorTimes(car.name) = 3
                  (endX, p._2)
                else p
            }
          case Phase.Ended =>
            sectorTimes(car.name) = 3
            car.actualSector = context.model.track.nextSector(car.actualSector)
            turnMovement(car)
        }

      private def checkLap(car: Car): Unit =
        if car.actualSector.id == 1 then car.actualLap = car.actualLap + 1
        if car.actualLap > context.model.actualLap then context.model.actualLap = car.actualLap
        if car.actualLap > context.model.totalLaps then carsArrived = carsArrived + 1

      private def turnMovement(car: Car): Tuple2[Int, Int] =
        car.actualSector.phase(car.drawingCarParams.position) match {
          case Phase.Acceleration =>
            val p = movementsManager.turn(car, sectorTimes(car.name), car.actualSpeed, car.actualSector.drawingParams)
            sectorTimes(car.name) = sectorTimes(car.name) + 1
            p
          case Phase.Ended =>
            car.actualSector = context.model.track.nextSector(car.actualSector)
            sectorTimes(car.name) = 0
            angles.reset(car.name)
            sectorTimes(car.name) = 25 //TODO
            //car.actualSpeed = 45 //TODO
            //if car.actualLap > context.model.totalLaps then carsArrived = carsArrived + 1 //TODO
            checkLap(car)
            straightMovement(car)
          case Phase.Deceleration => (0, 0)
        }

      private def updateStanding(): Task[Unit] =
        for
          lastSnap <- io(context.model.getLastSnapshot())
          newStanding = calcNewStanding(lastSnap)
          _ <- io(context.model.setS(newStanding))
          _ <- io(context.view.updateDisplayedStanding())
        yield ()

      private def calcNewStanding(snap: Snapshot): Standing =

        val carsByLap = snap.cars.groupBy(_.actualLap).sortWith(_._1 >= _._1)
        var l1: List[Car] = List.empty

        carsByLap.foreach(carsBySector => {
          carsBySector._2
            .groupBy(_.actualSector)
            .sortWith(_._1.id >= _._1.id)
            .foreach(e => {
              e._1 match
                case Straight(id, _) =>
                  if id == 1 then l1 = l1.concat(sortCars(e._2, _ > _, true))
                  else l1 = l1.concat(sortCars(e._2, _ < _, true))
                case Turn(id, _) =>
                  if id == 2 then l1 = l1.concat(sortCars(e._2, _ > _, false))
                  else l1 = l1.concat(sortCars(e._2, _ < _, false))
            })
        })
        Standing(Map.from(l1.zipWithIndex.map { case (k, v) => (v, k) }))

      private def sortCars(cars: List[Car], f: (Int, Int) => Boolean, isHorizontal: Boolean): List[Car] =
        var l: List[Car] = List.empty
        if isHorizontal then
          cars
            .sortWith((c1, c2) => f(c1.drawingCarParams.position._1, c2.drawingCarParams.position._1))
            .foreach(e => l = l.concat(List(e)))
        else
          cars
            .sortWith((c1, c2) => f(c1.drawingCarParams.position._2, c2.drawingCarParams.position._2))
            .foreach(e => l = l.concat(List(e)))
        l

      private def updateView(): Task[Unit] =
        for
          cars <- io(context.model.getLastSnapshot().cars)
          _ <- io(context.view.updateCars(cars, context.model.actualLap, context.model.totalLaps))
        yield ()

      private def getFinalPositions(car: Car): (Int, Int) =
        finalPositions(context.model.standing._standing.find(_._2.equals(car)).get._1)

  trait Interface extends Provider with Component:
    self: Requirements =>
