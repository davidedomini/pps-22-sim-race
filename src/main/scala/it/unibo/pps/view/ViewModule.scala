package it.unibo.pps.view

import it.unibo.pps.controller.ControllerModule
import it.unibo.pps.model.{Car, Snapshot, Standings, Track, Tyre}
import it.unibo.pps.view.ViewConstants.*

object ViewModule:
  trait View:

    /**  Method that updates the car displayed */
    def updateDisplayedCar(): Unit

    /**  Method that updates the displayed standings */
    def updateDisplayedStandings(): Unit

    /** Method that displays the starting positions panel */
    def displayStartingPositionsPanel(): Unit

    /** Method that updates the displayed parameters when the car displayed is changed */
    def updateParametersPanel(): Unit

    /** Method that displays the simulation panel
     *   @param track
     *   The track to be rendered before the display of the simulation panel
     */
    def displaySimulationPanel(track: Track): Unit

    /** Method that displays the end race panel */
    def displayEndRacePanel(): Unit

    def updateCars(cars: List[Car], actualLap: Int, totalLaps: Int): Unit
    def updateCharts(l: List[Snapshot]): Unit

    /** Method that sets enabled the final report button that if pressed, display the end race panel */
    def setFinalReportEnabled(): Unit

    /**  Method that updates the fastest lap icon
     * @param carName
     *   The name of the car that has made the fastest lap
     */
    def updateFastestLapIcon(carName: String): Unit

  trait Provider:
    val view: View

  type Requirements = ControllerModule.Provider

  trait Component:
    context: Requirements =>
    class ViewImpl extends View:
      val gui = new Gui(context.controller)

      override def updateDisplayedCar(): Unit =
        gui.updateDisplayedCar()

      override def updateDisplayedStandings(): Unit = gui.updateDisplayedStandings()

      override def displaySimulationPanel(track: Track): Unit =
        gui.displaySimulationPanel(track)

      override def updateCars(cars: List[Car], actualLap: Int, totalLaps: Int): Unit =
        gui.simulationPanel.render(cars, actualLap, totalLaps)

      override def displayStartingPositionsPanel(): Unit =
        gui.displayStartingPositionsPanel()

      override def displayEndRacePanel(): Unit =
        gui.displayEndRacePanel()

      override def updateParametersPanel(): Unit =
        gui.updateParametersPanel()

      override def updateCharts(l: List[Snapshot]): Unit =
        gui.simulationPanel.updateCharts(l.last)

      override def setFinalReportEnabled(): Unit =
        gui.setFinalReportEnabled()

      override def updateFastestLapIcon(carName: String): Unit =
        gui.updateFastestLapIcon(carName)

  trait Interface extends Provider with Component:
    self: Requirements =>
