package it.unibo.pps.view.main_panel

import it.unibo.pps.controller.ControllerModule
import it.unibo.pps.utility.GivenConversion.GuiConversion.given
import it.unibo.pps.view.main_panel.StartingPositionsPanel
import it.unibo.pps.view.Constants.StartingPositionsPanelConstants.*
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import it.unibo.pps.utility.PimpScala.RichJPanel.*

import java.awt.{BorderLayout, Color, Dimension, FlowLayout, GridBagConstraints, GridBagLayout}
import javax.swing.*

trait StartingPositionsPanel extends JPanel

object StartingPositionsPanel:
  def apply(controller: ControllerModule.Controller): StartingPositionsPanel =
    StartingPositionsPanelImpl(controller)

  private class StartingPositionsPanelImpl(controller: ControllerModule.Controller) extends StartingPositionsPanel:
    self =>

    private lazy val topLabel = createLabel(
      Dimension(STARTING_POS_PANEL_WIDTH, TOP_LABEL_HEIGHT),
      SwingConstants.CENTER,
      () => Left("Sets the order of the starting grid: ")
    )
    private lazy val positionPanel = createPanel
    private lazy val startingPositionsComponents = createStartingPositionsComponents
    private lazy val startingPositionsPanel = createPanelAndAddAllComponents

    startingPositionsPanel foreach (e => self.add(e))

    private def createStartingPositionsComponents: List[StartingPositionsComponents] =
      for car <- controller.cars
      yield StartingPositionsComponents(
        createLabel(
          Dimension(CAR_POS_WIDTH, CAR_POS_HEIGHT),
          SwingConstants.LEFT,
          () => Left(s"${controller.cars.indexOf(car) + 1}. ")
        ),
        createLabel(Dimension(CAR_NAME_WIDTH, CAR_POS_HEIGHT), SwingConstants.LEFT, () => Left(s"${car.name}")),
        createLabel(
          Dimension(CAR_MINIATURE_WIDTH, CAR_MINIATURE_HEIGHT),
          SwingConstants.CENTER,
          () => Right(ImageLoader.load(s"/cars/miniatures/${controller.cars.indexOf(car)}.png"))
        ),
        if car.equals(controller.cars.head) then
          createButton(controller.cars.indexOf(car), "/arrows/blank_background.png", e => if e == 0 then e else e - 1)
        else createButton(controller.cars.indexOf(car), "/arrows/arrow-up.png", e => if e == 0 then e else e - 1),
        if car.equals(controller.cars.last) then
          createButton(controller.cars.indexOf(car), "/arrows/blank_background.png", e => if e == 0 then e else e - 1)
        else
          createButton(
            controller.cars.indexOf(car),
            "/arrows/arrow-bottom.png",
            e => if e == (NUM_CARS - 1) then e else e + 1
          )
      )

    private def createLabel(dim: Dimension, horizontal: Int, f: () => Either[String, ImageIcon]): Task[JLabel] =
      for
        label <- f() match
          case Left(s: String) => JLabel(s)
          case Right(i: ImageIcon) => JLabel(i)
        _ <- label.setPreferredSize(dim)
        _ <- label.setHorizontalAlignment(horizontal)
      yield label

    private def createButton(index: Int, path: String, calcIndex: Int => Int): Task[JButton] =
      for
        button <- JButton(ImageLoader.load(path))
        _ <- button.setBorder(BorderFactory.createEmptyBorder())
        _ <- button.setBackground(BUTTON_NOT_SELECTED_COLOR)
        _ <- button.setHorizontalAlignment(SwingConstants.RIGHT)
        _ <- button.addActionListener { e =>
          val nextIndex = calcIndex(index)
          controller.invertPosition(index, nextIndex)
          invertParams(index, nextIndex)
        }
      yield button

    private def invertParams(prevIndex: Int, nextIndex: Int): Unit =
      var nextLabelSupport = ""
      var prevLabelSupport = ""

      val p = for
        nextLabel <- startingPositionsComponents(nextIndex).name
        nextLabelSupport = nextLabel.getText
        prevLabel <- startingPositionsComponents(prevIndex).name
        prevLabelSupport = prevLabel.getText
        nextImage <- startingPositionsComponents(nextIndex).miniature
        prevImage <- startingPositionsComponents(prevIndex).miniature
        _ <- nextLabel.setText(prevLabelSupport)
        _ <- prevLabel.setText(nextLabelSupport)
        _ <- updateImage(nextImage, prevLabelSupport)
        _ <- updateImage(prevImage, nextLabelSupport)
      yield ()
      p.runSyncUnsafe()

    private def updateImage(label: JLabel, img: String): Task[Unit] =
      label.setIcon(ImageLoader.load(s"/cars/miniatures/${CAR_NAMES.find(_._2.equals(img)).get._1}.png"))

    private def createPanel: Task[JPanel] =
      for
        panel <- JPanel()
        _ <- panel.setPreferredSize(Dimension(STARTING_POS_PANEL_WIDTH, STARTING_POS_SUBPANEL_HEIGHT))
      yield panel

    private def createPanelAndAddAllComponents: Task[JPanel] =
      for
        panel <- JPanel()
        _ <- panel.setPreferredSize(Dimension(STARTING_POS_PANEL_WIDTH, STARTING_POS_PANEL_HEIGHT))
        topLabel <- topLabel
        positionPanel <- positionPanel
        _ <- startingPositionsComponents.foreach(e => addToPanel(e, positionPanel))
        _ <- panel.addAll(List(topLabel, positionPanel))
        _ <- panel.setVisible(true)
      yield panel

    private def addToPanel(
        elem: StartingPositionsComponents,
        posPanel: JPanel
    ): Task[Unit] =
      val p = for
        panel <- JPanel()
        _ <- panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.BLACK))
        pos <- elem.position
        name <- elem.name
        img <- elem.miniature
        upButton <- elem.upButton
        downButton <- elem.downButton
        blank <- JLabel(ImageLoader.load("/arrows/blank_background.png"))
        _ <- panel.addAll(List(pos, name, img))
        _ <- if pos.getText.equals(s"$NUM_CARS. ") then panel.add(blank)
        _ <- panel.addAll(List(upButton, downButton))
        _ <- if pos.getText.equals("1. ") then panel.add(blank)
        _ <- posPanel.add(panel)
      yield ()
      p.runSyncUnsafe()
