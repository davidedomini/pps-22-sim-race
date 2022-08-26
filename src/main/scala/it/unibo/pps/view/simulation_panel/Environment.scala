package it.unibo.pps.view.simulation_panel

import it.unibo.pps.model.{Sector, Track}
import java.awt.{Color, Dimension, Graphics}
import javax.swing.JPanel
import it.unibo.pps.utility.PimpScala.RichTuple2._
import scala.{Tuple2 => Point2d}
import it.unibo.pps.view.ViewConstants.*
import it.unibo.pps.model.{Car, Straight, Turn}

class Environment(val w: Int, val h: Int) extends JPanel:

  private var _track: Track = Track()
  private var _cars: List[Car] = List.empty

  def track: Track = _track
  def track_=(t: Track): Unit = _track = t
  def cars: List[Car] = _cars
  def cars_=(c: List[Car]): Unit = _cars = c

  override def getPreferredSize: Dimension = new Dimension(w, h)
  override def paintComponent(g: Graphics): Unit =
    g.setColor(Color.MAGENTA)
    g.drawString("LAP: 1", 449, 60)

    g.setColor(Color.BLACK)

    _cars.foreach(c => drawCar(c.drawingCarParams.position, c.drawingCarParams.color, g))
    g.setColor(Color.BLACK)

    def sketcher(e: Sector) = e match {
      case s: Straight => drawStraigth(s, g)
      case t: Turn => drawTurn(t, g)
    }

    _track.sectors.foreach(sketcher(_))
    g.drawRect(0, 0, w, h)

  private def drawStraigth(s: Straight, g: Graphics): Unit = s.drawingParams match {
    case DrawingStraightParams(p0External, p1External, p0Internal, p1Internal, _) =>
      g.drawLine(p0External._1, p0External._2, p1External._1, p1External._2)
      g.drawLine(p0Internal._1, p0Internal._2, p1Internal._1, p1Internal._2)
  }

  private def drawTurn(t: Turn, g: Graphics): Unit = t.drawingParams match {
    case DrawingTurnParams(center, startPointE, startPointI, endPointE, endPointI, direction, _) =>
      val externalRadius = center euclideanDistance startPointE
      val internalRadius = center euclideanDistance startPointI
      drawSingleTurn(externalRadius, center, 2 * externalRadius, direction, g)
      drawSingleTurn(internalRadius, center, 2 * internalRadius, direction, g)
  }

  private def drawSingleTurn(radius: Int, center: Point2d[Int, Int], diameter: Int, direction: Int, g: Graphics): Unit =
    center match {
      case Point2d(x, y) =>
        g.drawArc(x - radius, y - radius, diameter, diameter, TURN_START_ANGLE, TURN_END_ANGLE * direction)
    }

  private def drawCar(position: Point2d[Int, Int], color: Color, g: Graphics): Unit =
    g.setColor(color)
    g.fillOval(position._1, position._2, CAR_DIAMETER, CAR_DIAMETER)
