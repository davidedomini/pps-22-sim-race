package it.unibo.pps.controller

import it.unibo.pps.engine.SimulationEngineModule
import it.unibo.pps.model.{Car, Driver, ModelModule, Snapshot, Tyre}
import it.unibo.pps.view.ViewModule
import it.unibo.pps.view.main_panel.ImageLoader
import monix.execution.Scheduler.Implicits.global
import monix.execution.Cancelable
import it.unibo.pps.utility.PimpScala.RichOption.*
import it.unibo.pps.view.simulation_panel.DrawingCarParams

import java.awt.Color

object ControllerModule:
  trait Controller:
    def notifyStart(): Unit
    //def createCars(): Unit
    def getStartingPositions(): scala.collection.mutable.Map[Int, String]
    def getCurrentCar(): Car
    def getCars(): List[Car]
    def updateParametersPanel(): Unit
    def updateDisplayedCar(): Unit
    def getCurrentCarIndex(): Int
    def notifyStop(): Unit
    def setCurrentCarIndex(index: Int): Unit
    def setPath(path: String): Unit
    def setTyre(tyre: Tyre): Unit
    def setMaxSpeed(speed: Int): Unit
    def setAttack(attack: Int): Unit
    def setDefense(defense: Int): Unit
    def displaySimulationPanel(): Unit
    def displayStartingPositionsPanel(): Unit
    def invertPosition(prevIndex: Int, nextIndex: Int): Unit
    def notifyDecreseSpeed(): Unit
    def notifyIncreaseSpeed(): Unit

  trait Provider:
    val controller: Controller

  type Requirements = ModelModule.Provider with SimulationEngineModule.Provider with ViewModule.Provider

  trait Component:
    context: Requirements =>
    class ControllerImpl extends Controller:

      private val imageLoader = ImageLoader()
      private val numCars = 4
      private val carNames = List("Ferrari", "Mercedes", "Red Bull", "McLaren")
      //private var currentCarIndex = 0
      //private var cars: List[Car] = List.empty
      //private val startingPositions: scala.collection.mutable.Map[Int, String] = scala.collection.mutable.Map(0 -> "Ferrari", 1 -> "Mercedes", 2 -> "Red Bull", 3 -> "McLaren")

      override def notifyStart(): Unit = stopFuture = Some(
        context.simulationEngine
          .simulationStep()
          .loopForever
          .runAsync {
            case Left(exp) => global.reportFailure(exp)
            case _ =>
          }
      )

      override def invertPosition(prevIndex: Int, nextIndex: Int): Unit =
        val support = context.model.startingPositions(prevIndex)
        context.model.startingPositions(prevIndex) = context.model.startingPositions(nextIndex)
        context.model.startingPositions(nextIndex) = support

      override def getStartingPositions(): scala.collection.mutable.Map[Int, String] = context.model.startingPositions

      override def getCurrentCarIndex(): Int = context.model.currentCarIndex

      override def getCars(): List[Car] = context.model.cars

      override def setCurrentCarIndex(index: Int): Unit = context.model.currentCarIndex = index

      override def setPath(path: String): Unit = context.model.cars(context.model.currentCarIndex).path = path

      private var stopFuture: Option[Cancelable] = None

      override def notifyStop(): Unit =
        stopFuture --> (_.cancel())
        stopFuture = None

      override def notifyDecreseSpeed(): Unit =
        context.simulationEngine.decreaseSpeed()

      override def notifyIncreaseSpeed(): Unit =
        context.simulationEngine.increaseSpeed()

      override def setTyre(tyre: Tyre): Unit = context.model.cars(context.model.currentCarIndex).tyre = tyre

      override def setMaxSpeed(speed: Int): Unit = context.model.cars(context.model.currentCarIndex).maxSpeed = speed

      override def setAttack(attack: Int): Unit = context.model.cars(context.model.currentCarIndex).driver.attack = attack

      override def setDefense(defense: Int): Unit = context.model.cars(context.model.currentCarIndex).driver.defense = defense

      override def getCurrentCar(): Car = context.model.cars(context.model.currentCarIndex)

      override def updateParametersPanel(): Unit =
        context.view.updateParametersPanel()

      /*override def createCars(): Unit =
        val l = for
          index <- 0 until numCars
          car = Car(s"/cars/$index-hard.png", carNames(index), Tyre.HARD, Driver(1,1), 200, 0, DrawingCarParams((453, 115), Color.CYAN))
        yield car
        cars = l.toList*/

      override def updateDisplayedCar(): Unit =
        context.view.updateDisplayedCar()

      override def displaySimulationPanel(): Unit =
        context.view.displaySimulationPanel(context.model.track, context.model.standing)
        context.view.updateCars(context.model.cars)

      override def displayStartingPositionsPanel(): Unit =
        context.view.displayStartingPositionsPanel()

  trait Interface extends Provider with Component:
    self: Requirements =>
