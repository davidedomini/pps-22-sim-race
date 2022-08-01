package it.unibo.pps.view

import it.unibo.pps.controller.ControllerModule

import java.awt.{BorderLayout, Color, Component, Dimension, Graphics}
import javax.swing.{JButton, JComponent, JPanel}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import java.awt.event.{ActionEvent, ActionListener}

trait SimulationPanel extends JPanel:

  /** Method for rendering the new snapshot of the simulation
   *
   */
  def render(): Unit

object SimulationPanel:

  given Conversion[Enviroment, Task[Enviroment]] = Task(_)
  given Conversion[Int, Task[Int]] = Task(_)
  given Conversion[Unit, Task[Unit]] = Task(_)
  given Conversion[JButton, Task[JButton]] = Task(_)
  given Conversion[Component, Task[Component]] = Task(_)
  given Conversion[JPanel, Task[JPanel]] = Task(_)

  def apply(width: Int, height: Int, controller: ControllerModule.Controller): SimulationPanel = new SimulationPanelImpl(width, height, controller)

  private class SimulationPanelImpl(width: Int, height: Int, controller: ControllerModule.Controller) extends SimulationPanel:
    self =>
    self.setLayout(new BorderLayout())
    private val canvas = createCanvas()
    canvas.foreach(c => self.add(c, BorderLayout.NORTH))
    private val btnPanel = new JPanel()
    Task
      .sequence(
        createButton("Start", e => println("button start pressed")) ::
        createButton("Stop", e => println("button stop pressed")) ::
        createButton("+ Vel", e => println("button incVel pressed")) ::
        createButton("- Vel", e => println("button decVel pressed")) ::
        Nil)
      .foreach(btns => btns.foreach(b => btnPanel.add(b)))
    self.add(btnPanel, BorderLayout.SOUTH)
    btnPanel.setBackground(Color.CYAN)
    override def render(): Unit = ???

    private def createCanvas(): Task[Enviroment] =
      val w = (width*0.6).toInt
      val h = (height*0.7).toInt
      for
        cnv <- new Enviroment(w, h)
        _ <- cnv.setSize(w, h)
        _ <- cnv.setVisible(true)
      yield cnv

    private def createButton(title: String, listener: ActionListener): Task[JButton] =
      for
        jb <- new JButton()
        _ <- jb.setText(title)
        _ <- jb.addActionListener(listener)
      yield jb

class Enviroment(val w: Int, val h: Int) extends JPanel:
      override def getPreferredSize: Dimension = new Dimension(w, h)
      override def paintComponent(g: Graphics): Unit =
        g.drawRect(20, 20, 100, 100)



