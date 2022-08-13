package it.unibo.pps.view.simulation_panel

import it.unibo.pps.model.{Sector, Track}
import it.unibo.pps.utility.PimpScala.RichTuple2.*

import java.awt.{Color, Dimension, Graphics}
import javax.swing.JPanel

class Enviroment(val w: Int, val h: Int) extends JPanel:

  var track: Track = Track()

  override def getPreferredSize: Dimension = new Dimension(w, h)
  override def paintComponent(g: Graphics): Unit =
    g.setColor(Color.BLACK)

    // ---------------- Metodo 1 per disegnare le macchine --------------------------------------------
    //Variabili relative alle macchine
    var intW = (w * 0.5).toInt
    var intH = (h * 0.2).toInt + 2
    var carDiameter = 12
    var distanceBeetweenCars = 20
    g.setColor(Color.BLUE)
    g.fillOval(intW, intH, carDiameter, carDiameter)
    g.setColor(Color.GREEN)
    g.fillOval(intW + distanceBeetweenCars, intH + distanceBeetweenCars, carDiameter, carDiameter)
    g.setColor(Color.RED)
    g.fillOval(intW + (distanceBeetweenCars * 2), intH + (distanceBeetweenCars * 2), carDiameter, carDiameter)
    g.setColor(Color.BLACK)

    //------------------------------------------------------------------------------------------------

    def sketcher(e: Sector) = e match {
      case s: Sector.Straight => drawStraigth(s, g)
      case t: Sector.Turn => drawTurn(t, g)
    }

    track.getSectors().foreach(sketcher(_))
    g.drawRect(0, 0, w, h)

  private def drawStraigth(s: Sector.Straight, g: Graphics): Unit =
    val p0 = s.drawingParams.p0External
    val p1 = s.drawingParams.p1External
    val p2 = s.drawingParams.p0Internal
    val p3 = s.drawingParams.p1Internal
    g.drawLine(p0._1, p0._2, p1._1, p1._2)
    g.drawLine(p2._1, p2._2, p3._1, p3._2)

  private def drawTurn(t: Sector.Turn, g: Graphics): Unit =
    val externalRadius = t.drawingParams.center euclideanDistance t.drawingParams.startPointE
    val interalRadius = t.drawingParams.center euclideanDistance t.drawingParams.startPointI
    drawSingleTurn(
      externalRadius,
      t.drawingParams.center._1,
      t.drawingParams.center._2,
      2 * externalRadius,
      t.drawingParams.direction,
      g
    )
    drawSingleTurn(
      interalRadius,
      t.drawingParams.center._1,
      t.drawingParams.center._2,
      2 * interalRadius,
      t.drawingParams.direction,
      g
    )

  private def drawSingleTurn(radius: Int, x: Int, y: Int, diameter: Int, direction: Int, g: Graphics): Unit =
    val x0 = x - radius
    val y0 = y - radius
    val startAngle = 270
    val endAngle = 180 * direction
    g.drawArc(x0, y0, diameter, diameter, startAngle, endAngle)